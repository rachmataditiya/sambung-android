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
package org.meshtastic.core.repository

import okio.FileSystem
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SambungMediaBoardTest {
    private val folder =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "sambung-media-test-${Random.nextInt(1, Int.MAX_VALUE)}"
    private val photo = ByteArray(300) { it.toByte() }
    private val key = SambungMediaBoard.key(senderNodeNum = -2, messagePacketId = 77)

    private fun attach() {
        SambungMediaBoard.media.value = emptyMap()
        SambungMediaBoard.attach(folder.toString())
    }

    @AfterTest
    fun cleanUp() {
        FileSystem.SYSTEM.deleteRecursively(folder)
        SambungMediaBoard.media.value = emptyMap()
    }

    @Test
    fun aNodeNumberAboveTheSignedRangeIsFiledUnsigned() {
        assertEquals("4294967294-77", key)
    }

    @Test
    fun aWholeFileIsStillThereAfterARestart() {
        attach()
        SambungMediaBoard.put(
            key,
            SambungMedia(kind = 1, codec = 2, received = 2, total = 2, isOutgoing = true, bytes = photo),
        )

        attach() // what a restart does: memory is empty, the folder is read again

        val kept = SambungMediaBoard.media.value[key]
        assertContentEquals(photo, kept?.bytes)
        assertEquals(1, kept?.kind)
        assertEquals(2, kept?.codec)
        assertTrue(kept?.isOutgoing == true)
    }

    @Test
    fun aTransferStillUnderWayIsNotKept() {
        attach()
        SambungMediaBoard.put(key, SambungMedia(kind = 1, codec = 2, received = 1, total = 3, isOutgoing = false))

        attach()

        assertNull(SambungMediaBoard.media.value[key])
    }

    @Test
    fun aFileGoesWhenItsMessageIsDeleted() {
        attach()
        val other = SambungMediaBoard.key(senderNodeNum = 5, messagePacketId = 78)
        SambungMediaBoard.put(key, SambungMedia(1, 2, 1, 1, isOutgoing = false, bytes = photo))
        SambungMediaBoard.put(other, SambungMedia(1, 2, 1, 1, isOutgoing = false, bytes = photo))

        SambungMediaBoard.forgetMessages(listOf(77))
        attach()

        assertNull(SambungMediaBoard.media.value[key])
        assertContentEquals(photo, SambungMediaBoard.media.value[other]?.bytes)
    }
}
