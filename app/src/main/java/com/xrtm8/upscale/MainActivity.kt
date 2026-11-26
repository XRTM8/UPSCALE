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
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.xrtm8.upscale.ui.StudioScreen
import com.xrtm8.upscale.ui.StudioViewModel
import com.xrtm8.upscale.ui.theme.UpscaleStudioTheme

class MainActivity : ComponentActivity() {
    private val viewModel: StudioViewModel by viewModels()

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val bg = Color.rgb(12, 14, 17)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(bg),
            navigationBarStyle = SystemBarStyle.dark(bg),
        )
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

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

            UpscaleStudioTheme {
                StudioScreen(
                    state = state,
                    shaderParamsRef = viewModel.liveShaderParams,
                    onPickVideo = {
                        pickVideoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    },
                    onToggleLanguage = viewModel::toggleLanguage,
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
                )
            }
        }
    }
}
