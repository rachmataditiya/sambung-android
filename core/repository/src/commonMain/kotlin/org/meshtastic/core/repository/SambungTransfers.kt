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

import kotlinx.coroutines.flow.MutableStateFlow
import org.meshtastic.proto.MeshPacket

/**
 * A photo or voice clip that belongs to a text message: how much of it is here, and the file once it is whole.
 *
 * Added for Sambung, November 2026.
 */
@Suppress("ArrayInDataClass")
data class SambungMedia(
    val kind: Int,
    val codec: Int,
    /** Packets received, or for an outgoing file packets sent. */
    val received: Int,
    val total: Int,
    val isOutgoing: Boolean,
    val failed: Boolean = false,
    val delivered: Boolean = false,
    val bytes: ByteArray? = null,
)

/**
 * Where the transfers put their files and the message list finds them.
 *
 * shortcut: one in-memory map for the whole app, so a file is gone after the app is restarted and the message shows its
 * plain text again; store the files on disk when photos should survive a restart.
 */
object SambungMediaBoard {
    val media = MutableStateFlow<Map<String, SambungMedia>>(emptyMap())

    /** A file is filed under the node that sent the message and the message's packet id. */
    fun key(senderNodeNum: Int, messagePacketId: Int): String = "${senderNodeNum.toUInt()}-${messagePacketId.toUInt()}"
}

/** Sends and receives files over the mesh with the packets in `SambungTransferWire`. */
interface SambungTransfers {
    /** Called for every packet on the private application port. */
    fun handlePacket(packet: MeshPacket, myNodeNum: Int)

    /**
     * Sends [placeholder] as an ordinary text message to [contactKey], then [file] in packets attached to it. Returns
     * at once; the transfer runs on for a minute or so and its progress shows on the message.
     */
    fun send(file: ByteArray, kind: Int, codec: Int, placeholder: String, contactKey: String, ourNodeNum: Int)
}
