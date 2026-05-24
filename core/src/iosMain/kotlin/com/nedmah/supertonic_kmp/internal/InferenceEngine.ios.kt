package com.nedmah.supertonic_kmp.internal

import com.nedmah.supertonic_kmp.api.SupertonicConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * iOS [InferenceEngine] — thin wrapper that delegates to [SupertonicBridgeProtocol].
 *
 * All heavy lifting (ORT sessions, tokenization, denoising loop) is done in
 * `SupertonicBridge.swift`. This class only routes calls through [SupertonicHolder].
 *
 * The bridge must be set up before [load] is called:
 * ```swift
 * // iOSApp.swift
 * SupertonicHolder.shared.bridge = SupertonicBridge()
 * ```
 */
internal actual class InferenceEngine actual constructor(
    private val config: SupertonicConfig,
) {
    actual val isLoaded: Boolean
        get() = SupertonicHolder.bridge?.isLoaded ?: false

    actual fun load() {
        checkBridge().load(config.storageDir)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun generate(
        text: String,
        lang: String,
        voiceStyleJson: String,
        speed: Float,
        steps: Int,
    ): ByteArray {
        val nsData: NSData = checkBridge().generate(text, lang, voiceStyleJson, speed, steps)
            ?: return ByteArray(0)

        val length = nsData.length.toInt()
        if (length == 0) return ByteArray(0)

        val result = ByteArray(length)
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
        }
        return result
    }

    actual fun cancel() {
        SupertonicHolder.bridge?.cancel()
    }

    actual fun close() {
        SupertonicHolder.bridge?.close()
    }

    private fun checkBridge(): SupertonicBridgeProtocol =
        SupertonicHolder.bridge
            ?: error(
                "SupertonicBridge not initialized. " +
                        "Set SupertonicHolder.shared.bridge = SupertonicBridge() " +
                        "in your iOSApp.swift before using SupertonicTts."
            )
}