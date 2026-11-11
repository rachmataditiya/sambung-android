/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.codec2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Runs on a phone: the codec is native code. The assets are what the iPhone app's Codec2 makes of the shared clip
 * (sambung-ios Packages/Codec2 tests): the encoded bytes and the samples they decode to.
 */
@RunWith(AndroidJUnit4::class)
class SambungCodec2Test {
    private fun asset(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }

    /** The same whole-number signal the iPhone tests build. */
    private fun sharedClip() = ShortArray(CLIP_SAMPLES) { i ->
        val saw = ((i * 37) % 200 - 100) * 60
        val rough = ((i * i) % 61 - 30) * 40
        val pulse = if ((i / 80) % 2 == 0) 2000 else -2000
        (saw + rough + pulse).toShort()
    }

    private val iphoneEncoded by lazy { asset("codec2-700c-clip.enc") }
    private val iphoneDecoded by lazy {
        val buffer = ByteBuffer.wrap(asset("codec2-700c-clip.dec")).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        ShortArray(buffer.remaining()).also(buffer::get)
    }

    /** 1.0 when two signals have the same shape. */
    private fun likeness(a: ShortArray, b: ShortArray): Double {
        var dot = 0.0
        var aa = 0.0
        var bb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i].toDouble()
            aa += a[i] * a[i].toDouble()
            bb += b[i] * b[i].toDouble()
        }
        return dot / sqrt(aa * bb)
    }

    /**
     * Loudness over time: the root mean square of each 20 ms. Two decoders agree on this even where their phases
     * differ.
     */
    private fun envelope(samples: ShortArray): ShortArray {
        val frames = samples.size / ENVELOPE_FRAME
        return ShortArray(frames) { frame ->
            var sum = 0.0
            for (i in 0 until ENVELOPE_FRAME) {
                val value = samples[frame * ENVELOPE_FRAME + i].toDouble()
                sum += value * value
            }
            sqrt(sum / ENVELOPE_FRAME).toInt().toShort()
        }
    }

    private fun report(name: String, decoded: ShortArray) {
        val maxDifference = decoded.indices.maxOf { kotlin.math.abs(decoded[it] - iphoneDecoded[it]) }
        println(
            "SambungCodec2Test: $name waveform likeness ${likeness(decoded, iphoneDecoded)}, " +
                "loudness likeness ${likeness(
                    envelope(decoded),
                    envelope(iphoneDecoded),
                )}, largest sample difference $maxDifference",
        )
    }

    @Test
    fun aClipFromTheIphoneOpensIntoTheSameSound() {
        val decoded = SambungCodec2.decode(iphoneEncoded)
        assertEquals(iphoneDecoded.size, decoded.size)
        report("decode", decoded)
        assertTrue(likeness(envelope(decoded), envelope(iphoneDecoded)) > CLOSE_ENOUGH)
    }

    @Test
    fun theSharedClipIsCompressedLikeTheIphoneDoes() {
        val encoded = SambungCodec2.encode(sharedClip())
        assertEquals(iphoneEncoded.size, encoded.size)
        val differing = encoded.indices.count { encoded[it] != iphoneEncoded[it] }
        println("SambungCodec2Test: $differing of ${encoded.size} encoded bytes differ from the iPhone's")
        // The bytes on the air are what both apps must agree on.
        assertEquals(0, differing)
    }

    @Test
    fun aPartialFrameIsPaddedAndStrayBytesAreIgnored() {
        assertEquals(
            2 * SambungCodec2.BYTES_PER_FRAME,
            SambungCodec2.encode(ShortArray(SambungCodec2.SAMPLES_PER_FRAME + 1)).size,
        )
        assertEquals(
            SambungCodec2.SAMPLES_PER_FRAME,
            SambungCodec2.decode(ByteArray(SambungCodec2.BYTES_PER_FRAME + 3)).size,
        )
        assertEquals(0, SambungCodec2.encode(ShortArray(0)).size)
        assertEquals(0, SambungCodec2.decode(ByteArray(3)).size)
    }

    private companion object {
        const val CLIP_SAMPLES = 8000
        const val CLOSE_ENOUGH = 0.98
        const val ENVELOPE_FRAME = 160
    }
}
