package com.nedmah.supertonic_kmp.internal

/**
 * Holds the Swift-implemented inference bridge for iOS.
 *
 * Initialized from the host app's entry point (e.g. iOSApp.swift):
 * ```swift
 * SupertonicHolder.shared.bridge = SupertonicBridge()
 * ```
 */
object SupertonicHolder {
    var bridge: SupertonicBridgeProtocol? = null
}