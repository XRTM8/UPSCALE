package com.xrtm8.upscale.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.xrtm8.upscale.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseUpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val expectedSha256: String?,
    val checksumUrl: String? = null,
    val releaseNotes: String = "",
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Available(val info: ReleaseUpdateInfo) : UpdateStatus
    data class Downloading(val info: ReleaseUpdateInfo, val progress: Float) : UpdateStatus
    data class ReadyToInstall(val info: ReleaseUpdateInfo, val apkFile: File) : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

object AppUpdater {
    const val REPO_PACKAGE_ID = "com.xrtm8.upscale"
    const val PERMANENT_CERT_SHA256 = "39b2da1a2249a87b340b0c94b1d9098829f6f263bb9636f9743cb2c73aa56925"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/XRTM8/UPSCALE/releases/latest"
    private const val TRUSTED_DOWNLOAD_PREFIX = "https://github.com/XRTM8/UPSCALE/releases/download/"

    fun parseVersionCode(versionName: String): Long? {
        val clean = versionName.trim().removePrefix("v").substringBefore('-')
        val parts = clean.split('.')
        if (parts.size != 3) return null
        val major = parts[0].toLongOrNull() ?: return null
        val minor = parts[1].toLongOrNull() ?: return null
        val patch = parts[2].toLongOrNull() ?: return null
        if (major < 0 || minor !in 0..999 || patch !in 0..999) return null
        return major * 1_000_000L + minor * 1_000L + patch
    }

    fun selectBestAsset(
        releaseJson: String,
        supportedAbis: List<String>,
        currentVersionCode: Long,
    ): ReleaseUpdateInfo? {
        val root = JSONObject(releaseJson)
        val tagName = root.optString("tag_name", "").trim()
        val versionName = tagName.removePrefix("v")
        val remoteVersionCode = parseVersionCode(versionName) ?: return null
        if (remoteVersionCode <= currentVersionCode) return null

        val releaseNotes = root.optString("body", "").trim()
        val assets = root.optJSONArray("assets") ?: return null
        val isArm64 = supportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) }

        var arm64Asset: ReleaseUpdateInfo? = null
        var universalAsset: ReleaseUpdateInfo? = null
        var checksumsUrl: String? = null

        for (i in 0 until assets.length()) {
            val item = assets.optJSONObject(i) ?: continue
            val name = item.optString("name", "")
            val url = item.optString("browser_download_url", "")
            if (!url.startsWith(TRUSTED_DOWNLOAD_PREFIX)) continue

            if (name.equals("SHA256SUMS.txt", ignoreCase = true)) {
                checksumsUrl = url
                continue
            }
            if (!name.endsWith(".apk", ignoreCase = true)) continue

            val size = item.optLong("size", 0L)
            val digest = item.optString("digest", "").removePrefix("sha256:").lowercase().takeIf {
                it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' }
            }
            val parsed = ReleaseUpdateInfo(
                versionName = versionName,
                versionCode = remoteVersionCode,
                assetName = name,
                downloadUrl = url,
                sizeBytes = size,
                expectedSha256 = digest,
                releaseNotes = releaseNotes,
            )
            when {
                name.contains("arm64-v8a", ignoreCase = true) -> arm64Asset = parsed
                name.contains("universal", ignoreCase = true) -> universalAsset = parsed
            }
        }

        val chosen = if (isArm64) (arm64Asset ?: universalAsset) else universalAsset
        return chosen?.copy(checksumUrl = checksumsUrl)
    }

    fun parseSha256FromSums(sumsText: String, assetName: String): String? {
        return sumsText.lineSequence()
            .map { it.trim() }
            .firstNotNullOfOrNull { line ->
                if (line.isEmpty()) return@firstNotNullOfOrNull null
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val hash = parts[0].lowercase()
                    val file = parts.last().removePrefix("*")
                    if (file == assetName && hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }) {
                        hash
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
    }

    fun checkLatestRelease(
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    ): Result<ReleaseUpdateInfo?> = runCatching {
        val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "UPSCALE-Studio-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            val status = conn.responseCode
            require(status in 200..299) { "Update check returned HTTP $status" }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            selectBestAsset(body, supportedAbis, currentVersionCode)
        } finally {
            conn.disconnect()
        }
    }

    fun cleanupUpdateCache(context: Context) {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) return
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fetchExpectedSha256Fallback(info: ReleaseUpdateInfo): String? {
        if (info.expectedSha256 != null) return info.expectedSha256
        val url = info.checksumUrl ?: return null
        if (!url.startsWith(TRUSTED_DOWNLOAD_PREFIX)) return null
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "UPSCALE-Studio-Android/${BuildConfig.VERSION_NAME}")
            }
            try {
                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    parseSha256FromSums(text, info.assetName)
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    fun downloadAndVerify(
        context: Context,
        info: ReleaseUpdateInfo,
        onProgress: (Float) -> Unit,
    ): Result<File> = runCatching {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        updatesDir.listFiles()?.forEach { it.delete() }
        val targetFile = File(updatesDir, info.assetName)
        val expectedHash = fetchExpectedSha256Fallback(info)

        val conn = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "UPSCALE-Studio-Android/${BuildConfig.VERSION_NAME}")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val code = conn.responseCode
            require(code in 200..299) { "Download failed (HTTP $code)." }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes.takeIf { it > 0 } ?: -1L
            var downloaded = 0L
            conn.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        if (total > 0L) {
                            onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 0.99f))
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            targetFile.delete()
            throw error
        } finally {
            conn.disconnect()
        }

        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
        if (expectedHash != null) {
            check(actualSha256.equals(expectedHash, ignoreCase = true)) {
                "Downloaded APK checksum mismatch."
            }
        }

        verifyArchiveCompatibility(context, targetFile, info.versionCode).getOrThrow()
        onProgress(1f)
        targetFile
    }

    fun verifyArchiveCompatibility(
        context: Context,
        apkFile: File,
        expectedMinVersionCode: Long,
    ): Result<Unit> = runCatching {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archiveInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } ?: error("Downloaded file is not a valid Android package.")

        check(archiveInfo.packageName == REPO_PACKAGE_ID) {
            "Package ID mismatch (${archiveInfo.packageName})."
        }
        check(context.packageName == REPO_PACKAGE_ID) {
            "Cannot install release update over a debug build (${context.packageName})."
        }
        check(archiveInfo.longVersionCode >= expectedMinVersionCode && archiveInfo.longVersionCode > BuildConfig.VERSION_CODE.toLong()) {
            "Update version (${archiveInfo.longVersionCode}) is not newer than installed (${BuildConfig.VERSION_CODE})."
        }

        val archiveCerts = extractCertSha256Set(archiveInfo)
        check(archiveCerts.size == 1 && archiveCerts.first() == PERMANENT_CERT_SHA256) {
            "APK signing certificate does not match the permanent release identity."
        }

        val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, flags)
        }
        val installedCerts = extractCertSha256Set(installedInfo)
        check(installedCerts == archiveCerts) {
            "Installed app signature differs from update package; cannot update in-place without conflict."
        }
    }

    private fun extractCertSha256Set(packageInfo: PackageInfo): Set<String> {
        val signingInfo = packageInfo.signingInfo ?: return emptySet()
        val signers = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        } ?: return emptySet()

        val md = MessageDigest.getInstance("SHA-256")
        return signers.map { sig ->
            md.reset()
            md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun createUnknownSourcesSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
