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
import kotlinx.coroutines.flow.update
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
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
 * Where the transfers put their files and the message list finds them. Whole files are also kept in a folder, once one
 * is attached, so a photo or voice clip is still there after the app is restarted. A transfer that is still under way
 * lives in memory only.
 */
object SambungMediaBoard {
    val media = MutableStateFlow<Map<String, SambungMedia>>(emptyMap())

    private var fileSystem: FileSystem = FileSystem.SYSTEM
    private var directory: Path? = null

    /** A file is filed under the node that sent the message and the message's packet id. */
    fun key(senderNodeNum: Int, messagePacketId: Int): String = "${senderNodeNum.toUInt()}-${messagePacketId.toUInt()}"

    /** Starts keeping whole files in [directoryPath] and brings back the ones already there. */
    fun attach(directoryPath: String, files: FileSystem = FileSystem.SYSTEM) {
        val folder = directoryPath.toPath()
        fileSystem = files
        directory = folder
        runCatching {
            files.createDirectories(folder)
            val kept =
                files.list(folder).mapNotNull { path -> read(path)?.let { path.name.substringBefore('.') to it } }
            media.update { kept.toMap() + it }
        }
    }

    /** Shows [item] under [key]; a whole file is written to the folder too. */
    fun put(key: String, item: SambungMedia) {
        media.update { it + (key to item) }
        val folder = directory
        val bytes = item.bytes
        if (folder != null && bytes != null) {
            runCatching { fileSystem.write(folder / fileName(key, item)) { write(bytes) } }
        }
    }

    /** Changes what is shown under [key], if anything is: progress and status, which are not kept on disk. */
    fun update(key: String, change: (SambungMedia) -> SambungMedia) {
        media.update { all -> all[key]?.let { all + (key to change(it)) } ?: all }
    }

    /** A photo or voice clip goes when its message does. */
    fun forgetMessages(packetIds: Collection<Int>) {
        val suffixes = packetIds.map { "-${it.toUInt()}" }
        val gone = media.value.keys.filter { key -> suffixes.any(key::endsWith) }
        if (gone.isNotEmpty()) {
            media.update { it - gone.toSet() }
            val folder = directory ?: return
            runCatching {
                fileSystem.list(folder).filter { it.name.substringBefore('.') in gone }.forEach(fileSystem::delete)
            }
        }
    }

    // "<key>.<kind>.<codec>.<o|i>": everything needed to show the file again is in its name.
    private fun fileName(key: String, item: SambungMedia) =
        "$key.${item.kind}.${item.codec}.${if (item.isOutgoing) OUTGOING else INCOMING}"

    private fun read(path: Path): SambungMedia? {
        val parts = path.name.split('.')
        val kind = parts.getOrNull(1)?.toIntOrNull()
        val codec = parts.getOrNull(2)?.toIntOrNull()
        return if (parts.size == NAME_PARTS && kind != null && codec != null) {
            val bytes = fileSystem.read(path) { readByteArray() }
            SambungMedia(kind, codec, received = 1, total = 1, isOutgoing = parts[3] == OUTGOING, bytes = bytes)
        } else {
            null
        }
    }

    private const val OUTGOING = "o"
    private const val INCOMING = "i"
    private const val NAME_PARTS = 4
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
