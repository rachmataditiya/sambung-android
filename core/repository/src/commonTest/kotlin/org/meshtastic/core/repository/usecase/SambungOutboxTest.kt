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

import org.meshtastic.core.repository.usecase.SambungOutbox.Attempts
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SambungOutboxTest {
    private val sent = 1_000_000L
    private val minute = 60_000L

    @Test
    fun retriesOnceTheAudienceIsHeardAfterTheFailedSend() {
        assertTrue(
            SambungOutbox.shouldRetry(sent, Attempts(), audienceHeardAtMillis = sent + minute, now = sent + 3 * minute),
        )
    }

    @Test
    fun staysQuietWhileNobodyWasHeardSinceTheLastTry() {
        assertFalse(
            SambungOutbox.shouldRetry(sent, Attempts(), audienceHeardAtMillis = sent - minute, now = sent + 3 * minute),
        )
        val tried = Attempts(count = 1, lastAtMillis = sent + 5 * minute)
        assertFalse(
            SambungOutbox.shouldRetry(sent, tried, audienceHeardAtMillis = sent + 4 * minute, now = sent + 10 * minute),
        )
    }

    @Test
    fun waitsTwoMinutesBetweenTries() {
        assertFalse(SambungOutbox.shouldRetry(sent, Attempts(), audienceHeardAtMillis = sent + 1, now = sent + minute))
    }

    @Test
    fun givesUpAfterThreeTries() {
        val tried = Attempts(count = SambungOutbox.MAX_ATTEMPTS, lastAtMillis = sent + minute)
        assertFalse(
            SambungOutbox.shouldRetry(sent, tried, audienceHeardAtMillis = sent + 9 * minute, now = sent + 10 * minute),
        )
    }

    @Test
    fun leavesMessagesOlderThanADayAlone() {
        val now = sent + SambungOutbox.MAX_AGE_MILLIS + minute
        assertFalse(SambungOutbox.shouldRetry(sent, Attempts(), audienceHeardAtMillis = now - minute, now = now))
    }
}
