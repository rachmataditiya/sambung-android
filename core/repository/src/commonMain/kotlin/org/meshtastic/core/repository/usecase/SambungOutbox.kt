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
package org.meshtastic.core.repository.usecase

import co.touchlab.kermit.Logger
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.isMessageStatusRetryable
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository

/**
 * Sends a failed text message again once the people it was for are back in range: the recipient of a direct message, or
 * any other node for a channel.
 *
 * Added for Sambung, November 2026.
 */
class SambungOutbox(
    private val packetRepository: PacketRepository,
    private val nodeRepository: NodeRepository,
    private val sendMessageUseCase: SendMessageUseCase,
) {
    /** How often a message was retried, and when last. */
    data class Attempts(val count: Int = 0, val lastAtMillis: Long = 0)

    // shortcut: kept in memory, so the count starts again when the app is restarted; persist it if messages get
    // retried more than intended.
    private val attempts = mutableMapOf<Long, Attempts>()

    /** One pass over the failed messages. Call it about once a minute while the app is up. */
    suspend fun runOnce(now: Long = nowMillis) {
        val failed = packetRepository.getFailedOutgoingMessages()
        if (failed.isEmpty()) return
        val nodes = nodeRepository.nodeDBbyNum.value.values
        val ourNum = nodeRepository.ourNodeInfo.value?.num
        for (message in failed) {
            val tried = attempts[message.uuid] ?: Attempts()
            val retry =
                isMessageStatusRetryable(MessageStatus.ERROR, message.routingError) &&
                    shouldRetry(
                        sentAtMillis = message.sentAtMillis,
                        attempts = tried,
                        audienceHeardAtMillis = audienceHeardAtMillis(ContactKey(message.contactKey), nodes, ourNum),
                        now = now,
                    )
            if (!retry) continue
            attempts[message.uuid] = Attempts(count = tried.count + 1, lastAtMillis = now)
            Logger.i { "Sambung outbox: retrying message ${message.uuid}, attempt ${tried.count + 1}" }
            sendMessageUseCase.resend(message.uuid, message.contactKey)
        }
    }

    private fun audienceHeardAtMillis(key: ContactKey, nodes: Collection<Node>, ourNum: Int?): Long {
        val lastHeardSeconds =
            if (key.addressString == NodeAddress.ID_BROADCAST) {
                nodes.filter { it.num != ourNum }.maxOfOrNull { it.lastHeard } ?: 0
            } else {
                nodeRepository.getNode(key.addressString).lastHeard
            }
        return lastHeardSeconds * MILLIS_PER_SECOND
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val MIN_GAP_MILLIS = 120_000L
        const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
        private const val MILLIS_PER_SECOND = 1000L

        /**
         * Retry only a message from the last day, at most [MAX_ATTEMPTS] times, at least two minutes apart, and only
         * when its audience was heard after the last try: sending into silence just spends airtime.
         */
        fun shouldRetry(sentAtMillis: Long, attempts: Attempts, audienceHeardAtMillis: Long, now: Long): Boolean {
            val lastTry = maxOf(sentAtMillis, attempts.lastAtMillis)
            return attempts.count < MAX_ATTEMPTS &&
                now - sentAtMillis <= MAX_AGE_MILLIS &&
                now - lastTry >= MIN_GAP_MILLIS &&
                audienceHeardAtMillis > lastTry
        }
    }
}
