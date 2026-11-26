package com.xrtm8.upscale

import com.xrtm8.upscale.engine.AppUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdaterTest {

    private fun sampleReleaseJson(tag: String = "v0.6.0") = """
        {
          "tag_name": "$tag",
          "assets": [
            {
              "name": "UPSCALE-Studio-0.6.0-android-arm64-v8a.apk",
              "size": 4500000,
              "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "browser_download_url": "https://github.com/XRTM8/UPSCALE/releases/download/$tag/UPSCALE-Studio-0.6.0-android-arm64-v8a.apk"
            },
            {
              "name": "UPSCALE-Studio-0.6.0-android-universal.apk",
              "size": 4900000,
              "digest": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
              "browser_download_url": "https://github.com/XRTM8/UPSCALE/releases/download/$tag/UPSCALE-Studio-0.6.0-android-universal.apk"
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
        val arm64 = AppUpdater.selectBestAsset(sampleReleaseJson(), listOf("arm64-v8a", "armeabi-v7a"), 5000L)
        assertNotNull(arm64)
        assertEquals("UPSCALE-Studio-0.6.0-android-arm64-v8a.apk", arm64!!.assetName)
        assertEquals(6000L, arm64.versionCode)

        val x86 = AppUpdater.selectBestAsset(sampleReleaseJson(), listOf("x86_64", "x86"), 5000L)
        assertNotNull(x86)
        assertEquals("UPSCALE-Studio-0.6.0-android-universal.apk", x86!!.assetName)
    }

    @Test
    fun ignoresSameOrOlderVersions() {
        assertNull(AppUpdater.selectBestAsset(sampleReleaseJson("v0.5.0"), listOf("arm64-v8a"), 5000L))
        assertNull(AppUpdater.selectBestAsset(sampleReleaseJson("v0.4.0"), listOf("arm64-v8a"), 5000L))
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
