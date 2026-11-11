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

/**
 * Speech squeezed to 100 bytes a second with Codec2's 700C mode, which is what makes a voice clip fit in a few LoRa
 * packets. It sounds like digital radio, not like a phone call. Same codec and mode as the iPhone app
 * (SambungVoiceCodec.swift).
 *
 * Added for Sambung, November 2026.
 */
object SambungCodec2 {
    const val SAMPLE_RATE = 8000
    const val SAMPLES_PER_FRAME = 320
    const val BYTES_PER_FRAME = 4

    /** Codec id carried in the transfer manifest. */
    const val MANIFEST_CODEC_ID = 1

    init {
        System.loadLibrary("sambungcodec2")
    }

    /** 8 kHz mono samples in, Codec2 frames out. A trailing partial frame is padded with silence. */
    fun encode(samples: ShortArray): ByteArray {
        val frames = (samples.size + SAMPLES_PER_FRAME - 1) / SAMPLES_PER_FRAME
        return if (frames == 0) {
            ByteArray(0)
        } else {
            nativeEncode(samples.copyOf(frames * SAMPLES_PER_FRAME)) ?: ByteArray(0)
        }
    }

    /** Whole frames only: bytes past the last complete frame are ignored. */
    fun decode(clip: ByteArray): ShortArray {
        val whole = clip.size / BYTES_PER_FRAME * BYTES_PER_FRAME
        return if (whole == 0) ShortArray(0) else nativeDecode(clip.copyOf(whole)) ?: ShortArray(0)
    }

    private external fun nativeEncode(samples: ShortArray): ByteArray?

    private external fun nativeDecode(clip: ByteArray): ShortArray?
}
