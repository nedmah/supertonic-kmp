package com.nedmah.supertonic_kmp.internal

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Character-level tokenizer backed by Supertonic's `unicode_indexer.json`.
 *
 * The indexer is a flat JSON array of 65536 integers (BMP Unicode).
 * Position = codepoint, value = token index, -1 = unsupported character.
 *
 * Characters that map to -1 are silently dropped from the output.
 *
 * @param indexerJson Raw content of `onnx/unicode_indexer.json`.
 */
internal class Tokenizer(indexerJson: String) {

    /** Lookup table: codepoint -> token index, -1 if unsupported. */
    private val table: IntArray = parseTable(indexerJson)

    /**
     * Converts [text] into a sequence of token indices.
     *
     * @param text Input text. Characters outside the BMP or with no mapping are dropped.
     * @return Token indices ready to be used as `input_ids` for `text_encoder.onnx`.
     */
    fun tokenize(text: String): IntArray {
        val result = IntArray(text.length) // upper bound
        var size = 0
        for (ch in text) {
            val cp = ch.code
            if (cp >= table.size) continue
            val idx = table[cp]
            if (idx == -1) continue
            result[size++] = idx
        }
        return result.copyOf(size)
    }

    /**
     * Returns `true` if every character in [text] has a token mapping.
     * Useful for deciding whether to pre-process text before tokenizing.
     */
    fun isFullySupported(text: String): Boolean =
        text.all { ch -> ch.code < table.size && table[ch.code] != -1 }

    // --- Private ---

    private fun parseTable(json: String): IntArray {
        val list = Json.decodeFromString(ListSerializer(Int.serializer()), json)
        return list.toIntArray()
    }
}