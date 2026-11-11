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
import kotlin.test.assertNull

class SambungLocationMessageTest {
    @Test
    fun readsThePositionTheIphoneAppSends() {
        assertEquals(-6.2088 to 106.8456, SambungLocationMessage.coordinate("📍 Lokasi saya: -6.20880, 106.84560"))
    }

    @Test
    fun readsItWhateverTheWordsInBetween() {
        assertEquals(1.5 to -73.25, SambungLocationMessage.coordinate("📍 My location: 1.500, -73.250"))
    }

    @Test
    fun ordinaryTextIsNotAPosition() {
        assertNull(SambungLocationMessage.coordinate("see you at 6.20, 106.84"))
        assertNull(SambungLocationMessage.coordinate("📍 here"))
    }

    @Test
    fun aLatitudeOffTheGlobeIsRefused() {
        assertNull(SambungLocationMessage.coordinate("📍 Lokasi saya: 96.00000, 106.84560"))
    }
}
