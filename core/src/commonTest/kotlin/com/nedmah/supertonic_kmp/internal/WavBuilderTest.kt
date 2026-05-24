package com.nedmah.supertonic_kmp.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WavBuilderTest {

    @Test
    fun `empty samples produces valid 44-byte WAV header`() {
        val wav = WavBuilder.build(FloatArray(0))
        assertEquals(44, wav.size)
        assertWavHeader(wav, dataSize = 0)
    }

    @Test
    fun `total size is 44 plus two bytes per sample`() {
        val samples = FloatArray(1000)
        val wav = WavBuilder.build(samples)
        assertEquals(44 + 1000 * 2, wav.size)
    }

    @Test
    fun `RIFF WAVE fmt data markers are correct`() {
        val wav = WavBuilder.build(FloatArray(1))
        assertEquals("RIFF", wav.readAscii(0, 4))
        assertEquals("WAVE", wav.readAscii(8, 4))
        assertEquals("fmt ", wav.readAscii(12, 4))
        assertEquals("data", wav.readAscii(36, 4))
    }

    @Test
    fun `sample rate is 44100`() {
        val wav = WavBuilder.build(FloatArray(1))
        assertEquals(44100, wav.readInt32Le(24))
    }

    @Test
    fun `bits per sample is 16`() {
        val wav = WavBuilder.build(FloatArray(1))
        assertEquals(16, wav.readInt16Le(34))
    }

    @Test
    fun `channel count is 1`() {
        val wav = WavBuilder.build(FloatArray(1))
        assertEquals(1, wav.readInt16Le(22))
    }

    @Test
    fun `silence sample encodes to zero bytes`() {
        val wav = WavBuilder.build(floatArrayOf(0f))
        assertEquals(0, wav[44].toInt())
        assertEquals(0, wav[45].toInt())
    }

    @Test
    fun `positive full-scale sample encodes to Short MAX_VALUE`() {
        val wav = WavBuilder.build(floatArrayOf(1f))
        val pcm = wav.readInt16Le(44)
        assertEquals(Short.MAX_VALUE.toInt(), pcm)
    }

    @Test
    fun `negative full-scale sample encodes to -32767`() {
        val wav = WavBuilder.build(floatArrayOf(-1f))
        val pcm = wav.readInt16LeSignedShort(44)
        assertEquals((-32767).toShort(), pcm)
    }

    @Test
    fun `samples above 1f are clamped`() {
        val clamped = WavBuilder.build(floatArrayOf(2f))
        val unclamped = WavBuilder.build(floatArrayOf(1f))
        assertEquals(unclamped.readInt16Le(44), clamped.readInt16Le(44))
    }

    @Test
    fun `samples below -1f are clamped`() {
        val clamped = WavBuilder.build(floatArrayOf(-2f))
        val unclamped = WavBuilder.build(floatArrayOf(-1f))
        assertEquals(unclamped.readInt16Le(44), clamped.readInt16Le(44))
    }

    @Test
    fun `RIFF chunk size equals total size minus 8`() {
        val samples = FloatArray(500)
        val wav = WavBuilder.build(samples)
        assertEquals(wav.size - 8, wav.readInt32Le(4))
    }

    @Test
    fun `data chunk size equals sample count times two`() {
        val samples = FloatArray(300)
        val wav = WavBuilder.build(samples)
        assertEquals(300 * 2, wav.readInt32Le(40))
    }

    // --- Helpers ---

    private fun assertWavHeader(wav: ByteArray, dataSize: Int) {
        assertTrue(wav.size >= 44)
        assertEquals("RIFF", wav.readAscii(0, 4))
        assertEquals("WAVE", wav.readAscii(8, 4))
        assertEquals("fmt ", wav.readAscii(12, 4))
        assertEquals("data", wav.readAscii(36, 4))
        assertEquals(dataSize, wav.readInt32Le(40))
    }

    private fun ByteArray.readAscii(offset: Int, length: Int) =
        this.decodeToString(startIndex = offset, endIndex = offset + length)

    private fun ByteArray.readInt32Le(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8) or
                ((this[offset + 2].toInt() and 0xFF) shl 16) or
                ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.readInt16Le(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.readInt16LeSignedShort(offset: Int): Short =
        readInt16Le(offset).toShort()
}