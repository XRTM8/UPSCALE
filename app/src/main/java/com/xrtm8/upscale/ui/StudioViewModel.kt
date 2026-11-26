package com.xrtm8.upscale.ui

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xrtm8.upscale.data.PreferencesStore
import com.xrtm8.upscale.engine.ExportForegroundService
import com.xrtm8.upscale.engine.ExportRepository
import com.xrtm8.upscale.engine.LiveShaderParams
import com.xrtm8.upscale.engine.MediaInspector
import com.xrtm8.upscale.model.AppLanguage
import com.xrtm8.upscale.model.DeviceCapabilities
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlan
import com.xrtm8.upscale.model.ExportPlanCalculator
import com.xrtm8.upscale.model.ExportState
import com.xrtm8.upscale.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

enum class StudioSheet {
    NONE,
    PROCESSING,
    OUTPUT,
}

data class StudioUiState(
    val language: AppLanguage = AppLanguage.AR,
    val options: ExportOptions = ExportOptions(),
    val capabilities: DeviceCapabilities = DeviceCapabilities(),
    val source: VideoMetadata? = null,
    val exportState: ExportState = ExportState.Idle,
    val activeSheet: StudioSheet = StudioSheet.NONE,
    val splitCompareEnabled: Boolean = false,
    val splitPosition: Float = 0.5f,
    val showSafeZones: Boolean = false,
    val showResultPreview: Boolean = false,
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
) {
    val isExportRunning: Boolean
        get() = exportState is ExportState.Preparing || exportState is ExportState.Running

    val isLocked: Boolean
        get() = isImporting || isExportRunning

    val completedOutput: ExportState.Completed?
        get() = exportState as? ExportState.Completed

    val activePreviewUri: String?
        get() = if (showResultPreview && completedOutput != null) {
            completedOutput?.outputUriString
        } else {
            source?.uriString
        }

    val activePreviewMetadata: VideoMetadata?
        get() = if (showResultPreview && completedOutput != null) {
            completedOutput?.metadata
        } else {
            source
        }

    fun targetDimensions(): Pair<Int, Int> =
        ExportPlanCalculator.targetDimensions(options.postFormat, options.resolution)

    fun currentPlan(): ExportPlan? {
        val meta = source ?: return null
        return ExportPlanCalculator.buildPlan(meta, options, capabilities).getOrNull()
    }
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesStore(application)
    val liveShaderParams = AtomicReference(LiveShaderParams())

    private val _uiState = MutableStateFlow(
        StudioUiState(
            language = prefs.loadLanguage(),
            options = prefs.loadOptions(),
        ),
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ExportRepository.cleanupStaleCache(application)
                MediaInspector.inspectDeviceCapabilities(application)
            }.let { caps ->
                _uiState.update { state ->
                    val normalized = state.options.normalizedForPlatform(caps)
                    syncShaderParams(normalized, state.splitCompareEnabled, state.splitPosition, state.showResultPreview)
                    state.copy(capabilities = caps, options = normalized)
                }
            }
        }

        viewModelScope.launch {
            ExportRepository.state.collect { nextExportState ->
                _uiState.update { state ->
                    val autoShowResult = nextExportState is ExportState.Completed &&
                        state.exportState !is ExportState.Completed
                    val showResult = if (autoShowResult) true else if (nextExportState is ExportState.Idle) false else state.showResultPreview
                    val next = state.copy(
                        exportState = nextExportState,
                        showResultPreview = showResult,
                    )
                    syncShaderParams(next.options, next.splitCompareEnabled, next.splitPosition, next.showResultPreview)
                    next
                }
            }
        }
    }

    fun toggleLanguage() {
        _uiState.update { state ->
            val nextLang = if (state.language == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR
            prefs.saveLanguage(nextLang)
            state.copy(language = nextLang)
        }
    }

    fun openSheet(sheet: StudioSheet) {
        _uiState.update { it.copy(activeSheet = if (it.activeSheet == sheet) StudioSheet.NONE else sheet) }
    }

    fun closeSheet() {
        _uiState.update { it.copy(activeSheet = StudioSheet.NONE) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun toggleSplitCompare() {
        _uiState.update { state ->
            val nextEnabled = !state.splitCompareEnabled
            syncShaderParams(state.options, nextEnabled, state.splitPosition, state.showResultPreview)
            state.copy(splitCompareEnabled = nextEnabled)
        }
    }

    fun updateSplitPosition(fraction: Float) {
        val clamped = fraction.coerceIn(0.05f, 0.95f)
        _uiState.update { state ->
            syncShaderParams(state.options, state.splitCompareEnabled, clamped, state.showResultPreview)
            state.copy(splitPosition = clamped)
        }
    }

    fun toggleSafeZones() {
        _uiState.update { it.copy(showSafeZones = !it.showSafeZones) }
    }

    fun setShowResultPreview(showResult: Boolean) {
        _uiState.update { state ->
            syncShaderParams(state.options, state.splitCompareEnabled, state.splitPosition, showResult)
            state.copy(showResultPreview = showResult)
        }
    }

    fun updateOptions(transform: (ExportOptions) -> ExportOptions) {
        _uiState.update { state ->
            if (state.isLocked) return@update state
            val updated = transform(state.options).normalizedForPlatform(state.capabilities)
            prefs.saveOptions(updated)
            syncShaderParams(updated, state.splitCompareEnabled, state.splitPosition, state.showResultPreview)
            state.copy(options = updated)
        }
    }

    fun importVideo(uri: Uri) {
        if (_uiState.value.isLocked) return
        val app = getApplication<Application>()
        _uiState.update { it.copy(isImporting = true, errorMessage = null) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    runCatching {
                        app.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    val caps = MediaInspector.inspectDeviceCapabilities(app)
                    val meta = MediaInspector.inspectUri(app, uri).getOrThrow()
                    caps to meta
                }
            }

            result.onSuccess { (caps, meta) ->
                ExportRepository.resetIfTerminal()
                _uiState.update { state ->
                    val nextOptions = state.options.copy(
                        cropOffsetX = 0f,
                        cropOffsetY = 0f,
                        trimStartMs = 0L,
                        trimEndMs = meta.durationMs,
                    ).normalizedForPlatform(caps)
                    syncShaderParams(nextOptions, state.splitCompareEnabled, state.splitPosition, false)
                    state.copy(
                        source = meta,
                        capabilities = caps,
                        options = nextOptions,
                        exportState = ExportState.Idle,
                        showResultPreview = false,
                        activeSheet = StudioSheet.NONE,
                        isImporting = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = error.message ?: "Unable to read the selected video.",
                    )
                }
            }
        }
    }

    @androidx.media3.common.util.UnstableApi
    fun startExport() {
        val state = _uiState.value
        if (state.isLocked) return
        val source = state.source ?: return
        val app = getApplication<Application>()

        viewModelScope.launch {
            val caps = withContext(Dispatchers.IO) {
                MediaInspector.inspectDeviceCapabilities(app)
            }
            val planResult = ExportPlanCalculator.buildPlan(source, state.options, caps)
            planResult.onSuccess { plan ->
                _uiState.update {
                    it.copy(
                        capabilities = caps,
                        activeSheet = StudioSheet.NONE,
                        showResultPreview = false,
                        errorMessage = null,
                    )
                }
                ExportRepository.setState(ExportState.Preparing)
                ExportForegroundService.start(app, source, state.options, plan)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        capabilities = caps,
                        errorMessage = error.message ?: "Unable to prepare export.",
                    )
                }
            }
        }
    }

    fun cancelExport() {
        ExportRepository.cancelActiveExport()
    }

    fun shareCompletedOutput() {
        val completed = _uiState.value.completedOutput ?: return
        val app = getApplication<Application>()
        val uri = Uri.parse(completed.outputUriString)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(app.contentResolver, completed.outputName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(share, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(chooser)
    }

    private fun syncShaderParams(
        options: ExportOptions,
        splitEnabled: Boolean,
        splitPos: Float,
        showingExportedResult: Boolean,
    ) {
        liveShaderParams.set(
            LiveShaderParams(
                denoise = options.denoiseStrength,
                detail = options.detailStrength,
                clarity = options.clarityStrength,
                splitX = if (splitEnabled && !showingExportedResult) splitPos else -1f,
                showOriginalOnly = showingExportedResult,
            ),
        )
    }
}
