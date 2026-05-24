package com.nedmah.supertonic_kmp.internal

/**
 * Swift-side inference bridge protocol.
 *
 * Implemented by `SupertonicBridge.swift` in the host app.
 * All four ONNX models are run on the Swift side via ORT Swift SPM.
 */
interface SupertonicBridgeProtocol {
    val isLoaded: Boolean

    /**
     * Load all four ONNX sessions from [storageDir].
     * Must be called before [generate].
     */
    fun load(storageDir: String)

    /**
     * Run the full TTS pipeline and return raw [platform.Foundation.NSData].
     *
     * @param text      Raw input text (preprocessing done inside the bridge)
     * @param lang      BCP-47 language code, e.g. "ru"
     * @param voiceStyleJson Contents of the bundled voice style JSON
     * @param speed     Speech rate multiplier (applied to duration predictor output)
     * @param steps     Number of denoising steps
     * @return WAV bytes (44100 Hz, 16-bit PCM, mono), or empty on error/cancel
     */
    fun generate(
        text: String,
        lang: String,
        voiceStyleJson: String,
        speed: Float,
        steps: Int,
    ): platform.Foundation.NSData?

    /** Abort the current generation between denoising steps. */
    fun cancel()

    /** Release all ORT sessions. */
    fun close()
}