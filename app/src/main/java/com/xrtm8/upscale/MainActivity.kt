package com.xrtm8.upscale

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
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
                    onSetShowResult = viewModel::setShowResultPreview,
                    onUpdateOptions = viewModel::updateOptions,
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
                    onShareOutput = viewModel::shareCompletedOutput,
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
}
