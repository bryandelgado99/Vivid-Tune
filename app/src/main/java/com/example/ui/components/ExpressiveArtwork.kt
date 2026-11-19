package com.example.ui.components

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.ArtworkSource
import com.example.model.CoverShapeStyle
import com.example.model.Track
import kotlin.math.abs

class ScallopMorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationDegrees: Float
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        matrix.reset()
        val minDim = minOf(size.width, size.height)
        matrix.scale(minDim / 2f, minDim / 2f)
        matrix.translate(1f, 1f)
        matrix.rotateZ(rotationDegrees)
        val path = morph.toPath(progress = progress.coerceIn(0f, 1f)).asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

@Composable
fun TrackArtworkImage(
    track: Track,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    when (val source = track.artworkSource) {
        is ArtworkSource.Resource -> {
            Image(
                painter = painterResource(id = source.resId),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        }
        is ArtworkSource.EmbeddedBytes -> {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(source.bytes)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        }
        ArtworkSource.GeneratedGradient -> {
            Box(
                modifier = modifier.background(
                    Brush.linearGradient(
                        colors = listOf(
                            track.accentColor.copy(alpha = 0.92f),
                            track.secondaryColor,
                            Color(0xFF141118)
                        )
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.14f),
                        radius = size.minDimension * 0.34f,
                        center = Offset(size.width * 0.72f, size.height * 0.28f)
                    )
                    drawCircle(
                        color = track.accentColor.copy(alpha = 0.28f),
                        radius = size.minDimension * 0.44f,
                        center = Offset(size.width * 0.25f, size.height * 0.78f)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
fun ExpressiveHeroCoverPager(
    tracks: List<Track>,
    pagerState: PagerState,
    isPlaying: Boolean,
    shapeStyle: CoverShapeStyle,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cover_motion")
    val slowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "disc_rotation"
    )
    val scallopBreath by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scallop_breath"
    )

    val shapeA = remember {
        RoundedPolygon(
            numVertices = 12,
            rounding = CornerRounding(0.32f)
        )
    }
    val shapeB = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 12,
            innerRadius = 0.86f,
            rounding = CornerRounding(0.45f)
        )
    }
    val scallopMorph = remember(shapeA, shapeB) { Morph(shapeA, shapeB) }

    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 28.dp),
        pageSpacing = 16.dp,
        modifier = modifier.testTag("hero_cover_pager")
    ) { page ->
        val track = tracks[page]
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            .let { abs(it).coerceIn(0f, 1f) }
        val pageScale = 1f - (pageOffset * 0.10f)
        val pageAlpha = 1f - (pageOffset * 0.38f)

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        val playScale by animateFloatAsState(
            targetValue = when {
                isPressed -> 0.94f
                isPlaying -> 1.0f
                else -> 0.955f
            },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "cover_play_scale"
        )

        val squircleRadius by animateDpAsState(
            targetValue = if (isPlaying) 36.dp else 46.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "squircle_radius"
        )

        val activeShape: Shape = when (shapeStyle) {
            CoverShapeStyle.SQUIRCLE -> RoundedCornerShape(squircleRadius)
            CoverShapeStyle.SCALLOP -> ScallopMorphShape(
                morph = scallopMorph,
                progress = if (isPlaying) scallopBreath else 0.1f,
                rotationDegrees = if (isPlaying) slowRotation * 0.35f else 0f
            )
            CoverShapeStyle.VINYL_DISC -> CircleShape
            CoverShapeStyle.ARCH -> RoundedCornerShape(
                topStartPercent = 50,
                topEndPercent = 50,
                bottomStartPercent = 14,
                bottomEndPercent = 14
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pageScale * playScale
                    scaleY = pageScale * playScale
                    alpha = pageAlpha
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(
                        elevation = if (isPlaying) 28.dp else 14.dp,
                        shape = activeShape,
                        ambientColor = track.accentColor.copy(alpha = 0.65f),
                        spotColor = track.accentColor.copy(alpha = 0.75f)
                    )
                    .clip(activeShape)
                    .background(track.secondaryColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = Color.White.copy(alpha = 0.25f)),
                        onClick = onCoverClick
                    )
            ) {
                TrackArtworkImage(
                    track = track,
                    contentDescription = "${stringResource(R.string.cover_art_description)}: ${track.title}",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (shapeStyle == CoverShapeStyle.VINYL_DISC && isPlaying) {
                                rotationZ = slowRotation
                            }
                        }
                )

                // Subtle vinyl spindle center when in VINYL_DISC mode
                if (shapeStyle == CoverShapeStyle.VINYL_DISC) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF100E13).copy(alpha = 0.78f))
                            .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(track.accentColor)
                        )
                    }
                } else {
                    // Subtle glossy rim highlight for editorial depth
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.14f),
                                shape = activeShape
                            )
                    )
                }

                // Minimalist floating playback badge on bottom-end corner of cover
                if (isPlaying && page == pagerState.currentPage) {
                    Surface(
                        color = Color(0xFF100E13).copy(alpha = 0.62f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = track.accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
