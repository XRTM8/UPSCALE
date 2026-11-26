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
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.Presentation
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlan
import com.xrtm8.upscale.model.FramingMode
import com.xrtm8.upscale.model.NormalizedCropRect
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

data class LiveShaderParams(
    val deblock: Float = 0.25f,
    val denoise: Float = 0.45f,
    val detail: Float = 0.65f,
    val clarity: Float = 0.25f,
    val vibrance: Float = 0.10f,
    val exposure: Float = 0f,
    val warmth: Float = 0f,
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
        return params.showOriginalOnly || (
            params.deblock <= 0.001f &&
                params.denoise <= 0.001f &&
                params.detail <= 0.001f &&
                params.clarity <= 0.001f &&
                params.vibrance <= 0.001f &&
                abs(params.exposure) <= 0.001f &&
                abs(params.warmth) <= 0.001f
            )
    }
}

@UnstableApi
private class UpscaleShaderProgram(
    private val useHdr: Boolean,
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
            glProgram.setFloatUniform("uDeblock", if (params.showOriginalOnly) 0f else params.deblock)
            glProgram.setFloatUniform("uDenoise", if (params.showOriginalOnly) 0f else params.denoise)
            glProgram.setFloatUniform("uDetail", if (params.showOriginalOnly) 0f else params.detail)
            glProgram.setFloatUniform("uClarity", if (params.showOriginalOnly) 0f else params.clarity)
            glProgram.setFloatUniform("uVibrance", if (params.showOriginalOnly) 0f else params.vibrance)
            glProgram.setFloatUniform("uExposure", if (params.showOriginalOnly) 0f else params.exposure)
            glProgram.setFloatUniform("uWarmth", if (params.showOriginalOnly) 0f else params.warmth)
            glProgram.setFloatUniform("uSplitX", if (params.showOriginalOnly) 2f else params.splitX)
            glProgram.setFloatUniform("uHdrMode", if (useHdr) 1f else 0f)
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
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTexSampler;
            uniform vec2 uTexelSize;
            uniform float uDeblock;
            uniform float uDenoise;
            uniform float uDetail;
            uniform float uClarity;
            uniform float uVibrance;
            uniform float uExposure;
            uniform float uWarmth;
            uniform float uSplitX;
            uniform float uHdrMode;
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

                // 0) Mild compression deblocking (targets flat/gradient macroblock edges).
                vec3 base = center;
                if (uDeblock > 0.001) {
                    vec3 nw = texture2D(uTexSampler, vTexSamplingCoord - dx - dy).rgb;
                    vec3 ne = texture2D(uTexSampler, vTexSamplingCoord + dx - dy).rgb;
                    vec3 sw = texture2D(uTexSampler, vTexSamplingCoord - dx + dy).rgb;
                    vec3 se = texture2D(uTexSampler, vTexSamplingCoord + dx + dy).rgb;
                    vec3 ringAvg = (n + s + w + e) * 0.15 + (nw + ne + sw + se) * 0.10;
                    float diff = abs(luma(ringAvg) - luma(center));
                    float flatMask = smoothstep(0.08, 0.008, diff);
                    base = mix(center, ringAvg, uDeblock * 0.52 * flatMask);
                }

                // 1) Edge-aware bilateral denoise (preserves edges & facial detail).
                vec3 denoised = base;
                if (uDenoise > 0.001) {
                    float cLuma = luma(base);
                    float sigma = mix(0.14, 0.035, uDenoise);
                    float wN = exp(-abs(luma(n) - cLuma) / sigma);
                    float wS = exp(-abs(luma(s) - cLuma) / sigma);
                    float wW = exp(-abs(luma(w) - cLuma) / sigma);
                    float wE = exp(-abs(luma(e) - cLuma) / sigma);
                    float wSum = 1.0 + wN + wS + wW + wE;
                    vec3 smoothed = (base + n * wN + s * wS + w * wW + e * wE) / wSum;
                    denoised = mix(base, smoothed, uDenoise * 0.75);
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

                    float adaptiveGain = uDetail * 1.15 * clamp(1.0 - localContrast * 1.25, 0.2, 1.0);
                    float deltaL = clamp(highPass * adaptiveGain, -0.14, 0.14);
                    sharpened = max(denoised + vec3(deltaL), 0.0);
                    if (uHdrMode < 0.5) {
                        sharpened = min(sharpened, 1.0);
                    }
                }

                // 3) Exposure & white-balance warmth.
                vec3 toned = sharpened;
                if (abs(uExposure) > 0.001) {
                    toned = toned * exp2(uExposure * 0.55);
                }
                if (abs(uWarmth) > 0.001) {
                    float lTone = clamp(luma(toned), 0.0, 1.0);
                    float weight = 0.35 + 0.65 * lTone;
                    toned += vec3(uWarmth * 0.055, 0.0, -uWarmth * 0.055) * weight;
                }
                toned = max(toned, 0.0);
                if (uHdrMode < 0.5) {
                    toned = min(toned, 1.0);
                }

                // 4) Subtle mid-tone clarity curve without clipping highlights.
                vec3 clarified = toned;
                if (uClarity > 0.001) {
                    vec3 unit = clamp(toned, 0.0, 1.0);
                    vec3 sCurve = unit * unit * (3.0 - 2.0 * unit);
                    vec3 target = (uHdrMode > 0.5) ? max(toned, sCurve) : sCurve;
                    clarified = mix(toned, target, uClarity * 0.34);
                }

                // 5) Skin-protective vibrance boost (lifts muted colors, leaves saturated tones safe).
                vec3 finalRgb = clarified;
                if (uVibrance > 0.001) {
                    float l = luma(clarified);
                    float maxCh = max(clarified.r, max(clarified.g, clarified.b));
                    float minCh = min(clarified.r, min(clarified.g, clarified.b));
                    float sat = maxCh - minCh;
                    float vibFactor = uVibrance * 0.55 * (1.0 - clamp(sat * 1.4, 0.0, 1.0));
                    finalRgb = mix(vec3(l), clarified, 1.0 + vibFactor);
                }

                finalRgb = max(finalRgb, 0.0);
                if (uHdrMode < 0.5) {
                    finalRgb = min(finalRgb, 1.0);
                }
                gl_FragColor = vec4(finalRgb, centerSample.a);
            }
        """
    }
}

@UnstableApi
object VideoEffectsFactory {

    fun buildExportEffects(plan: ExportPlan, options: ExportOptions): List<Effect> {
        val effects = mutableListOf<Effect>()
        plan.targetFps?.let { fps ->
            effects += FrameDropEffect.createDefaultFrameDropEffect(fps.toFloat())
        }
        plan.cropRect?.let { rect ->
            effects += Crop(rect.left, rect.right, rect.bottom, rect.top)
        }
        val layout = if (options.framing == FramingMode.COVER) {
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        } else {
            Presentation.LAYOUT_SCALE_TO_FIT
        }
        // Upscale / frame first so deblock + denoise + CAS detail operate at the target resolution.
        effects += Presentation.createForWidthAndHeight(plan.targetWidth, plan.targetHeight, layout)
        val params = LiveShaderParams(
            deblock = options.deblockStrength,
            denoise = options.denoiseStrength,
            detail = options.detailStrength,
            clarity = options.clarityStrength,
            vibrance = options.vibranceStrength,
            exposure = options.exposureStrength,
            warmth = options.warmthStrength,
            splitX = -1f,
            showOriginalOnly = false,
        )
        effects += UpscaleGlEffect(paramsProvider = { params }, isLivePreview = false)
        return effects
    }

    fun buildPreviewEffects(
        paramsRef: AtomicReference<LiveShaderParams>,
        cropRect: NormalizedCropRect?,
    ): List<Effect> {
        val effects = mutableListOf<Effect>()
        cropRect?.let { rect ->
            effects += Crop(rect.left, rect.right, rect.bottom, rect.top)
        }
        effects += UpscaleGlEffect(paramsProvider = { paramsRef.get() }, isLivePreview = true)
        return effects
    }
}
