package com.xrtm8.upscale

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.xrtm8.upscale.engine.AppUpdater
import com.xrtm8.upscale.engine.UpdateStatus
import com.xrtm8.upscale.ui.StudioScreen
import com.xrtm8.upscale.ui.StudioViewModel
import com.xrtm8.upscale.ui.theme.UpscaleStudioTheme

class MainActivity : ComponentActivity() {
    private val viewModel: StudioViewModel by viewModels()

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            handleIncomingVideoIntent(intent)
        }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            SideEffect {
                val style = if (state.isAmoledWhite) {
                    SystemBarStyle.light(Color.WHITE, Color.WHITE)
                } else {
                    SystemBarStyle.dark(Color.BLACK)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            val pickVideoLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia(),
            ) { uri ->
                if (uri != null) {
                    viewModel.importVideo(uri)
                }
            }

            val pickBatchVideosLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickMultipleVisualMedia(10),
            ) { uris ->
                if (uris.isNotEmpty()) {
                    viewModel.enqueueBatchVideos(uris)
                }
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {
                viewModel.startExport()
            }

            val installPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) {
                val ready = viewModel.uiState.value.updateStatus as? UpdateStatus.ReadyToInstall
                if (ready != null && AppUpdater.canRequestPackageInstalls(this)) {
                    startActivity(AppUpdater.createInstallIntent(this, ready.apkFile))
                }
            }

            UpscaleStudioTheme(isAmoledWhite = state.isAmoledWhite) {
                StudioScreen(
                    state = state,
                    shaderParamsRef = viewModel.liveShaderParams,
                    onPickVideo = {
                        pickVideoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    },
                    onToggleLanguage = viewModel::toggleLanguage,
                    onToggleTheme = viewModel::toggleTheme,
                    onOpenSheet = viewModel::openSheet,
                    onCloseSheet = viewModel::closeSheet,
                    onToggleSplitCompare = viewModel::toggleSplitCompare,
                    onSplitPositionChange = viewModel::updateSplitPosition,
                    onToggleSafeZones = viewModel::toggleSafeZones,
                    onToggleZoom = viewModel::toggleZoom,
                    onZoomChange = viewModel::updateZoom,
                    onSetShowResult = viewModel::setShowResultPreview,
                    onUpdateOptions = viewModel::updateOptions,
                    onApplyPreset = viewModel::applyPreset,
                    onApplyCustomPreset = viewModel::applyCustomPreset,
                    onSaveCustomPreset = viewModel::saveCustomPreset,
                    onDeleteCustomPreset = viewModel::deleteCustomPreset,
                    onResetFilters = viewModel::resetEnhancementDefaults,
                    onSaveSnapshot = viewModel::saveSnapshot,
                    onSnapshotFailed = viewModel::reportSnapshotFailed,
                    onStartExport = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.startExport()
                        }
                    },
                    onCancelExport = viewModel::cancelExport,
                    onPickBatchVideos = {
                        pickBatchVideosLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    },
                    onRemoveBatchItem = viewModel::removeBatchItem,
                    onClearDoneBatch = viewModel::clearCompletedBatchItems,
                    onStartBatchExport = viewModel::startBatchExport,
                    onSeekPreview = viewModel::requestPreviewSeek,
                    onOpenOutput = viewModel::openCompletedOutput,
                    onShareOutput = viewModel::shareCompletedOutput,
                    onOpenRecent = { item -> viewModel.openVideoUri(item.uriString) },
                    onShareRecent = { item -> viewModel.shareVideoUri(item.uriString, item.name) },
                    onImportRecent = { item ->
                        viewModel.closeSheet()
                        viewModel.importVideo(Uri.parse(item.uriString))
                    },
                    onRefreshRecents = viewModel::refreshRecentExports,
                    onDismissError = viewModel::dismissError,
                    onCheckUpdate = { viewModel.checkForUpdates(manual = true) },
                    onStartUpdateDownload = viewModel::startUpdateDownload,
                    onInstallUpdate = {
                        val ready = state.updateStatus as? UpdateStatus.ReadyToInstall
                        if (ready != null) {
                            if (AppUpdater.canRequestPackageInstalls(this)) {
                                startActivity(AppUpdater.createInstallIntent(this, ready.apkFile))
                            } else {
                                installPermissionLauncher.launch(AppUpdater.createUnknownSourcesSettingsIntent(this))
                            }
                        }
                    },
                    onDismissUpdateBanner = viewModel::dismissUpdateBanner,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingVideoIntent(intent)
    }

    private fun handleIncomingVideoIntent(intent: Intent?) {
        if (intent == null) return
        val incomingUri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (incomingUri != null) {
            viewModel.importVideo(incomingUri)
        }
    }
}
