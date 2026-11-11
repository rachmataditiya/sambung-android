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

import org.meshtastic.core.model.SambungLocationMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SambungSosTest {
    @Test
    fun theCallIsTheSameTextTheIphoneAppSends() {
        val message = SambungSos.message("Budi", " Butuh bantuan segera. ", -6.2088 to 106.8456)
        assertEquals("\u0007🆘 SOS Budi: Butuh bantuan segera.\n📍 Lokasi saya: -6.20880, 106.84560", message)
        // Sambung draws the position as a location card.
        assertEquals(-6.2088 to 106.8456, SambungLocationMessage.coordinate(message))
    }

    @Test
    fun withoutAPositionTheCallStillGoesOut() {
        assertEquals("\u0007🆘 SOS Budi: Tolong", SambungSos.message("Budi", "Tolong", null))
    }

    @Test
    fun repeatsAreNeverFasterThanOnceAMinuteAndStopAfterAnHour() {
        assertEquals(60, SambungSos.clampedInterval(5))
        assertEquals(300, SambungSos.clampedInterval(300))
        assertFalse(SambungSos.isOver(startedAtMillis = 1_000, now = 1_000 + SambungSos.MAX_DURATION_MILLIS - 1))
        assertTrue(SambungSos.isOver(startedAtMillis = 1_000, now = 1_000 + SambungSos.MAX_DURATION_MILLIS))
    }

    @Test
    fun settingsSurviveBeingStoredIncludingATextWithBars() {
        val settings = SambungSosSettings(contactKey = "8!0abc1234", text = "Tolong | di pos 3", intervalSeconds = 300)
        assertEquals(settings, SambungSosSettings.decode(settings.encode()))
    }

    @Test
    fun anUnreadableValueFallsBackToThePrimaryChannelEveryTwoMinutes() {
        assertEquals(SambungSosSettings(), SambungSosSettings.decode(null))
        assertEquals(SambungSosSettings(), SambungSosSettings.decode("nonsense"))
        assertEquals(60, SambungSosSettings.decode("5|0^all|x").intervalSeconds)
    }
}
