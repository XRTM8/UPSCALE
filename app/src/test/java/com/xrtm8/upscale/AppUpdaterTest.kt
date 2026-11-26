package com.xrtm8.upscale

import com.xrtm8.upscale.engine.AppUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdaterTest {

    private fun sampleReleaseJson(tag: String = "v0.8.0") = """
        {
          "tag_name": "$tag",
          "body": "### Release notes\n- Smoother preview\n- Deblock & vibrance",
          "assets": [
            {
              "name": "UPSCALE-Studio-0.8.0-android-arm64-v8a.apk",
              "size": 4500000,
              "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "browser_download_url": "https://github.com/XRTM8/UPSCALE/releases/download/$tag/UPSCALE-Studio-0.8.0-android-arm64-v8a.apk"
            },
            {
              "name": "UPSCALE-Studio-0.8.0-android-universal.apk",
              "size": 4900000,
              "digest": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
              "browser_download_url": "https://github.com/XRTM8/UPSCALE/releases/download/$tag/UPSCALE-Studio-0.8.0-android-universal.apk"
            },
            {
              "name": "SHA256SUMS.txt",
              "size": 256,
              "browser_download_url": "https://github.com/XRTM8/UPSCALE/releases/download/$tag/SHA256SUMS.txt"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesSemanticVersionsIntoMonotonicVersionCode() {
        assertEquals(5000L, AppUpdater.parseVersionCode("0.5.0"))
        assertEquals(5000L, AppUpdater.parseVersionCode("v0.5.0"))
        assertEquals(1002003L, AppUpdater.parseVersionCode("1.2.3"))
        assertNull(AppUpdater.parseVersionCode("invalid"))
    }

    @Test
    fun selectsArm64ApkOnArm64DevicesAndUniversalOtherwise() {
        val arm64 = AppUpdater.selectBestAsset(sampleReleaseJson(), listOf("arm64-v8a", "armeabi-v7a"), 7000L)
        assertNotNull(arm64)
        assertEquals("UPSCALE-Studio-0.8.0-android-arm64-v8a.apk", arm64!!.assetName)
        assertEquals(8000L, arm64.versionCode)
        assertEquals(
            "https://github.com/XRTM8/UPSCALE/releases/download/v0.8.0/SHA256SUMS.txt",
            arm64.checksumUrl,
        )
        assertEquals("### Release notes\n- Smoother preview\n- Deblock & vibrance", arm64.releaseNotes)

        val x86 = AppUpdater.selectBestAsset(sampleReleaseJson(), listOf("x86_64", "x86"), 7000L)
        assertNotNull(x86)
        assertEquals("UPSCALE-Studio-0.8.0-android-universal.apk", x86!!.assetName)
    }

    @Test
    fun parsesSha256FromSumsFile() {
        val sums = """
            aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  UPSCALE-Studio-0.8.0-android-arm64-v8a.apk
            bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb *UPSCALE-Studio-0.8.0-android-universal.apk
        """.trimIndent()
        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            AppUpdater.parseSha256FromSums(sums, "UPSCALE-Studio-0.8.0-android-arm64-v8a.apk"),
        )
        assertEquals(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            AppUpdater.parseSha256FromSums(sums, "UPSCALE-Studio-0.8.0-android-universal.apk"),
        )
        assertNull(AppUpdater.parseSha256FromSums(sums, "other.apk"))
    }

    @Test
    fun ignoresSameOrOlderVersions() {
        assertNull(AppUpdater.selectBestAsset(sampleReleaseJson("v0.7.0"), listOf("arm64-v8a"), 7000L))
        assertNull(AppUpdater.selectBestAsset(sampleReleaseJson("v0.4.0"), listOf("arm64-v8a"), 7000L))
    }

    @Test
    fun rejectsUntrustedDownloadOrigin() {
        val tampered = """
            {
              "tag_name": "v0.9.0",
              "assets": [
                {
                  "name": "UPSCALE-Studio-0.9.0-android-arm64-v8a.apk",
                  "size": 1000,
                  "browser_download_url": "https://evil.example.com/fake.apk"
                }
              ]
            }
        """.trimIndent()
        assertNull(AppUpdater.selectBestAsset(tampered, listOf("arm64-v8a"), 5000L))
    }
}
