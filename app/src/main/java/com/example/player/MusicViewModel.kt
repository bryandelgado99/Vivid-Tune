package com.example.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.MusicRepository
import com.example.model.ArtworkSource
import com.example.model.CoverShapeStyle
import com.example.model.RepeatMode
import com.example.model.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MusicUiState(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val scrubPositionMs: Long? = null,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val favoriteIds: Set<String> = emptySet(),
    val showFavoritesOnly: Boolean = false,
    val coverShapeStyle: CoverShapeStyle = CoverShapeStyle.SQUIRCLE,
    val isQueueSheetOpen: Boolean = false,
    val isLoading: Boolean = true
) {
    val currentTrack: Track?
        get() = tracks.getOrNull(currentIndex)

    val displayedPositionMs: Long
        get() = scrubPositionMs ?: positionMs
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressTickerJob: Job? = null
    private var isControllerSynced = false
    private var lastSeekTimestampMs = 0L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startProgressTicker() else stopProgressTicker()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = controller ?: return
            val index = ctrl.currentMediaItemIndex
            val tracks = _uiState.value.tracks
            if (index in tracks.indices) {
                val track = tracks[index]
                val reportedDuration = ctrl.duration.takeIf { it > 0L } ?: track.durationMs
                _uiState.update {
                    it.copy(
                        currentIndex = index,
                        positionMs = ctrl.currentPosition.coerceAtLeast(0L),
                        bufferedPositionMs = ctrl.bufferedPosition.coerceAtLeast(0L),
                        scrubPositionMs = null,
                        durationMs = reportedDuration
                    )
                }
                viewModelScope.launch { repository.setLastTrackId(track.id) }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val ctrl = controller ?: return
            val currentTrack = _uiState.value.currentTrack
            val duration = ctrl.duration.takeIf { it > 0L } ?: currentTrack?.durationMs ?: _uiState.value.durationMs
            _uiState.update {
                it.copy(
                    positionMs = newPosition.positionMs.coerceAtLeast(0L),
                    bufferedPositionMs = ctrl.bufferedPosition.coerceAtLeast(0L),
                    durationMs = duration
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val ctrl = controller ?: return
            val currentTrack = _uiState.value.currentTrack
            val duration = ctrl.duration.takeIf { it > 0L } ?: currentTrack?.durationMs ?: 0L
            _uiState.update {
                it.copy(
                    durationMs = duration,
                    positionMs = ctrl.currentPosition.coerceAtLeast(0L),
                    bufferedPositionMs = ctrl.bufferedPosition.coerceAtLeast(0L)
                )
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mapped = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }
            _uiState.update { it.copy(repeatMode = mapped) }
        }
    }

    init {
        initializeLibrary()
        observePreferences()
        connectController()
    }

    private fun initializeLibrary() {
        viewModelScope.launch {
            val prefs = repository.preferencesFlow.first()
            val bundled = repository.loadBundledTracks()
            val imported = repository.loadImportedTracks(prefs.importedUris)
            val allTracks = bundled + imported
            val startIdx = prefs.lastTrackId
                ?.let { savedId -> allTracks.indexOfFirst { it.id == savedId } }
                ?.takeIf { it >= 0 }
                ?: 0

            _uiState.update {
                it.copy(
                    tracks = allTracks,
                    currentIndex = startIdx,
                    durationMs = allTracks.getOrNull(startIdx)?.durationMs ?: 0L,
                    shuffleEnabled = prefs.shuffleEnabled,
                    repeatMode = prefs.repeatMode,
                    favoriteIds = prefs.favoriteIds,
                    coverShapeStyle = prefs.coverShapeStyle,
                    isLoading = false
                )
            }
            syncControllerQueueIfReady(allTracks, startIdx, playWhenReady = false)
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            repository.preferencesFlow.collect { prefs ->
                _uiState.update {
                    it.copy(
                        favoriteIds = prefs.favoriteIds,
                        coverShapeStyle = prefs.coverShapeStyle
                    )
                }
            }
        }
    }

    private fun connectController() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val mediaController = future.get()
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    val state = _uiState.value
                    if (state.tracks.isNotEmpty()) {
                        if (mediaController.mediaItemCount > 0) {
                            // Reattach cleanly if service was already playing
                            val idx = mediaController.currentMediaItemIndex.coerceIn(0, state.tracks.lastIndex)
                            _uiState.update {
                                it.copy(
                                    currentIndex = idx,
                                    isPlaying = mediaController.isPlaying,
                                    positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                                    bufferedPositionMs = mediaController.bufferedPosition.coerceAtLeast(0L),
                                    durationMs = mediaController.duration.takeIf { d -> d > 0L }
                                        ?: state.tracks[idx].durationMs
                                )
                            }
                            isControllerSynced = true
                            if (mediaController.isPlaying) startProgressTicker()
                        } else {
                            syncControllerQueueIfReady(state.tracks, state.currentIndex, playWhenReady = false)
                        }
                    }
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun syncControllerQueueIfReady(
        tracks: List<Track>,
        startIndex: Int,
        playWhenReady: Boolean
    ) {
        val ctrl = controller ?: return
        if (tracks.isEmpty() || isControllerSynced) return
        val mediaItems = tracks.map { it.toMediaItem() }
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        ctrl.setMediaItems(mediaItems, safeIndex, 0L)
        ctrl.shuffleModeEnabled = _uiState.value.shuffleEnabled
        ctrl.repeatMode = _uiState.value.repeatMode.toPlayerRepeatMode()
        ctrl.prepare()
        ctrl.playWhenReady = playWhenReady
        isControllerSynced = true
    }

    fun selectTrack(index: Int, autoPlay: Boolean = true) {
        val state = _uiState.value
        if (index !in state.tracks.indices) return
        val track = state.tracks[index]
        _uiState.update {
            it.copy(
                currentIndex = index,
                positionMs = 0L,
                bufferedPositionMs = 0L,
                scrubPositionMs = null,
                durationMs = track.durationMs
            )
        }
        viewModelScope.launch { repository.setLastTrackId(track.id) }

        val ctrl = controller
        if (ctrl != null) {
            if (!isControllerSynced) {
                syncControllerQueueIfReady(state.tracks, index, playWhenReady = autoPlay)
            } else {
                ctrl.seekTo(index, 0L)
                if (autoPlay) ctrl.play()
            }
        }
    }

    fun togglePlayPause() {
        val ctrl = controller
        val state = _uiState.value
        if (ctrl != null) {
            if (!isControllerSynced && state.tracks.isNotEmpty()) {
                syncControllerQueueIfReady(state.tracks, state.currentIndex, playWhenReady = true)
                return
            }
            if (ctrl.isPlaying) {
                ctrl.pause()
            } else {
                if (ctrl.playbackState == Player.STATE_ENDED) {
                    ctrl.seekTo(0L)
                }
                ctrl.play()
            }
        }
    }

    fun nextTrack() {
        val ctrl = controller
        val state = _uiState.value
        if (state.tracks.isEmpty()) return
        if (ctrl != null && isControllerSynced) {
            if (ctrl.hasNextMediaItem()) {
                ctrl.seekToNextMediaItem()
                ctrl.play()
            } else {
                ctrl.seekTo(0, 0L)
                ctrl.play()
            }
        } else {
            val nextIdx = (state.currentIndex + 1) % state.tracks.size
            selectTrack(nextIdx, autoPlay = true)
        }
    }

    fun previousTrack() {
        val ctrl = controller
        val state = _uiState.value
        if (state.tracks.isEmpty()) return
        if (ctrl != null && isControllerSynced) {
            if (ctrl.currentPosition > 3_000L) {
                ctrl.seekTo(0L)
            } else if (ctrl.hasPreviousMediaItem()) {
                ctrl.seekToPreviousMediaItem()
                ctrl.play()
            } else {
                ctrl.seekTo(state.tracks.lastIndex, 0L)
                ctrl.play()
            }
        } else {
            val prevIdx = if (state.currentIndex > 0) state.currentIndex - 1 else state.tracks.lastIndex
            selectTrack(prevIdx, autoPlay = true)
        }
    }

    fun onScrubbing(fraction: Float?) {
        val duration = _uiState.value.durationMs
        if (fraction == null || duration <= 0L) {
            _uiState.update { it.copy(scrubPositionMs = null) }
            return
        }
        val previewMs = (fraction.coerceIn(0f, 1f) * duration).toLong()
        _uiState.update { it.copy(scrubPositionMs = previewMs) }
    }

    fun seekToFraction(fraction: Float) {
        val state = _uiState.value
        val duration = state.durationMs
        if (duration <= 0L) {
            _uiState.update { it.copy(scrubPositionMs = null) }
            return
        }
        val targetMs = (fraction.coerceIn(0f, 1f) * duration).toLong()
        lastSeekTimestampMs = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                positionMs = targetMs,
                scrubPositionMs = null
            )
        }
        val ctrl = controller
        if (ctrl != null) {
            if (!isControllerSynced && state.tracks.isNotEmpty()) {
                syncControllerQueueIfReady(state.tracks, state.currentIndex, playWhenReady = false)
            }
            ctrl.seekTo(targetMs)
        }
    }

    fun toggleShuffle() {
        val newValue = !_uiState.value.shuffleEnabled
        _uiState.update { it.copy(shuffleEnabled = newValue) }
        controller?.shuffleModeEnabled = newValue
        viewModelScope.launch { repository.setShuffleEnabled(newValue) }
    }

    fun cycleRepeatMode() {
        val nextMode = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _uiState.update { it.copy(repeatMode = nextMode) }
        controller?.repeatMode = nextMode.toPlayerRepeatMode()
        viewModelScope.launch { repository.setRepeatMode(nextMode) }
    }

    fun toggleFavorite(trackId: String) {
        viewModelScope.launch { repository.toggleFavorite(trackId) }
    }

    fun setShowFavoritesOnly(favoritesOnly: Boolean) {
        _uiState.update { it.copy(showFavoritesOnly = favoritesOnly) }
    }

    fun cycleCoverShapeStyle() {
        val entries = CoverShapeStyle.entries
        val nextIndex = (entries.indexOf(_uiState.value.coverShapeStyle) + 1) % entries.size
        val nextStyle = entries[nextIndex]
        _uiState.update { it.copy(coverShapeStyle = nextStyle) }
        viewModelScope.launch { repository.setCoverShapeStyle(nextStyle) }
    }

    fun setQueueSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isQueueSheetOpen = open) }
    }

    fun importAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newTracks = repository.importAudioUris(uris)
            if (newTracks.isNotEmpty()) {
                val existing = _uiState.value.tracks
                val existingUris = existing.map { it.audioUri.toString() }.toSet()
                val uniqueNew = newTracks.filterNot { it.audioUri.toString() in existingUris }
                if (uniqueNew.isNotEmpty()) {
                    val updated = existing + uniqueNew
                    val firstNewIndex = existing.size
                    _uiState.update {
                        it.copy(
                            tracks = updated,
                            currentIndex = firstNewIndex,
                            positionMs = 0L,
                            durationMs = uniqueNew.first().durationMs
                        )
                    }
                    controller?.let { ctrl ->
                        ctrl.addMediaItems(uniqueNew.map { it.toMediaItem() })
                        ctrl.seekTo(firstNewIndex, 0L)
                        ctrl.play()
                    }
                }
            }
        }
    }

    fun removeImportedTrack(track: Track) {
        if (!track.isImported) return
        viewModelScope.launch {
            repository.removeImportedTrack(track.audioUri.toString())
            val state = _uiState.value
            val removeIdx = state.tracks.indexOfFirst { it.id == track.id }
            if (removeIdx == -1) return@launch
            val updated = state.tracks.filterNot { it.id == track.id }
            val newIndex = when {
                updated.isEmpty() -> 0
                removeIdx < state.currentIndex -> state.currentIndex - 1
                removeIdx == state.currentIndex -> state.currentIndex.coerceAtMost(updated.lastIndex)
                else -> state.currentIndex
            }
            _uiState.update {
                it.copy(
                    tracks = updated,
                    currentIndex = newIndex,
                    durationMs = updated.getOrNull(newIndex)?.durationMs ?: 0L
                )
            }
            controller?.removeMediaItem(removeIdx)
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = viewModelScope.launch {
            while (true) {
                val ctrl = controller
                if (ctrl != null && ctrl.isPlaying) {
                    val elapsedSinceSeek = System.currentTimeMillis() - lastSeekTimestampMs
                    val dur = ctrl.duration.takeIf { it > 0L } ?: _uiState.value.durationMs
                    val buffered = ctrl.bufferedPosition.coerceAtLeast(0L)
                    if (elapsedSinceSeek > 150L && _uiState.value.scrubPositionMs == null) {
                        val pos = ctrl.currentPosition.coerceAtLeast(0L)
                        _uiState.update {
                            it.copy(
                                positionMs = pos,
                                bufferedPositionMs = buffered,
                                durationMs = dur
                            )
                        }
                    } else {
                        _uiState.update { it.copy(bufferedPositionMs = buffered, durationMs = dur) }
                    }
                }
                delay(50L)
            }
        }
    }

    private fun stopProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = null
    }

    override fun onCleared() {
        stopProgressTicker()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }

    private fun Track.toMediaItem(): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)

        if (artworkSource is ArtworkSource.EmbeddedBytes) {
            metadataBuilder.setArtworkData(artworkSource.bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(audioUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }
}
