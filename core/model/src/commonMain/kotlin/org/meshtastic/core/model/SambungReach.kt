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

import org.meshtastic.core.model.util.onlineTimeThreshold

/**
 * What the radio knows about a node (last heard, hops, MQTT) as a statement about whether a message is likely to get
 * there.
 *
 * Added for Sambung, November 2026.
 */
sealed interface SambungReach {
    /** Heard over radio within the online window, through [hops] relays. */
    data class Reachable(val hops: Int, val lastHeard: Int) : SambungReach

    /** Only heard through an internet (MQTT) bridge. */
    data class ViaInternet(val lastHeard: Int) : SambungReach

    /** Heard before, but not recently. */
    data class Stale(val lastHeard: Int) : SambungReach

    data object NeverHeard : SambungReach

    companion object {
        /** [lastHeard] and [onlineThreshold] are seconds since 1970; anything heard after the threshold is online. */
        fun of(lastHeard: Int, hopsAway: Int, viaMqtt: Boolean, onlineThreshold: Int): SambungReach = when {
            lastHeard <= 0 -> NeverHeard
            lastHeard <= onlineThreshold -> Stale(lastHeard)
            viaMqtt -> ViaInternet(lastHeard)
            else -> Reachable(hops = hopsAway.coerceAtLeast(0), lastHeard = lastHeard)
        }
    }
}

/** Uses the same online window as the node list. */
fun Node.sambungReach(onlineThreshold: Int = onlineTimeThreshold()): SambungReach =
    SambungReach.of(lastHeard, hopsAway, viaMqtt, onlineThreshold)
