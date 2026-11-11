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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.SambungTransferWire
import org.meshtastic.core.model.SambungTransferWire.Packet
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.SambungMedia
import org.meshtastic.core.repository.SambungMediaBoard
import org.meshtastic.core.repository.SambungTransfers
import org.meshtastic.core.repository.SambungVoiceArrival
import org.meshtastic.core.repository.usecase.SendMessageOutcome
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * The same file transfer the iPhone app runs (SambungTransfer.swift): a receiver puts the chunks together, answers a
 * direct transfer with "complete", and asks the sender again for chunks that went missing.
 *
 * Added for Sambung, November 2026.
 */
@Single
class SambungTransfersImpl(
    private val commandSender: Lazy<CommandSender>,
    private val sendMessageUseCase: Lazy<SendMessageUseCase>,
    private val scope: ServiceScope,
) : SambungTransfers {

    private class Incoming(val isDirect: Boolean, val replyChannel: Int) {
        val assembler = SambungTransferWire.Assembler()
        var requestRounds = 0
        var finished = false
        var watchdog: Job? = null
    }

    private class Outgoing(
        val packets: List<Packet>,
        val to: String,
        val toNum: Int?,
        val channel: Int,
        val key: String,
    )

    private val mutex = Mutex()
    private val incoming = mutableMapOf<String, Incoming>()
    private val outgoing = mutableMapOf<Int, Outgoing>()

    override fun handlePacket(packet: MeshPacket, myNodeNum: Int) {
        val wire = packet.decoded?.payload?.toByteArray()?.let(SambungTransferWire::decode)
        // Our own packet echoed back by the mesh is not news.
        if (wire != null && packet.from != myNodeNum) {
            scope.launch { mutex.withLock { process(wire, packet, myNodeNum) } }
        }
    }

    private suspend fun process(wire: Packet, packet: MeshPacket, myNodeNum: Int) {
        when (wire) {
            is Packet.ManifestPacket -> receive(wire, wire.manifest.id, packet, myNodeNum)

            is Packet.Chunk -> receive(wire, wire.id, packet, myNodeNum)

            is Packet.Request -> resend(wire, packet.from)

            is Packet.Complete ->
                if (outgoing[wire.id]?.toNum == packet.from) {
                    outgoing.remove(wire.id)?.let { sent -> update(sent.key) { it.copy(delivered = true) } }
                }
        }
    }

    private suspend fun receive(wire: Packet, id: Int, packet: MeshPacket, myNodeNum: Int) {
        val transferKey = "${packet.from}-$id"
        val entry =
            incoming.getOrPut(transferKey) {
                Incoming(
                    isDirect = packet.to == myNodeNum,
                    replyChannel = if (packet.pki_encrypted) NodeAddress.PKC_CHANNEL_INDEX else packet.channel,
                )
            }
        if (entry.finished) return
        entry.assembler.add(wire)
        entry.assembler.manifest?.let { manifest ->
            val file = entry.assembler.file()
            entry.finished = file != null || entry.assembler.isCorrupt
            SambungMediaBoard.put(
                SambungMediaBoard.key(packet.from, manifest.messageId.toInt()),
                SambungMedia(
                    kind = manifest.kind,
                    codec = manifest.codec,
                    received = entry.assembler.receivedCount,
                    total = manifest.chunkCount,
                    isOutgoing = false,
                    failed = entry.assembler.isCorrupt,
                    bytes = file,
                ),
            )
            if (file != null && manifest.kind == SambungTransferWire.KIND_VOICE) {
                // Push to talk: the conversation on screen plays it at once.
                SambungMediaBoard.arrivedVoice.tryEmit(
                    SambungVoiceArrival(packet.from, entry.isDirect, packet.channel, file),
                )
            }
            if (file != null && entry.isDirect) reply(Packet.Complete(id), packet.from, entry.replyChannel)
        }
        entry.watchdog?.cancel()
        if (!entry.finished) entry.watchdog = scope.launch { watchQuietTransfer(transferKey, id, packet.from) }
    }

    /**
     * Once a transfer has gone quiet, a direct one asks the sender for what is missing; after [MAX_REQUEST_ROUNDS], or
     * straight away on a channel (where there is no one sender to answer without everyone answering), it is given up.
     */
    private suspend fun watchQuietTransfer(transferKey: String, id: Int, from: Int) {
        delay(QUIET_BEFORE_REQUEST)
        mutex.withLock {
            val entry = incoming[transferKey]?.takeIf { !it.finished } ?: return@withLock
            if (entry.isDirect && entry.requestRounds < MAX_REQUEST_ROUNDS) {
                entry.requestRounds++
                reply(Packet.Request(id, entry.assembler.missing.take(MAX_REQUESTED_CHUNKS)), from, entry.replyChannel)
                entry.watchdog = scope.launch { watchQuietTransfer(transferKey, id, from) }
            } else {
                entry.finished = true
                entry.assembler.manifest?.let { manifest ->
                    update(SambungMediaBoard.key(from, manifest.messageId.toInt())) { it.copy(failed = true) }
                }
            }
        }
    }

    private fun resend(request: Packet.Request, requestedBy: Int) {
        val sent = outgoing[request.id]?.takeIf { it.toNum == requestedBy } ?: return
        scope.launch {
            for (index in request.missing) {
                val packet = sent.packets.getOrNull(index) ?: continue
                sendQuietly(SambungTransferWire.encode(packet), sent.to, sent.channel, wantAck = true)
                delay(PACKET_INTERVAL)
            }
        }
    }

    private suspend fun reply(packet: Packet, toNum: Int, channel: Int) =
        sendQuietly(SambungTransferWire.encode(packet), NodeAddress.numToDefaultId(toNum), channel, wantAck = true)

    override fun send(
        file: ByteArray,
        kind: Int,
        codec: Int,
        placeholder: String,
        contactKey: String,
        ourNodeNum: Int,
    ) {
        scope.launch { sendNow(file, kind, codec, placeholder, contactKey, ourNodeNum) }
    }

    /** False when nothing was sent or the sending stopped part way. */
    internal suspend fun sendNow(
        file: ByteArray,
        kind: Int,
        codec: Int,
        placeholder: String,
        contactKey: String,
        ourNodeNum: Int,
    ): Boolean {
        // The text message goes first: it is what other clients show, and its id is what the file is attached to.
        val messageId = (sendMessageUseCase.value(placeholder, contactKey) as? SendMessageOutcome.Queued)?.packetId
        val id = Random.nextInt(1, MAX_TRANSFER_ID)
        val packets = messageId?.let { SambungTransferWire.packets(file, id, kind, codec, it.toUInt().toLong()) }
        if (messageId == null || packets == null) return false

        val parsed = ContactKey(contactKey)
        val to = parsed.addressString
        val isDirect = to != NodeAddress.ID_BROADCAST
        val key = SambungMediaBoard.key(ourNodeNum, messageId)
        val toNum = if (isDirect) to.removePrefix("!").toLongOrNull(HEX)?.toInt() else null
        mutex.withLock { outgoing[id] = Outgoing(packets, to, toNum, parsed.channel, key) }
        // The sender keeps the file too, so its own bubble shows it; "received" then counts packets sent.
        SambungMediaBoard.put(key, SambungMedia(kind, codec, 0, packets.size, true, bytes = file))

        var sentCount = 0
        val sentAll = packets.all { packet ->
            if (sentCount > 0) delay(PACKET_INTERVAL)
            sendQuietly(SambungTransferWire.encode(packet), to, parsed.channel, wantAck = isDirect).also { ok ->
                if (ok) update(key) { it.copy(received = ++sentCount) }
            }
        }
        // The bubble must not sit on "sending" for ever.
        if (!sentAll) update(key) { it.copy(failed = true) }
        return sentAll
    }

    /** False when the radio's queue refused the packet. */
    private suspend fun sendQuietly(bytes: ByteArray, to: String, channel: Int, wantAck: Boolean): Boolean =
        runCatching {
            commandSender.value.sendData(
                DataPacket(
                    to = to,
                    bytes = bytes.toByteString(),
                    dataType = PortNum.PRIVATE_APP.value,
                    channel = channel,
                    wantAck = wantAck,
                ),
            )
        }
            .onFailure {
                if (it is CancellationException) throw it
                Logger.w(it) { "Sambung transfer packet was not sent" }
            }
            .isSuccess

    private fun update(key: String, change: (SambungMedia) -> SambungMedia) = SambungMediaBoard.update(key, change)

    private companion object {
        /** One full packet is about two seconds on air with LongFast, and the radio's send queue is short. */
        val PACKET_INTERVAL = 4.seconds
        val QUIET_BEFORE_REQUEST = 20.seconds
        const val MAX_REQUEST_ROUNDS = 3
        const val MAX_REQUESTED_CHUNKS = 32
        const val MAX_TRANSFER_ID = 0xFFFF
        const val HEX = 16
    }
}
