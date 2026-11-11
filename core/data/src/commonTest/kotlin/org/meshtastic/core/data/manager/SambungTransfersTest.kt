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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.SambungTransferWire
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.SambungMediaBoard
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SambungTransfersTest {
    private val commandSender: CommandSender = mock(MockMode.autofill)
    private val sendMessageUseCase: SendMessageUseCase = mock(MockMode.autofill)
    private val scope = TestScope(StandardTestDispatcher())
    private val transfers =
        SambungTransfersImpl(
            lazy { commandSender },
            lazy { sendMessageUseCase },
            scope.backgroundScope.asServiceScope(),
        )

    private val me = 1
    private val sender = 10
    private val file = ByteArray(500) { (it * 7).toByte() }
    private val packets = SambungTransferWire.packets(file, 5, SambungTransferWire.KIND_IMAGE, 2, 77L)!!

    private fun meshPacket(from: Int, to: Int, payload: ByteArray) = MeshPacket.Builder()
        .also { packet ->
            packet.from = from
            packet.to = to
            packet.decoded =
                Data.Builder()
                    .also { data ->
                        data.portnum = PortNum.PRIVATE_APP
                        data.payload = payload.toByteString()
                    }
                    .build()
        }
        .build()

    private fun deliver(from: Int, to: Int) {
        SambungMediaBoard.media.value = emptyMap()
        packets.forEach { transfers.handlePacket(meshPacket(from, to, SambungTransferWire.encode(it)), myNodeNum = me) }
        scope.runCurrent()
    }

    @Test
    fun aDirectFileIsPutTogetherFiledUnderItsMessageAndAnswered() = scope.runTest {
        deliver(from = sender, to = me)

        val media = SambungMediaBoard.media.value[SambungMediaBoard.key(sender, 77)]
        assertContentEquals(file, media?.bytes)
        assertEquals(packets.size, media?.total)
        // The one packet sent back is "complete".
        verifySuspend(exactly(1)) { commandSender.sendData(any()) }
    }

    @Test
    fun aFileOnAChannelIsKeptButNotAnswered() = scope.runTest {
        deliver(from = sender, to = -1)

        assertContentEquals(file, SambungMediaBoard.media.value[SambungMediaBoard.key(sender, 77)]?.bytes)
        verifySuspend(exactly(0)) { commandSender.sendData(any()) }
    }

    @Test
    fun ourOwnPacketsEchoedBackAreIgnored() = scope.runTest {
        deliver(from = me, to = sender)

        assertNull(SambungMediaBoard.media.value[SambungMediaBoard.key(me, 77)])
    }
}
