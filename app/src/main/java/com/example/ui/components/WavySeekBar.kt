package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun WavySeekBar(
    progress: Float,
    isPlaying: Boolean,
    accentColor: Color,
    trackColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    bufferedProgress: Float = 0f,
    durationMs: Long = 0L,
    enabled: Boolean = true,
    onScrubbing: (Float?) -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }
    var trackWidthPx by remember { mutableIntStateOf(1) }

    val displayProgress = (if (isDragging) dragProgress else progress).coerceIn(0f, 1f)
    val clampedBuffered = bufferedProgress.coerceIn(displayProgress, 1f)

    val density = LocalDensity.current
    val horizontalPadPx = with(density) { 6.dp.toPx() }

    fun xToFraction(x: Float, widthPx: Float): Float {
        val usable = (widthPx - horizontalPadPx * 2f).coerceAtLeast(1f)
        return ((x - horizontalPadPx) / usable).coerceIn(0f, 1f)
    }

    val waveAmplitude by animateFloatAsState(
        targetValue = if (isPlaying && !isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "wave_amplitude"
    )

    val thumbHeight by animateDpAsState(
        targetValue = if (isDragging) 26.dp else 20.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumb_height"
    )

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.5.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumb_width"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wave_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase_value"
    )

    val currentPosMs = if (durationMs > 0L) (displayProgress * durationMs).toLong() else 0L
    val stateText = if (durationMs > 0L) {
        "${formatDuration(currentPosMs)} de ${formatDuration(durationMs)}"
    } else {
        "${(displayProgress * 100).roundToInt()}%"
    }

    val onAccentColor = if (accentColor.luminance() > 0.52f) {
        Color(0xFF141118)
    } else {
        Color(0xFFFFFBFF)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { trackWidthPx = it.width.coerceAtLeast(1) }
            .testTag("wavy_seekbar")
            .semantics {
                contentDescription = "Progreso de reproducción"
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(displayProgress, 0f..1f)
                if (enabled) {
                    setProgress { targetValue ->
                        val clamped = targetValue.coerceIn(0f, 1f)
                        onSeek(clamped)
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val fraction = xToFraction(offset.x, size.width.toFloat())
                    onScrubbing(null)
                    onSeek(fraction)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val fraction = xToFraction(offset.x, size.width.toFloat())
                        dragProgress = fraction
                        onScrubbing(fraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val fraction = xToFraction(change.position.x, size.width.toFloat())
                        dragProgress = fraction
                        onScrubbing(fraction)
                    },
                    onDragEnd = {
                        onScrubbing(null)
                        onSeek(dragProgress)
                        isDragging = false
                    },
                    onDragCancel = {
                        onScrubbing(null)
                        isDragging = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Live scrub timestamp tooltip floating above the thumb while dragging
        AnimatedVisibility(
            visible = isDragging && durationMs > 0L,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val usable = (trackWidthPx - horizontalPadPx * 2f).coerceAtLeast(1f)
                    val thumbCenterPx = horizontalPadPx + usable * displayProgress
                    val pillHalfWidthPx = with(density) { 28.dp.roundToPx() }
                    val maxOffset = (trackWidthPx - pillHalfWidthPx * 2).coerceAtLeast(0)
                    val x = (thumbCenterPx.roundToInt() - pillHalfWidthPx).coerceIn(0, maxOffset)
                    val y = with(density) { (-28).dp.roundToPx() }
                    IntOffset(x = x, y = y)
                }
        ) {
            Surface(
                color = accentColor,
                contentColor = onAccentColor,
                shape = CircleShape,
                shadowElevation = 6.dp,
                modifier = Modifier.testTag("scrub_preview_pill")
            ) {
                Text(
                    text = formatDuration(currentPosMs),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        ) {
            val centerY = size.height / 2f
            val padPx = horizontalPadPx
            val usableWidth = (size.width - padPx * 2f).coerceAtLeast(1f)
            val activeEndX = padPx + usableWidth * displayProgress
            val bufferedEndX = padPx + usableWidth * clampedBuffered
            val strokeWidth = 3.5.dp.toPx()
            val maxAmp = 3.2.dp.toPx() * waveAmplitude
            val waveLength = 26.dp.toPx()
            val thumbGap = 6.dp.toPx()

            // 1. Inactive remaining track
            val inactiveStartX = (activeEndX + thumbGap).coerceAtMost(size.width - padPx)
            if (inactiveStartX < size.width - padPx) {
                drawLine(
                    color = trackColor,
                    start = Offset(inactiveStartX, centerY),
                    end = Offset(size.width - padPx, centerY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 2. Buffered segment ahead of current playback position
            if (bufferedEndX > inactiveStartX + 2.dp.toPx()) {
                drawLine(
                    color = accentColor.copy(alpha = 0.30f),
                    start = Offset(inactiveStartX, centerY),
                    end = Offset(bufferedEndX, centerY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 3. Active played wavy (or straight when paused/scrubbing) track
            if (displayProgress > 0.002f) {
                val path = Path()
                val step = 3f
                var x = padPx
                val startY = centerY + maxAmp * sin((x / waveLength) * 2f * PI.toFloat() - phase)
                path.moveTo(x, startY)
                while (x <= activeEndX) {
                    val y = centerY + maxAmp * sin((x / waveLength) * 2f * PI.toFloat() - phase)
                    path.lineTo(x, y)
                    x += step
                }
                val endY = centerY + maxAmp * sin((activeEndX / waveLength) * 2f * PI.toFloat() - phase)
                path.lineTo(activeEndX, endY)

                drawPath(
                    path = path,
                    color = accentColor,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 4. Soft halo behind thumb while actively scrubbing
            if (isDragging) {
                drawCircle(
                    color = accentColor.copy(alpha = 0.22f),
                    radius = 16.dp.toPx(),
                    center = Offset(activeEndX, centerY)
                )
            }

            // 5. Expressive vertical pill thumb
            val thumbWidthPx = thumbWidth.toPx()
            val thumbHeightPx = thumbHeight.toPx()
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(
                    x = activeEndX - thumbWidthPx / 2f,
                    y = centerY - thumbHeightPx / 2f
                ),
                size = Size(thumbWidthPx, thumbHeightPx),
                cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
            )
        }
    }
}
