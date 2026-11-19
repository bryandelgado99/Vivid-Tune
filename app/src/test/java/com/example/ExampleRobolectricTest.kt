package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.example.model.ArtworkSource
import com.example.model.CoverShapeStyle
import com.example.model.Track
import com.example.player.MusicUiState
import com.example.ui.NowPlayingScreen
import com.example.ui.theme.OndaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleTracks() = listOf(
        Track(
            id = "test_1",
            title = "Solsticio de Terracota",
            artist = "Alba & Meridiano",
            album = "Arcos del Sur",
            durationMs = 24_000L,
            audioUri = Uri.parse("android.resource://com.example/${R.raw.track_solsticio}"),
            artworkSource = ArtworkSource.Resource(R.drawable.img_cover_solstice),
            accentColor = Color(0xFFFF7A45),
            secondaryColor = Color(0xFF421C14)
        ),
        Track(
            id = "test_2",
            title = "Nocturno en Cobalto",
            artist = "Prisma Sónico",
            album = "Cristal Líquido",
            durationMs = 22_000L,
            audioUri = Uri.parse("android.resource://com.example/${R.raw.track_nocturno}"),
            artworkSource = ArtworkSource.Resource(R.drawable.img_cover_nocturne),
            accentColor = Color(0xFF46C2FF),
            secondaryColor = Color(0xFF0E1D3B)
        )
    )

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Onda", appName)
    }

    @Test
    fun `now playing screen displays hero cover and transport controls`() {
        var playPauseClicked = false
        var shapeClicked = false

        composeRule.setContent {
            OndaTheme {
                NowPlayingScreen(
                    uiState = MusicUiState(
                        tracks = sampleTracks(),
                        currentIndex = 0,
                        isPlaying = false,
                        positionMs = 6_000L,
                        durationMs = 24_000L,
                        coverShapeStyle = CoverShapeStyle.SCALLOP,
                        isLoading = false
                    ),
                    onTogglePlayPause = { playPauseClicked = true },
                    onNextTrack = {},
                    onPreviousTrack = {},
                    onSelectTrack = {},
                    onSeek = {},
                    onToggleShuffle = {},
                    onCycleRepeatMode = {},
                    onToggleFavorite = {},
                    onCycleCoverShape = { shapeClicked = true },
                    onOpenQueue = {},
                    onDismissQueue = {},
                    onSetShowFavoritesOnly = {},
                    onAddMusicClick = {},
                    onRemoveImported = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_cover_pager").assertIsDisplayed()
        composeRule.onNodeWithTag("track_title").assertIsDisplayed()
        composeRule.onNodeWithText("Solsticio de Terracota").assertIsDisplayed()
        composeRule.onNodeWithTag("wavy_seekbar").assertIsDisplayed()
        composeRule.onNodeWithTag("play_pause_button").performClick()
        composeRule.onNodeWithTag("shape_toggle_button").performClick()

        assertTrue(playPauseClicked)
        assertTrue(shapeClicked)
    }

    @Test
    fun `seekable progress bar reflects playback position and triggers seek`() {
        var seekedFraction: Float? = null

        composeRule.setContent {
            OndaTheme {
                NowPlayingScreen(
                    uiState = MusicUiState(
                        tracks = sampleTracks(),
                        currentIndex = 0,
                        isPlaying = false,
                        positionMs = 6_000L,
                        bufferedPositionMs = 18_000L,
                        durationMs = 24_000L,
                        isLoading = false
                    ),
                    onTogglePlayPause = {},
                    onNextTrack = {},
                    onPreviousTrack = {},
                    onSelectTrack = {},
                    onSeek = { fraction -> seekedFraction = fraction },
                    onToggleShuffle = {},
                    onCycleRepeatMode = {},
                    onToggleFavorite = {},
                    onCycleCoverShape = {},
                    onOpenQueue = {},
                    onDismissQueue = {},
                    onSetShowFavoritesOnly = {},
                    onAddMusicClick = {},
                    onRemoveImported = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("elapsed_time_label").assertTextEquals("0:06")
        composeRule.onNodeWithTag("total_duration_label").assertTextEquals("0:24")
        composeRule.onNodeWithTag("wavy_seekbar").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.25f, 0f..1f)
            )
        )

        composeRule.onNodeWithTag("wavy_seekbar").performSemanticsAction(SemanticsActions.SetProgress) {
            it(0.75f)
        }
        composeRule.waitForIdle()

        assertEquals(0.75f, seekedFraction ?: -1f, 0.001f)
    }

    @Test
    fun `ui state displayedPositionMs prioritizes active scrub position`() {
        val idleState = MusicUiState(positionMs = 5_000L, scrubPositionMs = null, durationMs = 24_000L)
        val scrubbingState = idleState.copy(scrubPositionMs = 15_000L)

        assertEquals(5_000L, idleState.displayedPositionMs)
        assertEquals(15_000L, scrubbingState.displayedPositionMs)
    }
}
