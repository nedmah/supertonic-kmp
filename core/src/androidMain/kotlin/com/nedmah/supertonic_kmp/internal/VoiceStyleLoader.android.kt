package com.nedmah.supertonic_kmp.internal

/**
 * Loads a bundled resource from the library's classpath.
 *
 * Files in `commonMain/resources/` are packaged as Java classpath resources
 * in the AAR. Accessible via [ClassLoader.getResourceAsStream].
 *
 * @param path Resource path, e.g. "supertonic/voice_styles/M1.json"
 * @throws IllegalStateException if the resource is not found
 */
internal actual fun loadResource(path: String): String {
    val classLoader = checkNotNull(
        Thread.currentThread().contextClassLoader
            ?: object {}.javaClass.classLoader
    ) { "No ClassLoader available" }

    val stream = checkNotNull(classLoader.getResourceAsStream(path)) {
        "Resource not found: $path"
    }

    return stream.use { it.bufferedReader().readText() }
}