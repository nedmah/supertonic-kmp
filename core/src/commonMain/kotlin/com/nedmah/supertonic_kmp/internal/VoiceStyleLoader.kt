package com.nedmah.supertonic_kmp.internal


/**
 * Loads voice style JSON from the library's bundled resources.
 *
 * The files are located in commonMain/resources/supertonic/voice_styles/.json
 * and are bundled into the final artifact (AAR/framework).
 *
 */
internal class VoiceStyleLoader {

    private val cache = mutableMapOf<String, String>()

    /**
     * @param resourcePath for example "supertonic/voice_styles/M1.json"
     */
    fun load(resourcePath: String): String {
        return cache.getOrPut(resourcePath) {
            loadResource(resourcePath)
        }
    }
}

/**
 * Platform-specific resource loading via a path within a bundle.
 */
internal expect fun loadResource(path: String): String