package com.nedmah.supertonic_kmp.api

/**
 * Preinstalled Supertonic v3 voices.
 * All voice styles are bundled in the library; no need to download them separately.
 *
 * @param id Style file ID (M1.json, F1.json, ...)
 * @param gender Voice gender
 */
enum class SupertonicVoice(val id: String, val gender: Gender) {
    M1("M1", Gender.MALE),
    M2("M2", Gender.MALE),
    M3("M3", Gender.MALE),
    M4("M4", Gender.MALE),
    M5("M5", Gender.MALE),
    F1("F1", Gender.FEMALE),
    F2("F2", Gender.FEMALE),
    F3("F3", Gender.FEMALE),
    F4("F4", Gender.FEMALE),
    F5("F5", Gender.FEMALE);

    enum class Gender { MALE, FEMALE }

    /** Path to the bundled style file inside the library resources. */
    internal val resourcePath: String get() = "supertonic/voice_styles/$id.json"
}
