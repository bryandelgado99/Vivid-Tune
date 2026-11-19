package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.player.MusicViewModel
import com.example.ui.NowPlayingScreen
import com.example.ui.theme.OndaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            OndaTheme(darkTheme = true, dynamicColor = false) {
                val viewModel: MusicViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                val audioPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                    onResult = { uris ->
                        if (uris.isNotEmpty()) {
                            viewModel.importAudioFiles(uris)
                        }
                    }
                )

                BackHandler(enabled = uiState.isQueueSheetOpen) {
                    viewModel.setQueueSheetOpen(false)
                }

                NowPlayingScreen(
                    uiState = uiState,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onNextTrack = viewModel::nextTrack,
                    onPreviousTrack = viewModel::previousTrack,
                    onSelectTrack = { index -> viewModel.selectTrack(index, autoPlay = true) },
                    onSeek = viewModel::seekToFraction,
                    onScrubbing = viewModel::onScrubbing,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeatMode = viewModel::cycleRepeatMode,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onCycleCoverShape = viewModel::cycleCoverShapeStyle,
                    onOpenQueue = { viewModel.setQueueSheetOpen(true) },
                    onDismissQueue = { viewModel.setQueueSheetOpen(false) },
                    onSetShowFavoritesOnly = viewModel::setShowFavoritesOnly,
                    onAddMusicClick = {
                        audioPickerLauncher.launch(arrayOf("audio/*"))
                    },
                    onRemoveImported = viewModel::removeImportedTrack,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
