package com.nedmah.supertonic_kmp.api

/**
 * Engine configuration.
 *
 * @param storageDir Directory for storing ONNX models (~265MB).
 * Android: context.filesDir.path + "/supertonic"
 * iOS: NSDocumentDirectory + "/supertonic"
 * @param inferenceSteps Number of denoising steps.
 * 2-4 — fast, for streaming
 * 8 — default, a balance of speed and quality
 * 12+ — maximum quality, noticeably slower
 * @param defaultLang BCP-47 default language code. * Full list: ar, bg, hr, cs, da, nl, en, et, fi, fr, de,
 * el, hi, hu, id, it, ja, ko, lv, lt, pl, pt, ro, ru, sk, sl,
 * es, sv, tr, uk, vi. Pass "na" if the language is unknown.
 * @param defaultVoice Default voice.
 * @param defaultSpeed ​​Speech rate. Recommended range: 0.7–2.0.
 * @param huggingFaceBaseUrl Base URL for downloading models.
 * Can be replaced with your own CDN if needed.
 */
data class SupertonicConfig(
    val storageDir: String,
    val inferenceSteps: Int = 8,
    val defaultLang: String = "ru",
    val defaultVoice: SupertonicVoice = SupertonicVoice.M1,
    val defaultSpeed: Float = 1.0f,
    val huggingFaceBaseUrl: String = DEFAULT_HF_URL,
) {
    companion object {
        const val DEFAULT_HF_URL =
            "https://huggingface.co/Supertone/supertonic-3/resolve/main"

        /** List of model files to download. */
        val MODEL_FILES = listOf(
            "onnx/text_encoder.onnx",
            "onnx/vector_estimator.onnx",
            "onnx/vocoder.onnx",
            "onnx/tts.json",
            "onnx/unicode_indexer.json",
            "onnx/duration_predictor.onnx"
        )

        /** Approximate total size of models in bytes (~265MB). */
        const val APPROX_MODEL_SIZE_BYTES = 265L * 1024 * 1024
    }
}
