package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CoverShapeStyle
import com.example.model.RepeatMode
import com.example.model.Track
import com.example.player.MusicUiState
import com.example.ui.components.ExpressiveHeroCoverPager
import com.example.ui.components.QueueBottomSheet
import com.example.ui.components.TrackArtworkImage
import com.example.ui.components.WavySeekBar
import com.example.ui.components.formatDuration
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun NowPlayingScreen(
    uiState: MusicUiState,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onSeek: (Float) -> Unit,
    onScrubbing: (Float?) -> Unit = {},
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCycleCoverShape: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onSetShowFavoritesOnly: (Boolean) -> Unit,
    onAddMusicClick: () -> Unit,
    onRemoveImported: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrack = uiState.currentTrack
    val targetAccent = currentTrack?.accentColor ?: MaterialTheme.colorScheme.primary
    val targetSecondary = currentTrack?.secondaryColor ?: MaterialTheme.colorScheme.surfaceContainer

    val animatedAccent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 550),
        label = "accent_color"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = targetSecondary,
        animationSpec = tween(durationMillis = 650),
        label = "secondary_color"
    )

    val onAccentColor = if (animatedAccent.luminance() > 0.52f) {
        Color(0xFF141118)
    } else {
        Color(0xFFFFFBFF)
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.currentIndex.coerceIn(0, (uiState.tracks.size - 1).coerceAtLeast(0)),
        pageCount = { uiState.tracks.size }
    )

    val latestCurrentIndex by rememberUpdatedState(uiState.currentIndex)

    LaunchedEffect(uiState.currentIndex, uiState.tracks.size) {
        if (uiState.tracks.isNotEmpty() && uiState.currentIndex in uiState.tracks.indices) {
            if (pagerState.currentPage != uiState.currentIndex) {
                pagerState.animateScrollToPage(uiState.currentIndex)
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                if (!pagerState.isScrollInProgress &&
                    settled != latestCurrentIndex &&
                    settled in uiState.tracks.indices
                ) {
                    onSelectTrack(settled)
                }
            }
    }

    val progressFraction = if (uiState.durationMs > 0L) {
        (uiState.positionMs.toFloat() / uiState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bufferedFraction = if (uiState.durationMs > 0L) {
        (uiState.bufferedPositionMs.toFloat() / uiState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val bgBase = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBase)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedAccent.copy(alpha = if (uiState.isPlaying) 0.28f else 0.18f),
                            animatedSecondary.copy(alpha = 0.58f),
                            bgBase
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.34f),
                        radius = size.maxDimension * 0.78f
                    )
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (currentTrack != null) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideLayout = maxWidth >= 680.dp

                if (isWideLayout) {
                    ExpandedNowPlayingContent(
                        uiState = uiState,
                        currentTrack = currentTrack,
                        pagerState = pagerState,
                        animatedAccent = animatedAccent,
                        onAccentColor = onAccentColor,
                        progressFraction = progressFraction,
                        bufferedFraction = bufferedFraction,
                        onTogglePlayPause = onTogglePlayPause,
                        onNextTrack = onNextTrack,
                        onPreviousTrack = onPreviousTrack,
                        onSeek = onSeek,
                        onScrubbing = onScrubbing,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeatMode = onCycleRepeatMode,
                        onToggleFavorite = onToggleFavorite,
                        onCycleCoverShape = onCycleCoverShape,
                        onOpenQueue = onOpenQueue,
                        onAddMusicClick = onAddMusicClick
                    )
                } else {
                    CompactNowPlayingContent(
                        uiState = uiState,
                        currentTrack = currentTrack,
                        pagerState = pagerState,
                        animatedAccent = animatedAccent,
                        onAccentColor = onAccentColor,
                        progressFraction = progressFraction,
                        bufferedFraction = bufferedFraction,
                        onTogglePlayPause = onTogglePlayPause,
                        onNextTrack = onNextTrack,
                        onPreviousTrack = onPreviousTrack,
                        onSeek = onSeek,
                        onScrubbing = onScrubbing,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeatMode = onCycleRepeatMode,
                        onToggleFavorite = onToggleFavorite,
                        onCycleCoverShape = onCycleCoverShape,
                        onOpenQueue = onOpenQueue,
                        onAddMusicClick = onAddMusicClick
                    )
                }
            }
        }
    }

    if (uiState.isQueueSheetOpen) {
        QueueBottomSheet(
            tracks = uiState.tracks,
            currentTrackId = currentTrack?.id,
            isPlaying = uiState.isPlaying,
            favoriteIds = uiState.favoriteIds,
            showFavoritesOnly = uiState.showFavoritesOnly,
            accentColor = animatedAccent,
            onDismiss = onDismissQueue,
            onSelectTrack = { index -> onSelectTrack(index) },
            onToggleFavorite = onToggleFavorite,
            onSetShowFavoritesOnly = onSetShowFavoritesOnly,
            onAddMusicClick = onAddMusicClick,
            onRemoveImported = onRemoveImported
        )
    }
}

@Composable
private fun CompactNowPlayingContent(
    uiState: MusicUiState,
    currentTrack: Track,
    pagerState: PagerState,
    animatedAccent: Color,
    onAccentColor: Color,
    progressFraction: Float,
    bufferedFraction: Float,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubbing: (Float?) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCycleCoverShape: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddMusicClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 560.dp)
            .padding(top = 8.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopPlayerHeader(
            currentIndex = uiState.currentIndex,
            totalTracks = uiState.tracks.size,
            isPlaying = uiState.isPlaying,
            coverShapeStyle = uiState.coverShapeStyle,
            accentColor = animatedAccent,
            onCycleCoverShape = onCycleCoverShape,
            onOpenQueue = onOpenQueue
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Hero Album Cover dominates the center of Now Playing
        ExpressiveHeroCoverPager(
            tracks = uiState.tracks,
            pagerState = pagerState,
            isPlaying = uiState.isPlaying,
            shapeStyle = uiState.coverShapeStyle,
            onCoverClick = onTogglePlayPause,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TrackHeaderAndFavorite(
                track = currentTrack,
                isFavorite = currentTrack.id in uiState.favoriteIds,
                accentColor = animatedAccent,
                onToggleFavorite = { onToggleFavorite(currentTrack.id) }
            )

            PlaybackProgressSection(
                positionMs = uiState.displayedPositionMs,
                durationMs = uiState.durationMs,
                progressFraction = progressFraction,
                bufferedFraction = bufferedFraction,
                isScrubbing = uiState.scrubPositionMs != null,
                isPlaying = uiState.isPlaying,
                accentColor = animatedAccent,
                onSeek = onSeek,
                onScrubbing = onScrubbing
            )

            ExpressiveTransportControls(
                isPlaying = uiState.isPlaying,
                shuffleEnabled = uiState.shuffleEnabled,
                repeatMode = uiState.repeatMode,
                accentColor = animatedAccent,
                onAccentColor = onAccentColor,
                onTogglePlayPause = onTogglePlayPause,
                onNextTrack = onNextTrack,
                onPreviousTrack = onPreviousTrack,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeatMode = onCycleRepeatMode
            )

            BottomLibraryBar(
                tracks = uiState.tracks,
                currentIndex = uiState.currentIndex,
                accentColor = animatedAccent,
                onOpenQueue = onOpenQueue,
                onAddMusicClick = onAddMusicClick
            )
        }
    }
}

@Composable
private fun ExpandedNowPlayingContent(
    uiState: MusicUiState,
    currentTrack: Track,
    pagerState: PagerState,
    animatedAccent: Color,
    onAccentColor: Color,
    progressFraction: Float,
    bufferedFraction: Float,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubbing: (Float?) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCycleCoverShape: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddMusicClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        TopPlayerHeader(
            currentIndex = uiState.currentIndex,
            totalTracks = uiState.tracks.size,
            isPlaying = uiState.isPlaying,
            coverShapeStyle = uiState.coverShapeStyle,
            accentColor = animatedAccent,
            onCycleCoverShape = onCycleCoverShape,
            onOpenQueue = onOpenQueue
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            ExpressiveHeroCoverPager(
                tracks = uiState.tracks,
                pagerState = pagerState,
                isPlaying = uiState.isPlaying,
                shapeStyle = uiState.coverShapeStyle,
                onCoverClick = onTogglePlayPause,
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .widthIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TrackHeaderAndFavorite(
                    track = currentTrack,
                    isFavorite = currentTrack.id in uiState.favoriteIds,
                    accentColor = animatedAccent,
                    onToggleFavorite = { onToggleFavorite(currentTrack.id) }
                )

                PlaybackProgressSection(
                    positionMs = uiState.displayedPositionMs,
                    durationMs = uiState.durationMs,
                    progressFraction = progressFraction,
                    bufferedFraction = bufferedFraction,
                    isScrubbing = uiState.scrubPositionMs != null,
                    isPlaying = uiState.isPlaying,
                    accentColor = animatedAccent,
                    onSeek = onSeek,
                    onScrubbing = onScrubbing
                )

                ExpressiveTransportControls(
                    isPlaying = uiState.isPlaying,
                    shuffleEnabled = uiState.shuffleEnabled,
                    repeatMode = uiState.repeatMode,
                    accentColor = animatedAccent,
                    onAccentColor = onAccentColor,
                    onTogglePlayPause = onTogglePlayPause,
                    onNextTrack = onNextTrack,
                    onPreviousTrack = onPreviousTrack,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeatMode = onCycleRepeatMode
                )

                BottomLibraryBar(
                    tracks = uiState.tracks,
                    currentIndex = uiState.currentIndex,
                    accentColor = animatedAccent,
                    onOpenQueue = onOpenQueue,
                    onAddMusicClick = onAddMusicClick
                )
            }
        }
    }
}

@Composable
private fun TopPlayerHeader(
    currentIndex: Int,
    totalTracks: Int,
    isPlaying: Boolean,
    coverShapeStyle: CoverShapeStyle,
    accentColor: Color,
    onCycleCoverShape: () -> Unit,
    onOpenQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
            shape = CircleShape,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Text(
                    text = stringResource(R.string.now_playing_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (totalTracks > 0) {
                    Text(
                        text = "${currentIndex + 1}/$totalTracks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onCycleCoverShape,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                modifier = Modifier.testTag("shape_toggle_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = stringResource(R.string.shape_toggle_action),
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = coverShapeStyle.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                onClick = onOpenQueue,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("top_queue_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = stringResource(R.string.open_queue_action),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackHeaderAndFavorite(
    track: Track,
    isFavorite: Boolean,
    accentColor: Color,
    onToggleFavorite: () -> Unit
) {
    val heartScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "favorite_scale"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("track_title")
            )
            Text(
                text = "${track.artist} · ${track.album}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            onClick = onToggleFavorite,
            shape = CircleShape,
            color = if (isFavorite) {
                accentColor.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
            },
            modifier = Modifier
                .size(48.dp)
                .testTag("favorite_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(R.string.favorite_action),
                    tint = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = heartScale
                            scaleY = heartScale
                        }
                )
            }
        }
    }
}

@Composable
private fun PlaybackProgressSection(
    positionMs: Long,
    durationMs: Long,
    progressFraction: Float,
    bufferedFraction: Float,
    isScrubbing: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    onSeek: (Float) -> Unit,
    onScrubbing: (Float?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        WavySeekBar(
            progress = progressFraction,
            bufferedProgress = bufferedFraction,
            durationMs = durationMs,
            isPlaying = isPlaying,
            enabled = durationMs > 0L,
            accentColor = accentColor,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
            onScrubbing = onScrubbing,
            onSeek = onSeek
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = if (isScrubbing) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("elapsed_time_label")
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("total_duration_label")
            )
        }
    }
}

@Composable
private fun ExpressiveTransportControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    accentColor: Color,
    onAccentColor: Color,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit
) {
    // Expressive Material 3 morph: circle pill when paused -> soft squircle pill when playing
    val playCornerRadius by animateDpAsState(
        targetValue = if (isPlaying) 22.dp else 34.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "play_morph_radius"
    )

    val playButtonWidth by animateDpAsState(
        targetValue = if (isPlaying) 94.dp else 82.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "play_morph_width"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggleShuffle,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (shuffleEnabled) accentColor.copy(alpha = 0.16f) else Color.Transparent
                )
                .testTag("shuffle_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.shuffle_action),
                tint = if (shuffleEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        Surface(
            onClick = onPreviousTrack,
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            modifier = Modifier
                .size(width = 58.dp, height = 54.dp)
                .testTag("previous_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.previous_action),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Surface(
            onClick = onTogglePlayPause,
            shape = RoundedCornerShape(playCornerRadius),
            color = accentColor,
            shadowElevation = 10.dp,
            modifier = Modifier
                .size(width = playButtonWidth, height = 68.dp)
                .testTag("play_pause_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.pause_action else R.string.play_action
                    ),
                    tint = onAccentColor,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Surface(
            onClick = onNextTrack,
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            modifier = Modifier
                .size(width = 58.dp, height = 54.dp)
                .testTag("next_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.next_action),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        IconButton(
            onClick = onCycleRepeatMode,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (repeatMode != RepeatMode.OFF) accentColor.copy(alpha = 0.16f) else Color.Transparent
                )
                .testTag("repeat_button")
        ) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                },
                contentDescription = stringResource(R.string.repeat_action),
                tint = if (repeatMode != RepeatMode.OFF) {
                    accentColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun BottomLibraryBar(
    tracks: List<Track>,
    currentIndex: Int,
    accentColor: Color,
    onOpenQueue: () -> Unit,
    onAddMusicClick: () -> Unit
) {
    val nextTrack = if (tracks.size > 1) {
        tracks[(currentIndex + 1) % tracks.size]
    } else {
        null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onOpenQueue,
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .testTag("queue_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (nextTrack != null) {
                    TrackArtworkImage(
                        track = nextTrack,
                        contentDescription = null,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.queue_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor
                    )
                    Text(
                        text = nextTrack?.title ?: stringResource(R.string.library_title),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Surface(
            onClick = onAddMusicClick,
            shape = RoundedCornerShape(24.dp),
            color = accentColor.copy(alpha = 0.16f),
            modifier = Modifier
                .height(52.dp)
                .testTag("quick_add_music_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.add_music),
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.add_music),
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor
                )
            }
        }
    }
}
