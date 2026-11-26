package com.xrtm8.upscale.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.Effect
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.Crop
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.Presentation
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlan
import com.xrtm8.upscale.model.FramingMode
import java.util.concurrent.atomic.AtomicReference

data class LiveShaderParams(
    val denoise: Float = 0.45f,
    val detail: Float = 0.65f,
    val clarity: Float = 0.25f,
    /**
     * -1f = apply enhancement across the entire frame (export or full enhanced preview).
     * 0f..1f = live split-screen divider X (left of divider = original, right = enhanced).
     */
    val splitX: Float = -1f,
    val showOriginalOnly: Boolean = false,
)

@UnstableApi
class UpscaleGlEffect(
    private val paramsProvider: () -> LiveShaderParams,
    private val isLivePreview: Boolean,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return UpscaleShaderProgram(useHdr, paramsProvider)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
        if (isLivePreview) return false
        val params = paramsProvider()
        return params.showOriginalOnly ||
            (params.denoise <= 0.001f && params.detail <= 0.001f && params.clarity <= 0.001f)
    }
}

@UnstableApi
private class UpscaleShaderProgram(
    useHdr: Boolean,
    private val paramsProvider: () -> LiveShaderParams,
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity = */ 1) {

    private val glProgram: GlProgram
    private var texelWidth = 1f / 1080f
    private var texelHeight = 1f / 1920f

    init {
        try {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
        } catch (error: Exception) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texelWidth = 1f / inputWidth.coerceAtLeast(1).toFloat()
        texelHeight = 1f / inputHeight.coerceAtLeast(1).toFloat()
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val params = paramsProvider()
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatsUniform("uTexelSize", floatArrayOf(texelWidth, texelHeight))
            glProgram.setFloatUniform("uDenoise", if (params.showOriginalOnly) 0f else params.denoise)
            glProgram.setFloatUniform("uDetail", if (params.showOriginalOnly) 0f else params.detail)
            glProgram.setFloatUniform("uClarity", if (params.showOriginalOnly) 0f else params.clarity)
            glProgram.setFloatUniform("uSplitX", if (params.showOriginalOnly) 2f else params.splitX)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (error: Exception) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun release() {
        super.release()
        glProgram.delete()
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexelSize;
            uniform float uDenoise;
            uniform float uDetail;
            uniform float uClarity;
            uniform float uSplitX;
            varying vec2 vTexSamplingCoord;

            float luma(vec3 c) {
                return dot(c, vec3(0.2126, 0.7152, 0.0722));
            }

            void main() {
                vec4 centerSample = texture2D(uTexSampler, vTexSamplingCoord);
                vec3 center = centerSample.rgb;

                // Split-screen comparison: left side keeps untouched source.
                if (uSplitX >= 0.0 && vTexSamplingCoord.x < uSplitX) {
                    gl_FragColor = centerSample;
                    return;
                }

                vec2 dx = vec2(uTexelSize.x, 0.0);
                vec2 dy = vec2(0.0, uTexelSize.y);

                vec3 n = texture2D(uTexSampler, vTexSamplingCoord - dy).rgb;
                vec3 s = texture2D(uTexSampler, vTexSamplingCoord + dy).rgb;
                vec3 w = texture2D(uTexSampler, vTexSamplingCoord - dx).rgb;
                vec3 e = texture2D(uTexSampler, vTexSamplingCoord + dx).rgb;

                // 1) Edge-aware bilateral denoise (preserves edges & facial detail).
                vec3 denoised = center;
                if (uDenoise > 0.001) {
                    float cLuma = luma(center);
                    float sigma = mix(0.14, 0.035, uDenoise);
                    float wN = exp(-abs(luma(n) - cLuma) / sigma);
                    float wS = exp(-abs(luma(s) - cLuma) / sigma);
                    float wW = exp(-abs(luma(w) - cLuma) / sigma);
                    float wE = exp(-abs(luma(e) - cLuma) / sigma);
                    float wSum = 1.0 + wN + wS + wW + wE;
                    vec3 smoothed = (center + n * wN + s * wS + w * wW + e * wE) / wSum;
                    denoised = mix(center, smoothed, uDenoise * 0.78);
                }

                // 2) Contrast Adaptive Luma Sharpening (clamped to prevent halos).
                vec3 sharpened = denoised;
                if (uDetail > 0.001) {
                    float lC = luma(denoised);
                    float lN = luma(n);
                    float lS = luma(s);
                    float lW = luma(w);
                    float lE = luma(e);

                    float minL = min(lC, min(min(lN, lS), min(lW, lE)));
                    float maxL = max(lC, max(max(lN, lS), max(lW, lE)));
                    float localContrast = max(maxL - minL, 0.0001);
                    float avgNeighbors = 0.25 * (lN + lS + lW + lE);
                    float highPass = lC - avgNeighbors;

                    // Stronger sharpening on medium textures, damped on flat noise & harsh edges.
                    float adaptiveGain = uDetail * 1.15 * clamp(1.0 - localContrast * 1.25, 0.2, 1.0);
                    float deltaL = clamp(highPass * adaptiveGain, -0.14, 0.14);
                    sharpened = clamp(denoised + vec3(deltaL), 0.0, 1.0);
                }

                // 3) Subtle mid-tone clarity curve without clipping highlights.
                vec3 finalRgb = sharpened;
                if (uClarity > 0.001) {
                    vec3 sCurve = sharpened * sharpened * (3.0 - 2.0 * sharpened);
                    finalRgb = mix(sharpened, sCurve, uClarity * 0.36);
                }

                gl_FragColor = vec4(clamp(finalRgb, 0.0, 1.0), centerSample.a);
            }
        """
    }
}

@UnstableApi
object VideoEffectsFactory {

    fun buildExportEffects(plan: ExportPlan, options: ExportOptions): List<Effect> {
        val effects = mutableListOf<Effect>()
        plan.cropRect?.let { rect ->
            effects += Crop(rect.left, rect.right, rect.bottom, rect.top)
        }
        val params = LiveShaderParams(
            denoise = options.denoiseStrength,
            detail = options.detailStrength,
            clarity = options.clarityStrength,
            splitX = -1f,
            showOriginalOnly = false,
        )
        effects += UpscaleGlEffect(paramsProvider = { params }, isLivePreview = false)
        val layout = if (options.framing == FramingMode.COVER) {
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        } else {
            Presentation.LAYOUT_SCALE_TO_FIT
        }
        effects += Presentation.createForWidthAndHeight(plan.targetWidth, plan.targetHeight, layout)
        return effects
    }

    fun buildPreviewEffects(
        paramsRef: AtomicReference<LiveShaderParams>,
        cropRect: com.xrtm8.upscale.model.NormalizedCropRect?,
    ): List<Effect> {
        val effects = mutableListOf<Effect>()
        cropRect?.let { rect ->
            effects += Crop(rect.left, rect.right, rect.bottom, rect.top)
        }
        effects += UpscaleGlEffect(paramsProvider = { paramsRef.get() }, isLivePreview = true)
        return effects
    }
}
