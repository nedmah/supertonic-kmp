package com.nedmah.supertonic_kmp.internal

/**
 * Splits long text into chunks suitable for TTS inference.
 *
 * Splitting strategy:
 * 1. Split at sentence boundaries (`.` `!` `?` `…` `...`)
 * 2. Abbreviations like "т.е.", "др.", "Mr." are not treated as sentence ends
 * 3. Sentences longer than [maxChunkLength] are split at commas or semicolons,
 *    and as a last resort at whitespace
 * 4. Empty chunks are never returned
 *
 * @param maxChunkLength Soft maximum chunk length in characters. Default: 200.
 */
internal class TextChunker(private val maxChunkLength: Int = 200) {

    /**
     * Splits [text] into a list of non-empty chunks ready for [InferenceEngine.generate].
     */
    fun split(text: String): List<String> {
        if (text.length <= maxChunkLength) return listOf(text.trim()).filter { it.isNotEmpty() }

        val sentences = splitSentences(text)
        return mergeSentences(sentences)
    }

    private fun splitSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val buffer = StringBuilder()

        var i = 0
        while (i < text.length) {
            val ch = text[i]
            buffer.append(ch)

            if (ch.isSentenceEnd()) {
                // consume trailing sentence-end chars (e.g. "!..", "...")
                while (i + 1 < text.length && text[i + 1].isSentenceEnd()) {
                    i++
                    buffer.append(text[i])
                }

                val candidate = buffer.toString()
                if (!isAbbreviation(candidate, text, i)) {
                    result += candidate.trim()
                    buffer.clear()
                }
            }
            i++
        }

        val tail = buffer.toString().trim()
        if (tail.isNotEmpty()) result += tail

        return result.filter { it.isNotEmpty() }
    }

    /**
     * Returns true if the period at [dotPos] in [fullText] is part of an abbreviation
     * rather than a sentence boundary.
     *
     * Heuristics:
     * - preceded by 1–3 letters (т.е. / Mr. / др.)
     * - followed by a lowercase letter or digit (не начало нового предложения)
     * - followed by another period immediately (already handled as ellipsis)
     */
    private fun isAbbreviation(candidate: String, fullText: String, dotPos: Int): Boolean {
        val trimmed = candidate.trimEnd()
        if (!trimmed.endsWith('.')) return false

        for (abbr in ABBREVIATIONS) {
            if (trimmed.endsWith(abbr, ignoreCase = true)) return true
        }

        // Preceded by 1-3 letters likely abbreviation
        val beforeDot = trimmed.dropLast(1)
        val tail = beforeDot.takeLastWhile { it.isLetter() }
        if (tail.length in 1..3) {
            // followed by space + lowercase -> abbreviation
            val nextNonSpace = fullText.drop(dotPos + 1).trimStart()
            if (nextNonSpace.isNotEmpty()) {
                val nextChar = nextNonSpace.first()
                if (nextChar.isLowerCase() || nextChar.isDigit()) return true
            }
        }

        return false
    }

    private fun mergeSentences(sentences: List<String>): List<String> {
        val result = mutableListOf<String>()
        val buffer = StringBuilder()

        for (sentence in sentences) {
            val wouldBeLength = if (buffer.isEmpty()) sentence.length
            else buffer.length + 1 + sentence.length

            when {
                // sentence fits into the current buffer
                wouldBeLength <= maxChunkLength -> {
                    if (buffer.isNotEmpty()) buffer.append(' ')
                    buffer.append(sentence)
                }
                // current buffer is non-empty - flush it, then handle sentence
                buffer.isNotEmpty() -> {
                    result += buffer.toString()
                    buffer.clear()
                    if (sentence.length <= maxChunkLength) {
                        buffer.append(sentence)
                    } else {
                        result += splitLong(sentence)
                    }
                }
                // sentence alone is too long
                else -> result += splitLong(sentence)
            }
        }

        if (buffer.isNotEmpty()) result += buffer.toString()
        return result.filter { it.isNotEmpty() }
    }

    /**
     * Splits a single sentence that exceeds [maxChunkLength].
     * Tries comma/semicolon boundaries first, then falls back to whitespace.
     */
    private fun splitLong(sentence: String): List<String> {
        if (sentence.length <= maxChunkLength) return listOf(sentence)

        // Try to split at commas / semicolons
        val parts = splitAt(sentence, setOf(',', ';'))
        if (parts.size > 1) return parts.flatMap { splitLong(it.trim()) }

        // Fallback: split at whitespace
        return splitAtWhitespace(sentence)
    }

    private fun splitAt(text: String, delimiters: Set<Char>): List<String> {
        val result = mutableListOf<String>()
        val buffer = StringBuilder()

        for (ch in text) {
            buffer.append(ch)
            if (ch in delimiters && buffer.length >= maxChunkLength / 2) {
                result += buffer.toString().trim()
                buffer.clear()
            }
        }

        if (buffer.isNotEmpty()) result += buffer.toString().trim()
        return result.filter { it.isNotEmpty() }
    }

    private fun splitAtWhitespace(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0

        while (start < text.length) {
            var end = (start + maxChunkLength).coerceAtMost(text.length)
            if (end < text.length) {
                // walk back to the nearest space
                val spacePos = text.lastIndexOf(' ', end)
                if (spacePos > start) end = spacePos
            }
            result += text.substring(start, end).trim()
            start = end
        }

        return result.filter { it.isNotEmpty() }
    }

    // helpers

    private fun Char.isSentenceEnd() = this == '.' || this == '!' || this == '?' || this == '…'

    companion object {
        /**
         * Common abbreviations whose trailing period must not be treated as a sentence boundary.
         * Covers Russian, Ukrainian, English, and a few other European languages.
         */
        private val ABBREVIATIONS = setOf(
            // Russian
            "т.е.", "т.д.", "т.п.", "и.о.", "и.д.", "т.к.", "т.н.",
            "др.", "пр.", "гр.", "кг.", "км.", "см.", "мл.", "мм.",
            "руб.", "коп.", "тыс.", "млн.", "млрд.",
            "ул.", "пр-т.", "пл.", "р-н.", "обл.", "р-д.",
            "рис.", "стр.", "гл.", "разд.", "п.", "пп.", "ст.", "ч.",
            "г.", "гг.", "в.", "вв.", "н.э.", "до н.э.",
            // English
            "mr.", "mrs.", "ms.", "dr.", "prof.", "sr.", "jr.", "vs.",
            "etc.", "approx.", "dept.", "est.", "fig.", "no.", "p.", "pp.",
            "vol.", "jan.", "feb.", "mar.", "apr.", "jun.", "jul.", "aug.",
            "sep.", "oct.", "nov.", "dec.",
        )
    }
}