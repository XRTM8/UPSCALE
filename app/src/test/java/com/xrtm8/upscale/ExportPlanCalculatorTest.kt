package com.xrtm8.upscale

import com.xrtm8.upscale.model.DeviceCapabilities
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlanCalculator
import com.xrtm8.upscale.model.FramingMode
import com.xrtm8.upscale.model.OutputResolution
import com.xrtm8.upscale.model.PlatformPreset
import com.xrtm8.upscale.model.PostFormat
import com.xrtm8.upscale.model.QualityMode
import com.xrtm8.upscale.model.VideoCodecChoice
import com.xrtm8.upscale.model.VideoMetadata
import org.junit.Assert.assertEquals
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
    fun rejectsExportWhenStorageIsTooLow() {
        val lowStorage = capableDevice().copy(freeStorageBytes = 5L * 1024L * 1024L)
        val result = ExportPlanCalculator.buildPlan(sampleVideo(), ExportOptions(), lowStorage)
        assertTrue(result.isFailure)
    }

    @Test
    fun estimatesRemainingSecondsOnlyAfterProgressStarts() {
        val early = com.xrtm8.upscale.model.ExportState.Running("job", 0.02f, 200L, 10_000L, "out.mp4", 1_000L)
        assertNull(early.estimatedRemainingSeconds(2_000L))

        val mid = com.xrtm8.upscale.model.ExportState.Running("job", 0.5f, 5_000L, 10_000L, "out.mp4", 1_000L)
        assertEquals(10L, mid.estimatedRemainingSeconds(11_000L))
    }
}
