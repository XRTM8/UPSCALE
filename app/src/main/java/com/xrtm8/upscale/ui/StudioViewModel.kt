package com.xrtm8.upscale.ui

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xrtm8.upscale.data.PreferencesStore
import com.xrtm8.upscale.engine.AppUpdater
import com.xrtm8.upscale.engine.ExportForegroundService
import com.xrtm8.upscale.engine.ExportRepository
import com.xrtm8.upscale.engine.LiveShaderParams
import com.xrtm8.upscale.engine.MediaInspector
import com.xrtm8.upscale.engine.RecentExportItem
import com.xrtm8.upscale.engine.UpdateStatus
import com.xrtm8.upscale.model.AppError
import com.xrtm8.upscale.model.AppLanguage
import com.xrtm8.upscale.model.DeviceCapabilities
import com.xrtm8.upscale.model.EnhancementPreset
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlan
import com.xrtm8.upscale.model.ExportPlanCalculator
import com.xrtm8.upscale.model.ExportState
import com.xrtm8.upscale.model.LocalizedExportException
import com.xrtm8.upscale.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    EXPORTS,
}

enum class SnapshotStatus {
    SAVED,
    FAILED,
}

data class StudioUiState(
    val language: AppLanguage = AppLanguage.AR,
    val isAmoledWhite: Boolean = false,
    val options: ExportOptions = ExportOptions(),
    val capabilities: DeviceCapabilities = DeviceCapabilities(),
    val source: VideoMetadata? = null,
    val exportState: ExportState = ExportState.Idle,
    val activeSheet: StudioSheet = StudioSheet.NONE,
    val splitCompareEnabled: Boolean = false,
    val splitPosition: Float = 0.5f,
    val showSafeZones: Boolean = false,
    val previewZoom: Float = 1f,
    val showResultPreview: Boolean = false,
    val isImporting: Boolean = false,
    val error: AppError? = null,
    val snapshotStatus: SnapshotStatus? = null,
    val recentExports: List<RecentExportItem> = emptyList(),
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
    val updateBannerVisible: Boolean = false,
) {
    val recentExport: RecentExportItem?
        get() = recentExports.firstOrNull()

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
            isAmoledWhite = prefs.loadAmoledWhite(),
            options = prefs.loadOptions(),
        ),
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ExportRepository.cleanupStaleCache(application)
                AppUpdater.cleanupUpdateCache(application)
                val caps = MediaInspector.inspectDeviceCapabilities(application)
                val recents = MediaInspector.queryRecentExports(application)
                caps to recents
            }.let { (caps, recents) ->
                _uiState.update { state ->
                    val normalized = state.options.normalizedForPlatform(caps)
                    syncShaderParams(normalized, state.splitCompareEnabled, state.splitPosition, state.showResultPreview)
                    state.copy(capabilities = caps, options = normalized, recentExports = recents)
                }
            }
            // Quiet, non-intrusive background update check at most once every 6 hours.
            delay(1_500L)
            val elapsed = System.currentTimeMillis() - prefs.lastUpdateCheckMs()
            if (elapsed >= 6L * 3600L * 1000L) {
                checkForUpdates(manual = false)
            }
        }

        viewModelScope.launch {
            ExportRepository.state.collect { nextExportState ->
                val recents = if (nextExportState is ExportState.Completed) {
                    withContext(Dispatchers.IO) { MediaInspector.queryRecentExports(application) }
                } else {
                    _uiState.value.recentExports
                }
                _uiState.update { state ->
                    val autoShowResult = nextExportState is ExportState.Completed &&
                        state.exportState !is ExportState.Completed
                    val showResult = if (autoShowResult) true else if (nextExportState is ExportState.Idle) false else state.showResultPreview
                    val next = state.copy(
                        exportState = nextExportState,
                        showResultPreview = showResult,
                        recentExports = recents,
                    )
                    syncShaderParams(next.options, next.splitCompareEnabled, next.splitPosition, next.showResultPreview)
                    next
                }
            }
        }
    }

    fun refreshRecentExports() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val recents = withContext(Dispatchers.IO) {
                MediaInspector.queryRecentExports(app)
            }
            _uiState.update { it.copy(recentExports = recents) }
        }
    }

    fun toggleLanguage() {
        _uiState.update { state ->
            val nextLang = if (state.language == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR
            prefs.saveLanguage(nextLang)
            state.copy(language = nextLang)
        }
    }

    fun toggleTheme() {
        _uiState.update { state ->
            val nextWhite = !state.isAmoledWhite
            prefs.saveAmoledWhite(nextWhite)
            state.copy(isAmoledWhite = nextWhite)
        }
    }

    fun openSheet(sheet: StudioSheet) {
        _uiState.update { it.copy(activeSheet = if (it.activeSheet == sheet) StudioSheet.NONE else sheet) }
    }

    fun closeSheet() {
        _uiState.update { it.copy(activeSheet = StudioSheet.NONE) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun checkForUpdates(manual: Boolean = true) {
        val current = _uiState.value.updateStatus
        if (current is UpdateStatus.Checking || current is UpdateStatus.Downloading) return
        if (manual) {
            _uiState.update { it.copy(updateStatus = UpdateStatus.Checking, updateBannerVisible = true) }
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                AppUpdater.checkLatestRelease()
            }
            prefs.saveLastUpdateCheckMs(System.currentTimeMillis())
            result.onSuccess { info ->
                if (info == null) {
                    _uiState.update {
                        it.copy(
                            updateStatus = if (manual) UpdateStatus.UpToDate else UpdateStatus.Idle,
                            updateBannerVisible = manual,
                        )
                    }
                } else {
                    val dismissed = prefs.dismissedUpdateVersionCode() == info.versionCode
                    _uiState.update {
                        it.copy(
                            updateStatus = UpdateStatus.Available(info),
                            updateBannerVisible = manual || !dismissed,
                        )
                    }
                }
            }.onFailure { error ->
                if (manual) {
                    _uiState.update {
                        it.copy(
                            updateStatus = UpdateStatus.Error(error.message ?: "Update check failed."),
                            updateBannerVisible = true,
                        )
                    }
                }
            }
        }
    }

    fun startUpdateDownload() {
        val available = _uiState.value.updateStatus as? UpdateStatus.Available ?: return
        val app = getApplication<Application>()
        _uiState.update {
            it.copy(
                updateStatus = UpdateStatus.Downloading(available.info, 0f),
                updateBannerVisible = true,
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                AppUpdater.downloadAndVerify(app, available.info) { progress ->
                    _uiState.update { state ->
                        if (state.updateStatus is UpdateStatus.Downloading) {
                            state.copy(updateStatus = UpdateStatus.Downloading(available.info, progress))
                        } else {
                            state
                        }
                    }
                }
            }
            result.onSuccess { apkFile ->
                _uiState.update {
                    it.copy(
                        updateStatus = UpdateStatus.ReadyToInstall(available.info, apkFile),
                        updateBannerVisible = true,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        updateStatus = UpdateStatus.Error(error.message ?: "Update verification failed."),
                        updateBannerVisible = true,
                    )
                }
            }
        }
    }

    fun dismissUpdateBanner() {
        val current = _uiState.value.updateStatus
        if (current is UpdateStatus.Available) {
            prefs.saveDismissedUpdateVersionCode(current.info.versionCode)
        }
        _uiState.update { it.copy(updateBannerVisible = false) }
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

    fun toggleZoom() {
        _uiState.update { state ->
            val next = if (state.previewZoom > 1.05f) 1f else 2f
            state.copy(previewZoom = next)
        }
    }

    fun updateZoom(scale: Float) {
        _uiState.update { it.copy(previewZoom = scale.coerceIn(1f, 3f)) }
    }

    fun applyPreset(preset: EnhancementPreset) {
        updateOptions { it.withPreset(preset) }
    }

    fun resetEnhancementDefaults() {
        updateOptions { it.withPreset(EnhancementPreset.NATURAL) }
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

    fun saveSnapshot(bitmap: Bitmap) {
        val app = getApplication<Application>()
        val sourceName = _uiState.value.activePreviewMetadata?.name
            ?.substringBeforeLast('.')
            ?.replace(Regex("[^A-Za-z0-9\\u0600-\\u06FF_-]+"), "_")
            ?.take(24)
            ?.ifBlank { "Frame" } ?: "Frame"
        val fileName = "UPSCALE_${sourceName}_${System.currentTimeMillis() % 100_000}.png"

        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    ExportRepository.saveSnapshotBitmap(app, bitmap, fileName)
                } finally {
                    bitmap.recycle()
                }
            }
            _uiState.update {
                it.copy(snapshotStatus = if (saved.isSuccess) SnapshotStatus.SAVED else SnapshotStatus.FAILED)
            }
            delay(2_600L)
            _uiState.update { it.copy(snapshotStatus = null) }
        }
    }

    fun reportSnapshotFailed() {
        viewModelScope.launch {
            _uiState.update { it.copy(snapshotStatus = SnapshotStatus.FAILED) }
            delay(2_400L)
            _uiState.update { it.copy(snapshotStatus = null) }
        }
    }

    fun importVideo(uri: Uri) {
        if (_uiState.value.isLocked) return
        val app = getApplication<Application>()
        _uiState.update { it.copy(isImporting = true, error = null) }

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
                        previewZoom = 1f,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        error = AppError.UnableToReadVideo(error.message),
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
                        error = null,
                    )
                }
                ExportRepository.setState(ExportState.Preparing)
                ExportForegroundService.start(app, source, state.options, plan)
            }.onFailure { error ->
                val appError = (error as? LocalizedExportException)?.appError
                    ?: AppError.UnableToPrepareExport(error.message)
                _uiState.update {
                    it.copy(
                        capabilities = caps,
                        error = appError,
                    )
                }
            }
        }
    }

    @androidx.media3.common.util.UnstableApi
    fun cancelExport() {
        if (_uiState.value.exportState is ExportState.Preparing) {
            ExportForegroundService.cancelPendingIfPreparing()
        } else {
            ExportRepository.cancelActiveExport()
        }
    }

    fun shareCompletedOutput() {
        val completed = _uiState.value.completedOutput ?: return
        shareVideoUri(completed.outputUriString, completed.outputName)
    }

    fun shareVideoUri(uriString: String, name: String) {
        val app = getApplication<Application>()
        val uri = Uri.parse(uriString)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(app.contentResolver, name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(share, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(chooser)
    }

    fun openCompletedOutput() {
        val completed = _uiState.value.completedOutput ?: return
        openVideoUri(completed.outputUriString)
    }

    fun openVideoUri(uriString: String) {
        val app = getApplication<Application>()
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriString), "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { app.startActivity(viewIntent) }
            .onFailure {
                _uiState.update { it.copy(error = AppError.NoVideoPlayerFound) }
            }
    }

    private fun syncShaderParams(
        options: ExportOptions,
        splitEnabled: Boolean,
        splitPos: Float,
        showingExportedResult: Boolean,
    ) {
        liveShaderParams.set(
            LiveShaderParams(
                deblock = options.deblockStrength,
                denoise = options.denoiseStrength,
                detail = options.detailStrength,
                clarity = options.clarityStrength,
                vibrance = options.vibranceStrength,
                splitX = if (splitEnabled && !showingExportedResult) splitPos else -1f,
                showOriginalOnly = showingExportedResult,
            ),
        )
    }
}
