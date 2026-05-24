package com.nedmah.supertonic_kmp.internal

import com.nedmah.supertonic_kmp.api.SupertonicConfig


/**
 * ONNX platform inference engine.
 * Android: Microsoft onnxruntime-android (OrtSession)
 * iOS: Microsoft onnxruntime via SupertonicBridge.swift
 */
internal expect class InferenceEngine(config: SupertonicConfig) {

    /**
     * Load three ONNX sessions from disk.
     * Call after the models are downloaded.
     * Thread-safe, can be called again when changing voices.
     */
    fun load()

    /**
     * Generate WAV audio from text.
     *
     * @param text Text to synthesize
     * @param lang BCP-47 language code ("ru", "en", ...)
     * @param voiceStyleJson Contents of the voice style JSON file (bundled in resources)
     * @param speed Speech rate (0.7–2.0)
     * @param steps Number of denoising steps
     * @return ByteArray WAV, empty array on error
     */
    fun generate(
        text: String,
        lang: String,
        voiceStyleJson: String,
        speed: Float,
        steps: Int,
    ): ByteArray

    /** Abort the current generation if it is in progress. */
    fun cancel()

    /** Release the ORT session. After the call, the object is unusable. */
    fun close()

    /** true if sessions are loaded and ready for inference. */
    val isLoaded: Boolean
}
