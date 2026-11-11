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
package org.meshtastic.core.ui.component

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.SambungReach
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.sambung_ago_days
import org.meshtastic.core.resources.sambung_ago_hours
import org.meshtastic.core.resources.sambung_ago_minutes
import org.meshtastic.core.resources.sambung_ago_now
import org.meshtastic.core.resources.sambung_reach_direct
import org.meshtastic.core.resources.sambung_reach_internet
import org.meshtastic.core.resources.sambung_reach_never
import org.meshtastic.core.resources.sambung_reach_relays
import org.meshtastic.core.resources.sambung_reach_stale

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
private const val SECONDS_PER_DAY = 86_400

/**
 * A [SambungReach] in a few words: "Heard directly, 5 min ago".
 *
 * Added for Sambung, November 2026.
 */
@Composable
fun SambungReach.text(now: Long = nowSeconds): String = when (this) {
    SambungReach.NeverHeard -> stringResource(Res.string.sambung_reach_never)

    is SambungReach.Stale -> stringResource(Res.string.sambung_reach_stale, ago(lastHeard, now))

    is SambungReach.ViaInternet -> stringResource(Res.string.sambung_reach_internet, ago(lastHeard, now))

    is SambungReach.Reachable ->
        if (hops == 0) {
            stringResource(Res.string.sambung_reach_direct, ago(lastHeard, now))
        } else {
            stringResource(Res.string.sambung_reach_relays, hops, ago(lastHeard, now))
        }
}

@Composable
private fun ago(lastHeard: Int, now: Long): String {
    val seconds = (now - lastHeard).coerceAtLeast(0)
    return when {
        seconds < SECONDS_PER_MINUTE -> stringResource(Res.string.sambung_ago_now)

        seconds < SECONDS_PER_HOUR ->
            stringResource(Res.string.sambung_ago_minutes, (seconds / SECONDS_PER_MINUTE).toInt())

        seconds < SECONDS_PER_DAY -> stringResource(Res.string.sambung_ago_hours, (seconds / SECONDS_PER_HOUR).toInt())

        else -> stringResource(Res.string.sambung_ago_days, (seconds / SECONDS_PER_DAY).toInt())
    }
}
