package com.nedmah.supertonic_kmp.internal

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.nedmah.supertonic_kmp.api.SupertonicConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.text.Normalizer
import java.util.Random
import kotlin.math.ceil


// latent_dim(24) * chunk_compress_factor(6)
private const val LATENT_CHANNELS = 144
private const val STYLE_TOKENS = 50
private const val STYLE_DIM = 256
private const val STYLE_DP_TOKENS = 8
private const val STYLE_DP_DIM = 16

// ae.base_chunk_size(512) * ttl.chunk_compress_factor(6)
private const val CHUNK_SIZE = 512 * 6
private const val SAMPLE_RATE = 44100

internal actual class InferenceEngine actual constructor(
    private val config: SupertonicConfig
) {

    private val ortEnv = OrtEnvironment.getEnvironment()

    private var dpSession: OrtSession? = null
    private var textEncoderSession: OrtSession? = null
    private var vectorEstimatorSession: OrtSession? = null
    private var vocoderSession: OrtSession? = null
    private var tokenizer: Tokenizer? = null

    @Volatile
    private var cancelled = false

    actual val isLoaded: Boolean
        get() = dpSession != null
                && textEncoderSession != null
                && vectorEstimatorSession != null
                && vocoderSession != null

    actual fun load() {
        val dir = config.storageDir
        close()

        dpSession = ortEnv.createSession("$dir/duration_predictor.onnx")
        textEncoderSession = ortEnv.createSession("$dir/text_encoder.onnx")
        vectorEstimatorSession = ortEnv.createSession("$dir/vector_estimator.onnx")
        vocoderSession = ortEnv.createSession("$dir/vocoder.onnx")

        tokenizer = Tokenizer(File("$dir/unicode_indexer.json").readText())
    }

    actual fun generate(
        text: String,
        lang: String,
        voiceStyleJson: String,
        speed: Float,
        steps: Int,
    ): ByteArray {
        cancelled = false

        val tk = tokenizer ?: return ByteArray(0)
        val dp = dpSession ?: return ByteArray(0)
        val textEncoder = textEncoderSession ?: return ByteArray(0)
        val vectorEstimator = vectorEstimatorSession ?: return ByteArray(0)
        val vocoder = vocoderSession ?: return ByteArray(0)

        return try {
            // Preprocess and tokenize
            val processed = preprocessText(text, lang)   // "<en>Hello!</en>"
            val tokenIds = tk.tokenize(processed)
            if (tokenIds.isEmpty()) return ByteArray(0)
            val textLen = tokenIds.size

            // Parse voice styles
            val styleTtlFlat = parseStyle(voiceStyleJson, "style_ttl", STYLE_TOKENS, STYLE_DIM)
            val styleDpFlat = parseStyle(voiceStyleJson, "style_dp", STYLE_DP_TOKENS, STYLE_DP_DIM)

            // Common tensors
            val textIdsTensor = OnnxTensor.createTensor(
                ortEnv,
                LongBuffer.wrap(LongArray(textLen) { tokenIds[it].toLong() }),
                longArrayOf(1, textLen.toLong()),
            )
            val styleTtlTensor = OnnxTensor.createTensor(
                ortEnv,
                FloatBuffer.wrap(styleTtlFlat),
                longArrayOf(1, STYLE_TOKENS.toLong(), STYLE_DIM.toLong()),
            )
            val styleDpTensor = OnnxTensor.createTensor(
                ortEnv,
                FloatBuffer.wrap(styleDpFlat),
                longArrayOf(1, STYLE_DP_TOKENS.toLong(), STYLE_DP_DIM.toLong()),
            )
            val textMaskTensor = OnnxTensor.createTensor(
                ortEnv,
                FloatBuffer.wrap(FloatArray(textLen) { 1f }),
                longArrayOf(1, 1, textLen.toLong()),
            )

            // Duration predictor -> latent length
            val dpResult = dp.run(
                mapOf(
                    "text_ids" to textIdsTensor,
                    "style_dp" to styleDpTensor,
                    "text_mask" to textMaskTensor,
                )
            )
            val durationTensor = dpResult.get("duration").get() as OnnxTensor
            val durationSec = durationTensor.floatBuffer.get() / speed
            dpResult.close()
            styleDpTensor.close()

            val wavLenMax = durationSec * SAMPLE_RATE
            val latentLen = ceil(wavLenMax / CHUNK_SIZE).toInt().coerceAtLeast(1)

            if (cancelled) {
                textIdsTensor.close(); styleTtlTensor.close(); textMaskTensor.close(); return ByteArray(
                    0
                )
            }

            // Text encoder
            val encoderResult = textEncoder.run(
                mapOf(
                    "text_ids" to textIdsTensor,
                    "style_ttl" to styleTtlTensor,
                    "text_mask" to textMaskTensor,
                )
            )
            textIdsTensor.close()

            val textEmbTensor = encoderResult.get("text_emb").get() as OnnxTensor

            // Latent mask - all ones for single sample
            val latentMaskTensor = OnnxTensor.createTensor(
                ortEnv,
                FloatBuffer.wrap(FloatArray(latentLen) { 1f }),
                longArrayOf(1, 1, latentLen.toLong()),
            )

            // Initial noise * latent mask
            val noiseSize = LATENT_CHANNELS * latentLen
            val rng = Random()
            val noisyLatent = FloatArray(noiseSize) { rng.nextGaussian().toFloat() }

            // Denoising loop
            // The vector_estimator directly outputs the updated latent (not velocity).
            // Each step: xt = vector_estimator(xt, ...)
            val totalStepTensor = OnnxTensor.createTensor(
                ortEnv,
                FloatBuffer.wrap(floatArrayOf(steps.toFloat())),
                longArrayOf(1),
            )

            for (step in 0 until steps) {
                if (cancelled) {
                    textEmbTensor.close(); latentMaskTensor.close()
                    styleTtlTensor.close(); textMaskTensor.close()
                    encoderResult.close(); totalStepTensor.close()
                    return ByteArray(0)
                }

                val noisyLatentTensor = OnnxTensor.createTensor(
                    ortEnv,
                    FloatBuffer.wrap(noisyLatent),
                    longArrayOf(1, LATENT_CHANNELS.toLong(), latentLen.toLong()),
                )
                val currentStepTensor = OnnxTensor.createTensor(
                    ortEnv,
                    FloatBuffer.wrap(floatArrayOf(step.toFloat())),
                    longArrayOf(1),
                )

                val veResult = vectorEstimator.run(
                    mapOf(
                        "noisy_latent" to noisyLatentTensor,
                        "text_emb" to textEmbTensor,
                        "style_ttl" to styleTtlTensor,
                        "latent_mask" to latentMaskTensor,
                        "text_mask" to textMaskTensor,
                        "current_step" to currentStepTensor,
                        "total_step" to totalStepTensor,
                    )
                )

                noisyLatentTensor.close()
                currentStepTensor.close()

                // Direct replacement: xt = denoised_latent
                val outputTensor = veResult.get("denoised_latent").get() as OnnxTensor
                outputTensor.floatBuffer.get(noisyLatent)
                veResult.close()
            }

            totalStepTensor.close()
            latentMaskTensor.close()
            styleTtlTensor.close()
            textMaskTensor.close()
            encoderResult.close()

            if (cancelled) return ByteArray(0)

            // Vocoder
            val latentTensor = OnnxTensor.createTensor(
                ortEnv,
                FloatBuffer.wrap(noisyLatent),
                longArrayOf(1, LATENT_CHANNELS.toLong(), latentLen.toLong()),
            )
            val vocoderResult = vocoder.run(mapOf("latent" to latentTensor))
            latentTensor.close()

            val wavTensor = vocoderResult.get("wav_tts").get() as OnnxTensor
            val wavLen = wavTensor.info.shape[1].toInt()
            val samples = FloatArray(wavLen).also { wavTensor.floatBuffer.get(it) }
            vocoderResult.close()

            // Build WAV
            WavBuilder.build(samples)

        } catch (e: Exception) {
            ByteArray(0)
        }
    }

    actual fun cancel() {
        cancelled = true
    }

    actual fun close() {
        dpSession?.close()
        textEncoderSession?.close()
        vectorEstimatorSession?.close()
        vocoderSession?.close()
        dpSession = null; textEncoderSession = null
        vectorEstimatorSession = null; vocoderSession = null
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Mirrors the official Python preprocessing pipeline:
     * NFKD normalization → add period if needed → wrap with language tokens.
     */
    private fun preprocessText(text: String, lang: String): String {
        var t = Normalizer.normalize(text, Normalizer.Form.NFKD)
        if (!t.last().isEndingPunctuation()) t += "."
        return "<$lang>$t</$lang>"
    }

    private fun Char.isEndingPunctuation() =
        this in setOf('.', '!', '?', ';', ':', ',', '\'', '"', ')', ']', '}', '…')

    /**
     * Parses a style tensor from the voice style JSON into a flat [FloatArray].
     * Handles both `style_ttl` [1, tokens, dim] and `style_dp` [1, tokens, dim].
     */
    private fun parseStyle(json: String, key: String, tokens: Int, dim: Int): FloatArray {
        val batch = Json.parseToJsonElement(json)
            .jsonObject[key]!!
            .jsonObject["data"]!!
            .jsonArray[0]
            .jsonArray
        val result = FloatArray(tokens * dim)
        for (i in 0 until tokens) {
            val row = batch[i].jsonArray
            for (j in 0 until dim) {
                result[i * dim + j] = row[j].jsonPrimitive.float
            }
        }
        return result
    }

}