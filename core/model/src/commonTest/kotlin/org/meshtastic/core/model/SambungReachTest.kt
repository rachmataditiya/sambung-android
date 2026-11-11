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

import kotlin.test.Test
import kotlin.test.assertEquals

class SambungReachTest {
    private val threshold = 1_000

    @Test
    fun heardRecentlyOverRadioIsReachableWithItsHops() {
        assertEquals(SambungReach.Reachable(hops = 2, lastHeard = 1_500), SambungReach.of(1_500, 2, false, threshold))
    }

    @Test
    fun anUnknownHopCountReadsAsDirect() {
        assertEquals(SambungReach.Reachable(hops = 0, lastHeard = 1_500), SambungReach.of(1_500, -1, false, threshold))
    }

    @Test
    fun heardRecentlyOnlyThroughMqttIsViaInternet() {
        assertEquals(SambungReach.ViaInternet(1_500), SambungReach.of(1_500, 0, true, threshold))
    }

    @Test
    fun pastTheOnlineWindowItIsStaleWhateverTheHops() {
        assertEquals(SambungReach.Stale(900), SambungReach.of(900, 0, false, threshold))
        assertEquals(SambungReach.Stale(1_000), SambungReach.of(1_000, 3, true, threshold))
    }

    @Test
    fun noLastHeardTimeMeansNeverHeard() {
        assertEquals(SambungReach.NeverHeard, SambungReach.of(0, 0, false, threshold))
    }
}
