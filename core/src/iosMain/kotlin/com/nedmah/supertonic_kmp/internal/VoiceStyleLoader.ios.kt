package com.nedmah.supertonic_kmp.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.darwin.NSObject

/**
 * Loads a bundled resource from the Kotlin framework bundle.
 *
 * Resources in `commonMain/resources/` are packaged inside the compiled
 * `.framework` bundle. We locate them via [NSBundle] of the framework itself,
 * not `mainBundle` (which belongs to the host app).
 *
 * @param path Resource path, e.g. "supertonic/voice_styles/M1.json"
 * @throws IllegalStateException if the resource is not found or cannot be read
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun loadResource(path: String): String {
    // Resolve the framework bundle via an in-framework object
    val bundle = NSBundle.bundleForClass(object : NSObject() {}.`class`()!!)

    val name = path.substringBeforeLast('.')
    val ext = path.substringAfterLast('.')
    val dir = name.substringBeforeLast('/', missingDelimiterValue = "")
        .takeIf { it.isNotEmpty() }
    val base = name.substringAfterLast('/')

    val filePath = bundle.pathForResource(
        name = base,
        ofType = ext,
        inDirectory = dir,
    ) ?: NSBundle.mainBundle.pathForResource(name, ofType = ext, inDirectory = dir)
    ?: error("Resource not found: $path")

    return NSString.stringWithContentsOfFile(
        path = filePath,
        encoding = NSUTF8StringEncoding,
        error = null,
    ) as String? ?: error("Cannot read resource: $path")
}