package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import com.example.model.ArtworkSource
import com.example.model.CoverShapeStyle
import com.example.model.RepeatMode
import com.example.model.Track
import com.example.ui.theme.CoverColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val Context.ondaDataStore: DataStore<Preferences> by preferencesDataStore(name = "onda_prefs")

data class PlayerPreferences(
    val favoriteIds: Set<String> = emptySet(),
    val importedUris: List<String> = emptyList(),
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val coverShapeStyle: CoverShapeStyle = CoverShapeStyle.SQUIRCLE,
    val lastTrackId: String? = null
)

class MusicRepository(private val context: Context) {

    private object Keys {
        val FAVORITE_IDS = stringSetPreferencesKey("favorite_ids")
        val IMPORTED_URIS = stringPreferencesKey("imported_uris_ordered")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val COVER_SHAPE = stringPreferencesKey("cover_shape")
        val LAST_TRACK_ID = stringPreferencesKey("last_track_id")
    }

    val preferencesFlow: Flow<PlayerPreferences> = context.ondaDataStore.data.map { prefs ->
        val uriList = prefs[Keys.IMPORTED_URIS]
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val repeat = prefs[Keys.REPEAT_MODE]
            ?.let { name -> RepeatMode.entries.firstOrNull { it.name == name } }
            ?: RepeatMode.ALL
        val shape = prefs[Keys.COVER_SHAPE]
            ?.let { name -> CoverShapeStyle.entries.firstOrNull { it.name == name } }
            ?: CoverShapeStyle.SQUIRCLE
        PlayerPreferences(
            favoriteIds = prefs[Keys.FAVORITE_IDS] ?: emptySet(),
            importedUris = uriList,
            shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
            repeatMode = repeat,
            coverShapeStyle = shape,
            lastTrackId = prefs[Keys.LAST_TRACK_ID]
        )
    }

    suspend fun loadBundledTracks(): List<Track> = withContext(Dispatchers.IO) {
        listOf(
            createBundledTrack(
                id = "bundled_solsticio",
                title = "Solsticio de Terracota",
                artist = "Alba & Meridiano",
                album = "Arcos del Sur",
                durationMs = 24_000L,
                rawResId = R.raw.track_solsticio,
                drawableResId = R.drawable.img_cover_solstice,
                fallbackAccent = Color(0xFFFF7A45),
                fallbackSecondary = Color(0xFF421C14)
            ),
            createBundledTrack(
                id = "bundled_nocturno",
                title = "Nocturno en Cobalto",
                artist = "Prisma Sónico",
                album = "Cristal Líquido",
                durationMs = 22_000L,
                rawResId = R.raw.track_nocturno,
                drawableResId = R.drawable.img_cover_nocturne,
                fallbackAccent = Color(0xFF46C2FF),
                fallbackSecondary = Color(0xFF0E1D3B)
            ),
            createBundledTrack(
                id = "bundled_verbena",
                title = "Verbena de Musgo",
                artist = "Bosque Analógico",
                album = "Esferas de Rocío",
                durationMs = 20_000L,
                rawResId = R.raw.track_verbena,
                drawableResId = R.drawable.img_cover_verbena,
                fallbackAccent = Color(0xFF7BE077),
                fallbackSecondary = Color(0xFF172D18)
            ),
            createBundledTrack(
                id = "bundled_espejismo",
                title = "Espejismo Carmesí",
                artist = "Seda & Pulso",
                album = "Geometría Dorada",
                durationMs = 22_000L,
                rawResId = R.raw.track_espejismo,
                drawableResId = R.drawable.img_cover_mirage,
                fallbackAccent = Color(0xFFFF5FA2),
                fallbackSecondary = Color(0xFF39132E)
            )
        )
    }

    private fun createBundledTrack(
        id: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        rawResId: Int,
        drawableResId: Int,
        fallbackAccent: Color,
        fallbackSecondary: Color
    ): Track {
        val uri = Uri.parse("android.resource://${context.packageName}/$rawResId")
        val palette = runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 12 }
            val bmp = BitmapFactory.decodeResource(context.resources, drawableResId, opts)
            if (bmp != null) {
                CoverColorExtractor.extractFromBitmap(bmp, fallbackAccent, fallbackSecondary)
                    .also { bmp.recycle() }
            } else {
                null
            }
        }.getOrNull()

        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            audioUri = uri,
            artworkSource = ArtworkSource.Resource(drawableResId),
            accentColor = palette?.accentColor ?: fallbackAccent,
            secondaryColor = palette?.secondaryColor ?: fallbackSecondary,
            isImported = false
        )
    }

    suspend fun loadImportedTracks(uris: List<String>): List<Track> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uriString ->
            val uri = Uri.parse(uriString)
            extractTrackFromUri(uri)
        }
    }

    suspend fun importAudioUris(uris: List<Uri>): List<Track> = withContext(Dispatchers.IO) {
        val newlyParsed = mutableListOf<Track>()
        for (uri in uris) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            extractTrackFromUri(uri)?.let { newlyParsed.add(it) }
        }
        if (newlyParsed.isNotEmpty()) {
            val currentPrefs = preferencesFlow.first()
            val merged = (currentPrefs.importedUris + newlyParsed.map { it.audioUri.toString() }).distinct()
            context.ondaDataStore.edit { prefs ->
                prefs[Keys.IMPORTED_URIS] = merged.joinToString("\n")
            }
        }
        newlyParsed
    }

    suspend fun removeImportedTrack(uriString: String) {
        context.ondaDataStore.edit { prefs ->
            val current = prefs[Keys.IMPORTED_URIS]
                ?.split("\n")
                ?.filter { it.isNotBlank() && it != uriString }
                ?: emptyList()
            prefs[Keys.IMPORTED_URIS] = current.joinToString("\n")
        }
    }

    suspend fun toggleFavorite(trackId: String) {
        context.ondaDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_IDS] ?: emptySet()
            prefs[Keys.FAVORITE_IDS] = if (trackId in current) current - trackId else current + trackId
        }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.ondaDataStore.edit { prefs ->
            prefs[Keys.SHUFFLE_ENABLED] = enabled
        }
    }

    suspend fun setRepeatMode(mode: RepeatMode) {
        context.ondaDataStore.edit { prefs ->
            prefs[Keys.REPEAT_MODE] = mode.name
        }
    }

    suspend fun setCoverShapeStyle(style: CoverShapeStyle) {
        context.ondaDataStore.edit { prefs ->
            prefs[Keys.COVER_SHAPE] = style.name
        }
    }

    suspend fun setLastTrackId(trackId: String) {
        context.ondaDataStore.edit { prefs ->
            prefs[Keys.LAST_TRACK_ID] = trackId
        }
    }

    private fun extractTrackFromUri(uri: Uri): Track? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val rawDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val artBytes = retriever.embeddedPicture

            val fallbackName = queryDisplayName(uri)
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: "Pista local"

            val title = rawTitle?.takeIf { it.isNotBlank() } ?: fallbackName
            val artist = rawArtist?.takeIf { it.isNotBlank() } ?: "Artista desconocido"
            val album = rawAlbum?.takeIf { it.isNotBlank() } ?: "Biblioteca local"

            val (fallbackAccent, fallbackSecondary) = deterministicPalette(title)
            val palette = if (artBytes != null) {
                CoverColorExtractor.extractFromBytes(artBytes, fallbackAccent, fallbackSecondary)
            } else {
                null
            }

            Track(
                id = "imported_${uri.toString().hashCode()}",
                title = title,
                artist = artist,
                album = album,
                durationMs = rawDuration,
                audioUri = uri,
                artworkSource = if (artBytes != null) ArtworkSource.EmbeddedBytes(artBytes) else ArtworkSource.GeneratedGradient,
                accentColor = palette?.accentColor ?: fallbackAccent,
                secondaryColor = palette?.secondaryColor ?: fallbackSecondary,
                isImported = true
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }

    private fun deterministicPalette(seed: String): Pair<Color, Color> {
        val palettes = listOf(
            Color(0xFFFF8A65) to Color(0xFF3E1F18),
            Color(0xFF64B5F6) to Color(0xFF14273D),
            Color(0xFF81C784) to Color(0xFF172D1A),
            Color(0xFFBA68C8) to Color(0xFF2D1636),
            Color(0xFFFFD54F) to Color(0xFF332910),
            Color(0xFF4DD0E1) to Color(0xFF112C33)
        )
        return palettes[abs(seed.hashCode()) % palettes.size]
    }
}
