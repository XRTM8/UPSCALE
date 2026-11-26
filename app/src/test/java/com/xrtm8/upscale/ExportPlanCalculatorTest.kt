package com.xrtm8.upscale

import com.xrtm8.upscale.engine.ExportRepository
import com.xrtm8.upscale.model.AudioQuality
import com.xrtm8.upscale.model.DeviceCapabilities
import com.xrtm8.upscale.model.EnhancementPreset
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlanCalculator
import com.xrtm8.upscale.model.FramingMode
import com.xrtm8.upscale.model.OutputResolution
import com.xrtm8.upscale.model.PlatformPreset
import com.xrtm8.upscale.model.PostFormat
import com.xrtm8.upscale.model.QualityMode
import com.xrtm8.upscale.model.TargetFps
import com.xrtm8.upscale.model.VideoCodecChoice
import com.xrtm8.upscale.model.VideoMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPlanCalculatorTest {

    private fun sampleVideo(
        width: Int = 1920,
        height: Int = 1080,
        durationMs: Long = 12_000L,
        isHdr: Boolean = false,
    ) = VideoMetadata(
        uriString = "content://media/external/video/media/1",
        name = "clip.mp4",
        width = width,
        height = height,
        displayWidth = width,
        displayHeight = height,
        durationMs = durationMs,
        frameRate = 30.0,
        mimeType = "video/avc",
        hasAudio = true,
        isHdr = isHdr,
        sizeBytes = 25_000_000L,
    )

    private fun capableDevice() = DeviceCapabilities(
        supports1080pH264 = true,
        supports1080pHevc = true,
        supports4kH264 = true,
        supports4kHevc = true,
        thermalStatus = 0,
        freeStorageBytes = 4L * 1024L * 1024L * 1024L,
    )

    @Test
    fun instagramFeedUses4x5AndCaps4kTo1080p() {
        val options = ExportOptions(
            platform = PlatformPreset.INSTAGRAM,
            postFormat = PostFormat.FEED,
            resolution = OutputResolution.UHD_4K,
        )
        val plan = ExportPlanCalculator.buildPlan(sampleVideo(), options, capableDevice()).getOrThrow()
        assertEquals(1080, plan.targetWidth)
        assertEquals(1350, plan.targetHeight)
    }

    @Test
    fun youtubeSupports4kAndNormalizesStoryToReel() {
        val options = ExportOptions(
            platform = PlatformPreset.YOUTUBE,
            postFormat = PostFormat.STORY,
            resolution = OutputResolution.UHD_4K,
            codec = VideoCodecChoice.HEVC,
            quality = QualityMode.MAXIMUM,
        )
        val plan = ExportPlanCalculator.buildPlan(sampleVideo(), options, capableDevice()).getOrThrow()
        assertEquals(2160, plan.targetWidth)
        assertEquals(3840, plan.targetHeight)
        assertTrue(plan.useHevc)
    }

    @Test
    fun supports1080pHevcAllowsHevcAt1080pWithout4k() {
        val midRangeDevice = capableDevice().copy(
            supports1080pHevc = true,
            supports4kH264 = false,
            supports4kHevc = false,
        )
        val options = ExportOptions(
            platform = PlatformPreset.INSTAGRAM,
            resolution = OutputResolution.FHD_1080P,
            codec = VideoCodecChoice.HEVC,
        )
        val plan = ExportPlanCalculator.buildPlan(sampleVideo(), options, midRangeDevice).getOrThrow()
        assertTrue(plan.useHevc)
        assertEquals(1080, plan.targetWidth)
    }

    @Test
    fun hdrVideoEnablesToneMappingInsteadOfFailing() {
        val plan = ExportPlanCalculator.buildPlan(
            source = sampleVideo(isHdr = true),
            rawOptions = ExportOptions(),
            capabilities = capableDevice(),
        ).getOrThrow()
        assertTrue(plan.toneMapHdrToSdr)
    }

    @Test
    fun coverCropPansWithinNormalizedBounds() {
        val center = ExportPlanCalculator.computeCoverCrop(1920, 1080, 1080, 1920, 0f, 0f)
        val leftEdge = ExportPlanCalculator.computeCoverCrop(1920, 1080, 1080, 1920, -1f, 0f)
        val rightEdge = ExportPlanCalculator.computeCoverCrop(1920, 1080, 1080, 1920, 1f, 0f)
        assertNotNull(center)
        assertNotNull(leftEdge)
        assertNotNull(rightEdge)
        assertEquals(-1f, leftEdge!!.left, 0.001f)
        assertEquals(1f, rightEdge!!.right, 0.001f)
        assertNull(ExportPlanCalculator.computeCoverCrop(1080, 1920, 1080, 1920, 0f, 0f))
    }

    @Test
    fun containFramingLeavesCropNullAndTrimsDuration() {
        val options = ExportOptions(
            framing = FramingMode.CONTAIN,
            trimStartMs = 2_000L,
            trimEndMs = 7_000L,
        )
        val plan = ExportPlanCalculator.buildPlan(sampleVideo(), options, capableDevice()).getOrThrow()
        assertNull(plan.cropRect)
        assertEquals(5_000L, plan.effectiveDurationMs)
    }

    @Test
    fun muteAudioReducesEstimatedSizeAndSetsPlanFlag() {
        val withAudio = ExportPlanCalculator.buildPlan(
            sampleVideo(),
            ExportOptions(muteAudio = false),
            capableDevice(),
        ).getOrThrow()
        val muted = ExportPlanCalculator.buildPlan(
            sampleVideo(),
            ExportOptions(muteAudio = true),
            capableDevice(),
        ).getOrThrow()
        assertFalse(withAudio.muteAudio)
        assertTrue(muted.muteAudio)
        assertTrue(muted.estimatedBytes < withAudio.estimatedBytes)
    }

    @Test
    fun enhancementPresetsConfigureDistinctFilterProfiles() {
        val lowLight = ExportOptions().withPreset(EnhancementPreset.LOW_LIGHT)
        assertEquals(EnhancementPreset.LOW_LIGHT, lowLight.preset)
        assertEquals(0.75f, lowLight.denoiseStrength, 0.001f)

        val crisp = ExportOptions().withPreset(EnhancementPreset.CRISP_TEXT)
        assertEquals(0.85f, crisp.detailStrength, 0.001f)
    }

    @Test
    fun smartOutputNameSanitizesFilenameAndIncludesPreset() {
        val name = ExportPlanCalculator.buildSmartOutputName(
            sourceName = "My Clip!! (2026).mp4",
            options = ExportOptions(
                platform = PlatformPreset.TIKTOK,
                postFormat = PostFormat.REEL,
                resolution = OutputResolution.FHD_1080P,
            ),
        )
        assertTrue(name.startsWith("UPSCALE_My_Clip_2026_TT_Reel_1080p_"))
        assertTrue(name.endsWith(".mp4"))
    }

    @Test
    fun exportRepositoryAllowsMacroblockAlignmentWithin16Px() {
        assertTrue(ExportRepository.isVerifiedDimensions(1080, 1344, 1080, 1350))
        assertTrue(ExportRepository.isVerifiedDimensions(1080, 1350, 1080, 1350))
        assertFalse(ExportRepository.isVerifiedDimensions(720, 1280, 1080, 1920))
    }

    @Test
    fun rejectsExportWhenStorageIsTooLow() {
        val lowStorage = capableDevice().copy(freeStorageBytes = 5L * 1024L * 1024L)
        val result = ExportPlanCalculator.buildPlan(sampleVideo(), ExportOptions(), lowStorage)
        assertTrue(result.isFailure)
    }

    @Test
    fun targetFpsCapsOnlyWhenSourceExceedsRequestedFps() {
        val sixtyFpsVideo = sampleVideo().copy(frameRate = 60.0)
        val cap30Plan = ExportPlanCalculator.buildPlan(
            sixtyFpsVideo,
            ExportOptions(targetFps = TargetFps.FPS_30),
            capableDevice(),
        ).getOrThrow()
        assertEquals(30, cap30Plan.targetFps)

        val thirtyFpsVideo = sampleVideo().copy(frameRate = 30.0)
        val cap60Plan = ExportPlanCalculator.buildPlan(
            thirtyFpsVideo,
            ExportOptions(targetFps = TargetFps.FPS_60),
            capableDevice(),
        ).getOrThrow()
        assertNull(cap60Plan.targetFps)
    }

    @Test
    fun exposureAndWarmthClampWithinBipolarBounds() {
        val normalized = ExportOptions(exposureStrength = 1.5f, warmthStrength = -2f)
            .normalizedForPlatform(capableDevice())
        assertEquals(1f, normalized.exposureStrength, 0.001f)
        assertEquals(-1f, normalized.warmthStrength, 0.001f)
    }

    @Test
    fun estimatesRemainingSecondsOnlyAfterProgressStarts() {
        val early = com.xrtm8.upscale.model.ExportState.Running("job", 0.02f, 200L, 10_000L, "out.mp4", 1_000L)
        assertNull(early.estimatedRemainingSeconds(2_000L))

        val mid = com.xrtm8.upscale.model.ExportState.Running("job", 0.5f, 5_000L, 10_000L, "out.mp4", 1_000L)
        assertEquals(10L, mid.estimatedRemainingSeconds(11_000L))
    }

    @Test
    fun compactSourceVideoDoesNotBloatOutputSize() {
        // 6 MB over 16s (~3 Mbps) at 1080p should stay near source size instead of bloating to 36 MB.
        val sixMbClip = sampleVideo(
            width = 1080,
            height = 1920,
            durationMs = 16_000L,
        ).copy(sizeBytes = 6L * 1024L * 1024L)

        val maxPlan = ExportPlanCalculator.buildPlan(
            sixMbClip,
            ExportOptions(quality = QualityMode.MAXIMUM, resolution = OutputResolution.FHD_1080P),
            capableDevice(),
        ).getOrThrow()
        assertTrue("Expected < 10 MB, got ${maxPlan.estimatedBytes}", maxPlan.estimatedBytes < 10L * 1024L * 1024L)

        val balancedPlan = ExportPlanCalculator.buildPlan(
            sixMbClip,
            ExportOptions(quality = QualityMode.BALANCED, resolution = OutputResolution.FHD_1080P),
            capableDevice(),
        ).getOrThrow()
        assertTrue(balancedPlan.estimatedBytes <= maxPlan.estimatedBytes)
        assertTrue("Expected < 8 MB, got ${balancedPlan.estimatedBytes}", balancedPlan.estimatedBytes < 8L * 1024L * 1024L)
    }

    @Test
    fun upscalingLowResSourceAllowsModerateBitrateHeadroomWithoutBloat() {
        val sixMb720p = sampleVideo(
            width = 720,
            height = 1280,
            durationMs = 16_000L,
        ).copy(sizeBytes = 6L * 1024L * 1024L)

        val plan = ExportPlanCalculator.buildPlan(
            sixMb720p,
            ExportOptions(quality = QualityMode.MAXIMUM, resolution = OutputResolution.FHD_1080P),
            capableDevice(),
        ).getOrThrow()
        assertTrue(plan.bitrateBps in 3_500_000..6_500_000)
        assertTrue(plan.estimatedBytes < 14L * 1024L * 1024L)
    }

    @Test
    fun highBitrateSourceIsCappedAtResolutionCeiling() {
        val highBitrate4k = sampleVideo(
            width = 2160,
            height = 3840,
            durationMs = 10_000L,
        ).copy(sizeBytes = 80L * 1024L * 1024L)

        val plan1080 = ExportPlanCalculator.buildPlan(
            highBitrate4k,
            ExportOptions(quality = QualityMode.MAXIMUM, resolution = OutputResolution.FHD_1080P),
            capableDevice(),
        ).getOrThrow()
        assertEquals(10_000_000, plan1080.bitrateBps)
    }

    @Test
    fun originalPostFormatAndSourceResolutionPreserveSourceDimensions() {
        val wide720p = sampleVideo(width = 1280, height = 720, durationMs = 10_000L)
        val plan = ExportPlanCalculator.buildPlan(
            wide720p,
            ExportOptions(
                postFormat = PostFormat.ORIGINAL,
                resolution = OutputResolution.SOURCE,
            ),
            capableDevice(),
        ).getOrThrow()
        assertEquals(1280, plan.targetWidth)
        assertEquals(720, plan.targetHeight)
    }

    @Test
    fun compactQualityReducesBitrateBelowSource() {
        val sixMbClip = sampleVideo(width = 1080, height = 1920, durationMs = 16_000L)
            .copy(sizeBytes = 6L * 1024L * 1024L)
        val compactPlan = ExportPlanCalculator.buildPlan(
            sixMbClip,
            ExportOptions(quality = QualityMode.COMPACT, resolution = OutputResolution.FHD_1080P),
            capableDevice(),
        ).getOrThrow()
        val balancedPlan = ExportPlanCalculator.buildPlan(
            sixMbClip,
            ExportOptions(quality = QualityMode.BALANCED, resolution = OutputResolution.FHD_1080P),
            capableDevice(),
        ).getOrThrow()
        assertTrue(compactPlan.bitrateBps < balancedPlan.bitrateBps)
        assertTrue(compactPlan.estimatedBytes < 6L * 1024L * 1024L)
    }

    @Test
    fun audioQualityAndKeepHdrAreReflectedInExportPlan() {
        val hdrVideo = sampleVideo(isHdr = true)
        val plan = ExportPlanCalculator.buildPlan(
            hdrVideo,
            ExportOptions(audioQuality = AudioQuality.AAC_320, keepHdr = true),
            capableDevice(),
        ).getOrThrow()
        assertEquals(320_000, plan.audioBitrateBps)
        assertFalse(plan.toneMapHdrToSdr)
    }
}
