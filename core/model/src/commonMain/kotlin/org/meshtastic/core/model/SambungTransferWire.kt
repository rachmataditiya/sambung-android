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
package org.meshtastic.core.model

/**
 * How Sambung carries a small file (a photo, a voice clip) over the mesh: the bytes are cut into chunks that each fit
 * one LoRa packet and sent on the private application port, which other Meshtastic clients ignore. A normal text
 * message goes out first, so those clients still show something, and the transfer names that message so Sambung can
 * draw the file in its place.
 *
 * Every packet starts with the magic byte and a type; integers are big-endian:
 * ```
 * manifest  'S' 1  id:u16 kind:u8 codec:u8 chunks:u8 size:u16 crc32:u32 message:u32 data…
 * chunk     'S' 2  id:u16 index:u8 data…
 * request   'S' 3  id:u16 count:u8 index:u8…        receiver asks for the chunks it lacks
 * complete  'S' 4  id:u16                           receiver has the whole file
 * ```
 *
 * The manifest carries chunk 0, so a one-packet file is one packet. This is the same format the iPhone app speaks
 * (SambungTransferWire.swift); the two must stay byte for byte alike.
 *
 * Added for Sambung, November 2026.
 */
@Suppress("MagicNumber")
object SambungTransferWire {
    const val MAGIC = 0x53

    /**
     * One packet stays about 1.2 seconds on air with LongFast. Longer ones arrive broken from boards without a
     * temperature-steady oscillator: the frequency slides while the radio transmits, and a two-second packet outlasts
     * what the receiver can follow (seen as CRC errors on the receiving radio, November 2026). A data payload could
     * hold 233 bytes; this is deliberately far below that.
     */
    const val MAX_PACKET_SIZE = 90
    const val MANIFEST_HEADER_SIZE = 17
    const val CHUNK_HEADER_SIZE = 5

    /** Policy, not format: about a minute of airtime on LongFast, and room for the largest photo. */
    const val MAX_CHUNKS = 56

    const val KIND_IMAGE = 1
    const val KIND_VOICE = 2

    private const val TYPE_MANIFEST = 1
    private const val TYPE_CHUNK = 2
    private const val TYPE_REQUEST = 3
    private const val TYPE_COMPLETE = 4
    private const val MAX_FILE_SIZE = 0xFFFF

    /** [messageId] is the packet id of the text message the file belongs to, as an unsigned 32-bit value. */
    data class Manifest(
        val id: Int,
        val kind: Int,
        val codec: Int,
        val chunkCount: Int,
        val size: Int,
        val crc32: Long,
        val messageId: Long,
    )

    sealed interface Packet {
        class ManifestPacket(val manifest: Manifest, val data: ByteArray) : Packet

        class Chunk(val id: Int, val index: Int, val data: ByteArray) : Packet

        data class Request(val id: Int, val missing: List<Int>) : Packet

        data class Complete(val id: Int) : Packet
    }

    private val crcTable =
        IntArray(256) { n ->
            var c = n
            repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
            c
        }

    /** The CRC-32 zlib computes, as an unsigned value. */
    fun crc32(data: ByteArray): Long {
        var c = -1
        for (byte in data) c = crcTable[(c xor byte.toInt()) and 0xFF] xor (c ushr 8)
        return (c.inv()).toLong() and 0xFFFFFFFFL
    }

    /** Cuts [file] into the packets to send, manifest first. Null when it is empty or too large to send. */
    fun packets(file: ByteArray, id: Int, kind: Int, codec: Int, messageId: Long): List<Packet>? {
        val firstSize = MAX_PACKET_SIZE - MANIFEST_HEADER_SIZE
        val restSize = MAX_PACKET_SIZE - CHUNK_HEADER_SIZE
        val chunkCount = if (file.size <= firstSize) 1 else 1 + (file.size - firstSize + restSize - 1) / restSize
        if (file.isEmpty() || chunkCount > MAX_CHUNKS || file.size > MAX_FILE_SIZE) return null

        val manifest = Manifest(id, kind, codec, chunkCount, file.size, crc32(file), messageId)
        val packets =
            mutableListOf<Packet>(Packet.ManifestPacket(manifest, file.copyOfRange(0, minOf(firstSize, file.size))))
        var offset = firstSize
        var index = 1
        while (offset < file.size) {
            packets += Packet.Chunk(id, index, file.copyOfRange(offset, minOf(offset + restSize, file.size)))
            offset += restSize
            index++
        }
        return packets
    }

    fun encode(packet: Packet): ByteArray {
        val out = mutableListOf(MAGIC.toByte())
        fun put8(value: Int) = out.add(value.toByte())
        fun put16(value: Int) {
            put8(value ushr 8)
            put8(value)
        }
        fun put32(value: Long) {
            put16((value ushr 16).toInt())
            put16(value.toInt())
        }
        when (packet) {
            is Packet.ManifestPacket -> {
                put8(TYPE_MANIFEST)
                put16(packet.manifest.id)
                put8(packet.manifest.kind)
                put8(packet.manifest.codec)
                put8(packet.manifest.chunkCount)
                put16(packet.manifest.size)
                put32(packet.manifest.crc32)
                put32(packet.manifest.messageId)
                out.addAll(packet.data.asList())
            }

            is Packet.Chunk -> {
                put8(TYPE_CHUNK)
                put16(packet.id)
                put8(packet.index)
                out.addAll(packet.data.asList())
            }

            is Packet.Request -> {
                put8(TYPE_REQUEST)
                put16(packet.id)
                put8(packet.missing.size)
                packet.missing.forEach(::put8)
            }

            is Packet.Complete -> {
                put8(TYPE_COMPLETE)
                put16(packet.id)
            }
        }
        return out.toByteArray()
    }

    /**
     * Null for anything that is not a well-formed Sambung transfer packet: the private port is shared with whatever
     * else people run on the mesh.
     */
    fun decode(payload: ByteArray): Packet? {
        fun get8(at: Int) = payload[at].toInt() and 0xFF
        fun get16(at: Int) = (get8(at) shl 8) or get8(at + 1)
        fun get32(at: Int) = (get16(at).toLong() shl 16) or get16(at + 2).toLong()
        val wellFormed = payload.size >= 4 && get8(0) == MAGIC
        val id = if (wellFormed) get16(2) else 0
        return when (if (wellFormed) get8(1) else 0) {
            TYPE_MANIFEST -> {
                val kind = if (payload.size > MANIFEST_HEADER_SIZE) get8(4) else 0
                if ((kind == KIND_IMAGE || kind == KIND_VOICE) && get8(6) >= 1) {
                    val manifest = Manifest(id, kind, get8(5), get8(6), get16(7), get32(9), get32(13))
                    Packet.ManifestPacket(manifest, payload.copyOfRange(MANIFEST_HEADER_SIZE, payload.size))
                } else {
                    null
                }
            }

            TYPE_CHUNK ->
                if (payload.size > CHUNK_HEADER_SIZE) {
                    Packet.Chunk(id, get8(4), payload.copyOfRange(CHUNK_HEADER_SIZE, payload.size))
                } else {
                    null
                }

            TYPE_REQUEST ->
                if (payload.size >= 5 && payload.size == 5 + get8(4)) {
                    Packet.Request(id, (5 until payload.size).map(::get8))
                } else {
                    null
                }

            TYPE_COMPLETE -> Packet.Complete(id)

            else -> null
        }
    }

    /**
     * Collects the chunks of one incoming file. Chunks may arrive in any order, more than once, and before the
     * manifest.
     */
    class Assembler {
        var manifest: Manifest? = null
            private set

        private val chunks = mutableMapOf<Int, ByteArray>()

        val receivedCount: Int
            get() = chunks.size

        fun add(packet: Packet) {
            when (packet) {
                is Packet.ManifestPacket -> {
                    manifest = packet.manifest
                    chunks[0] = packet.data
                }

                is Packet.Chunk -> chunks[packet.index] = packet.data

                is Packet.Request,
                is Packet.Complete,
                -> Unit
            }
        }

        /** Every index still needed. Before the manifest arrives the total is unknown, so only it is asked for. */
        val missing: List<Int>
            get() = manifest?.let { known -> (0 until known.chunkCount).filter { it !in chunks } } ?: listOf(0)

        /** The file once every chunk is here and the checksum matches, otherwise null. */
        fun file(): ByteArray? {
            val known = manifest
            return if (known == null || missing.isNotEmpty()) {
                null
            } else {
                (0 until known.chunkCount)
                    .fold(ByteArray(0)) { all, index -> all + chunks.getValue(index) }
                    .takeIf { it.size == known.size && crc32(it) == known.crc32 }
            }
        }

        /** All chunks are here but they do not add up to the file that was announced. */
        val isCorrupt: Boolean
            get() = manifest != null && missing.isEmpty() && file() == null
    }
}
