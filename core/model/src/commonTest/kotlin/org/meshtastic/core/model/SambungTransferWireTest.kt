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

import org.meshtastic.core.model.SambungTransferWire.Packet
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SambungTransferWireTest {
    private fun file(size: Int) = ByteArray(size) { (it * 31 + 7).toByte() }

    private fun hex(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun crcMatchesZlib() {
        assertEquals(0xCBF43926L, SambungTransferWire.crc32("123456789".encodeToByteArray()))
        assertEquals(0x352441C2L, SambungTransferWire.crc32("abc".encodeToByteArray()))
    }

    @Test
    fun aManifestIsLaidOutLikeTheIphoneApp() {
        val packets =
            SambungTransferWire.packets(
                "abc".encodeToByteArray(),
                0x0102,
                SambungTransferWire.KIND_IMAGE,
                2,
                0x0A0B0C0DL,
            )
        val expected =
            hex(
                0x53,
                1,
                0x01,
                0x02,
                1,
                2,
                1,
                0x00,
                0x03,
                0x35,
                0x24,
                0x41,
                0xC2,
                0x0A,
                0x0B,
                0x0C,
                0x0D,
                0x61,
                0x62,
                0x63,
            )
        assertEquals(1, packets?.size)
        assertContentEquals(expected, SambungTransferWire.encode(packets!!.single()))
    }

    @Test
    fun aFileSurvivesBeingCutSentOutOfOrderAndPutTogether() {
        val original = file(1_000)
        val packets = SambungTransferWire.packets(original, 7, SambungTransferWire.KIND_VOICE, 1, 0xFFFFFFFFL)!!
        assertEquals(6, packets.size)
        assertTrue(packets.all { SambungTransferWire.encode(it).size <= SambungTransferWire.MAX_PACKET_SIZE })

        val assembler = SambungTransferWire.Assembler()
        // Reversed, and one of them twice: the manifest arrives last.
        (packets.reversed() + packets[2]).forEach { packet ->
            assembler.add(SambungTransferWire.decode(SambungTransferWire.encode(packet))!!)
        }
        assertContentEquals(original, assembler.file())
        assertEquals(0xFFFFFFFFL, assembler.manifest?.messageId)
    }

    @Test
    fun missingChunksAreNamedUntilTheyArrive() {
        val packets = SambungTransferWire.packets(file(600), 9, SambungTransferWire.KIND_IMAGE, 1, 1L)!!
        val assembler = SambungTransferWire.Assembler()
        assertEquals(listOf(0), assembler.missing)
        assembler.add(packets[0])
        assembler.add(packets[2])
        assertEquals(listOf(1, 3), assembler.missing)
        assertNull(assembler.file())
    }

    @Test
    fun aDamagedFileIsRefused() {
        val packets = SambungTransferWire.packets(file(300), 9, SambungTransferWire.KIND_IMAGE, 1, 1L)!!
        val assembler = SambungTransferWire.Assembler()
        assembler.add(packets[0])
        val second = packets[1] as Packet.Chunk
        assembler.add(Packet.Chunk(second.id, second.index, second.data.copyOf().also { it[0] = (it[0] + 1).toByte() }))
        assertNull(assembler.file())
        assertTrue(assembler.isCorrupt)
    }

    @Test
    fun emptyAndOversizedFilesAreNotSent() {
        assertNull(SambungTransferWire.packets(ByteArray(0), 1, SambungTransferWire.KIND_IMAGE, 1, 1L))
        assertNull(SambungTransferWire.packets(file(4_669), 1, SambungTransferWire.KIND_IMAGE, 1, 1L))
        assertEquals(24, SambungTransferWire.packets(file(4_668), 1, SambungTransferWire.KIND_IMAGE, 1, 1L)?.size)
    }

    @Test
    fun requestAndCompleteRoundTrip() {
        val request = SambungTransferWire.decode(SambungTransferWire.encode(Packet.Request(513, listOf(1, 4, 23))))
        assertEquals(Packet.Request(513, listOf(1, 4, 23)), request)
        assertEquals(Packet.Complete(513), SambungTransferWire.decode(SambungTransferWire.encode(Packet.Complete(513))))
    }

    @Test
    fun otherTrafficOnThePrivatePortIsIgnored() {
        assertNull(SambungTransferWire.decode(hex(0x41, 1, 0, 1)))
        assertNull(SambungTransferWire.decode(hex(0x53, 9, 0, 1)))
        assertNull(SambungTransferWire.decode(hex(0x53, 1, 0, 1, 1, 1, 1)))
        assertNull(SambungTransferWire.decode(hex(0x53)))
    }
}
