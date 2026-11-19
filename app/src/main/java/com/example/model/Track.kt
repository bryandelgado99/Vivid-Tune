package com.example.model

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
sealed interface ArtworkSource {
    @Immutable
    data class Resource(@DrawableRes val resId: Int) : ArtworkSource

    @Immutable
    data class EmbeddedBytes(val bytes: ByteArray) : ArtworkSource {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EmbeddedBytes) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    @Immutable
    data object GeneratedGradient : ArtworkSource
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class CoverShapeStyle(val label: String) {
    SQUIRCLE("Editorial"),
    SCALLOP("Onda"),
    VINYL_DISC("Vinilo"),
    ARCH("Arco")
}

@Immutable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val audioUri: Uri,
    val artworkSource: ArtworkSource,
    val accentColor: Color,
    val secondaryColor: Color,
    val isImported: Boolean = false
)
