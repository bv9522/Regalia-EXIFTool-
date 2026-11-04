package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.RegaliaBottomNav
import com.example.ui.components.RegaliaDestination
import com.example.ui.components.RegaliaTopBar
import com.example.ui.components.SystemDiagnosticsDialog
import com.example.ui.screens.browse.BrowseScreen
import com.example.ui.screens.browse.BrowseViewModel
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.history.HistoryViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.metadata.MetadataScreen
import com.example.ui.screens.metadata.MetadataViewModel
import com.example.ui.screens.timestamp.TimestampScreen
import com.example.ui.screens.timestamp.TimestampViewModel
import com.example.ui.theme.RegaliaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RegaliaTheme {
                RegaliaApp()
            }
        }
    }
}

@Composable
fun RegaliaApp(
    homeViewModel: HomeViewModel = viewModel(),
    browseViewModel: BrowseViewModel = viewModel(),
    metadataViewModel: MetadataViewModel = viewModel(),
    timestampViewModel: TimestampViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel()
) {
    var currentDestination by remember { mutableStateOf(RegaliaDestination.HOME) }
    var navigationHistory by remember { mutableStateOf(listOf(RegaliaDestination.HOME)) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    fun navigateTo(destination: RegaliaDestination) {
        if (currentDestination != destination) {
            navigationHistory = navigationHistory + destination
            currentDestination = destination
        }
    }

    BackHandler(enabled = navigationHistory.size > 1) {
        val newHistory = navigationHistory.dropLast(1)
        navigationHistory = newHistory
        currentDestination = newHistory.lastOrNull() ?: RegaliaDestination.HOME
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            RegaliaTopBar(
                title = when (currentDestination) {
                    RegaliaDestination.HOME -> "Regalia"
                    RegaliaDestination.BROWSE -> "Browse Files"
                    RegaliaDestination.METADATA -> "Metadata Inspector"
                    RegaliaDestination.TIMESTAMP -> "Timestamp Fixer"
                    RegaliaDestination.HISTORY -> "Operation History"
                },
                subtitle = when (currentDestination) {
                    RegaliaDestination.HOME -> "Android Metadata & Timestamp Engine"
                    RegaliaDestination.BROWSE -> "Storage Access Framework (SAF)"
                    RegaliaDestination.METADATA -> "EXIF, QuickTime, OOXML, Vorbis"
                    RegaliaDestination.TIMESTAMP -> "Read-Back Verified Timestamp Correction"
                    RegaliaDestination.HISTORY -> "Audit Log & Rollback Restoration"
                },
                onShowDiagnostics = { showDiagnosticsDialog = true }
            )
        },
        bottomBar = {
            RegaliaBottomNav(
                currentDestination = currentDestination,
                onNavigate = { dest -> navigateTo(dest) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                RegaliaDestination.HOME -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigate = { dest -> navigateTo(dest) },
                        onLoadSamplesToTimestamp = { samples ->
                            timestampViewModel.addFiles(samples)
                        }
                    )
                }

                RegaliaDestination.BROWSE -> {
                    BrowseScreen(
                        viewModel = browseViewModel,
                        onSendToTimestamp = { files ->
                            timestampViewModel.addFiles(files)
                            navigateTo(RegaliaDestination.TIMESTAMP)
                        },
                        onInspectMetadata = { file ->
                            metadataViewModel.inspectFile(file)
                            navigateTo(RegaliaDestination.METADATA)
                        }
                    )
                }

                RegaliaDestination.METADATA -> {
                    MetadataScreen(
                        viewModel = metadataViewModel,
                        onSendToTimestamp = { file ->
                            timestampViewModel.addFiles(listOf(file))
                            navigateTo(RegaliaDestination.TIMESTAMP)
                        }
                    )
                }

                RegaliaDestination.TIMESTAMP -> {
                    TimestampScreen(
                        viewModel = timestampViewModel,
                        onInspectMetadata = { task ->
                            metadataViewModel.inspectFile(task.fileItem)
                            navigateTo(RegaliaDestination.METADATA)
                        }
                    )
                }

                RegaliaDestination.HISTORY -> {
                    HistoryScreen(
                        viewModel = historyViewModel
                    )
                }
            }
        }
    }

    if (showDiagnosticsDialog) {
        SystemDiagnosticsDialog(onDismiss = { showDiagnosticsDialog = false })
    }
}
