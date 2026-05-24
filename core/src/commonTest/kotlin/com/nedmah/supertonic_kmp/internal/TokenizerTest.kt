package com.nedmah.supertonic_kmp.internal

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenizerTest {

    // Minimal 256-entry table covering our test chars:
    //   ' ' (U+0020) -> 2
    //   '!' (U+0021) -> 3
    //   'a' (U+0061) -> 60
    //   'z' (U+007A) -> 85
    //   '#' (U+0023) -> -1  (unsupported)
    // Everything else -> -1
    private val tokenizer = Tokenizer(MOCK_JSON)

    @Test
    fun `space maps to index 2`() {
        assertContentEquals(intArrayOf(2), tokenizer.tokenize(" "))
    }

    @Test
    fun `exclamation mark maps to index 3`() {
        assertContentEquals(intArrayOf(3), tokenizer.tokenize("!"))
    }

    @Test
    fun `a maps to 60 and z maps to 85`() {
        assertContentEquals(intArrayOf(60, 85), tokenizer.tokenize("az"))
    }

    @Test
    fun `unsupported char is silently dropped`() {
        // '#' has no mapping in the table
        assertContentEquals(intArrayOf(60, 85), tokenizer.tokenize("a#z"))
    }

    @Test
    fun `empty string returns empty array`() {
        assertContentEquals(intArrayOf(), tokenizer.tokenize(""))
    }

    @Test
    fun `string of only unsupported chars returns empty array`() {
        assertContentEquals(intArrayOf(), tokenizer.tokenize("###"))
    }

    @Test
    fun `order of tokens matches order of characters`() {
        assertContentEquals(intArrayOf(85, 2, 60), tokenizer.tokenize("z a"))
    }

    @Test
    fun `char above table size is dropped`() {
        // Our mock table has only 256 entries; U+0400 'А' is beyond it
        val result = tokenizer.tokenize("А")
        assertContentEquals(intArrayOf(), result)
    }

    @Test
    fun `isFullySupported returns true when all chars are mapped`() {
        assertTrue(tokenizer.isFullySupported("a z!"))
    }

    @Test
    fun `isFullySupported returns false when any char is missing`() {
        assertFalse(tokenizer.isFullySupported("a#z"))
    }

    @Test
    fun `isFullySupported returns false for char above table size`() {
        assertFalse(tokenizer.isFullySupported("aА"))
    }

    @Test
    fun `isFullySupported returns true for empty string`() {
        assertTrue(tokenizer.isFullySupported(""))
    }

    companion object {
        // Hand-crafted 256-element JSON array:
        // ' '=2, '!'=3, 'a'=60, 'z'=85, everything else=-1
        private val MOCK_JSON: String = buildString {
            val table = IntArray(256) { -1 }
            table[0x20] = 2   // ' '
            table[0x21] = 3   // '!'
            table[0x61] = 60  // 'a'
            table[0x7A] = 85  // 'z'
            append('[')
            append(table.joinToString(","))
            append(']')
        }
    }
}