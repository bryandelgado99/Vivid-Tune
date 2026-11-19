package com.example.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min

data class ExtractedCoverPalette(
    val accentColor: Color,
    val secondaryColor: Color
)

object CoverColorExtractor {
    fun extractFromBytes(bytes: ByteArray, fallbackAccent: Color, fallbackSecondary: Color): ExtractedCoverPalette {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 8
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return ExtractedCoverPalette(fallbackAccent, fallbackSecondary)
        return try {
            extractFromBitmap(bitmap, fallbackAccent, fallbackSecondary)
        } finally {
            bitmap.recycle()
        }
    }

    fun extractFromBitmap(
        bitmap: Bitmap,
        fallbackAccent: Color,
        fallbackSecondary: Color
    ): ExtractedCoverPalette {
        val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        var bestVibrant = fallbackAccent
        var bestScore = -1f
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        val hsv = FloatArray(3)
        for (y in 2 until 22) {
            for (x in 2 until 22) {
                val pixel = scaled.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                rSum += r
                gSum += g
                bSum += b
                count++

                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val saturation = hsv[1]
                val value = hsv[2]
                // Prefer vivid yet readable colors for dark atmospheric surfaces
                if (value in 0.25f..0.95f) {
                    val lightnessPenalty = 1f - kotlin.math.abs(value - 0.68f)
                    val score = saturation * 2.2f + lightnessPenalty
                    if (score > bestScore) {
                        bestScore = score
                        bestVibrant = boostSaturationAndBrightness(pixel)
                    }
                }
            }
        }
        if (scaled != bitmap) {
            scaled.recycle()
        }

        val secondary = if (count > 0) {
            val avgColor = android.graphics.Color.rgb(
                (rSum / count).toInt(),
                (gSum / count).toInt(),
                (bSum / count).toInt()
            )
            darkenForAtmosphere(avgColor)
        } else {
            fallbackSecondary
        }

        return ExtractedCoverPalette(
            accentColor = if (bestScore > 0.25f) bestVibrant else fallbackAccent,
            secondaryColor = secondary
        )
    }

    private fun boostSaturationAndBrightness(argb: Int): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        hsv[1] = min(0.88f, max(0.45f, hsv[1] * 1.15f))
        hsv[2] = min(0.96f, max(0.72f, hsv[2]))
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun darkenForAtmosphere(argb: Int): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        hsv[1] = min(0.75f, max(0.25f, hsv[1]))
        hsv[2] = min(0.42f, max(0.18f, hsv[2] * 0.6f))
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
