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
package org.meshtastic.feature.messaging.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.SambungLocationMessage
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.sambung_compass_e
import org.meshtastic.core.resources.sambung_compass_n
import org.meshtastic.core.resources.sambung_compass_ne
import org.meshtastic.core.resources.sambung_compass_nw
import org.meshtastic.core.resources.sambung_compass_s
import org.meshtastic.core.resources.sambung_compass_se
import org.meshtastic.core.resources.sambung_compass_sw
import org.meshtastic.core.resources.sambung_compass_w
import org.meshtastic.core.resources.sambung_location_distance
import org.meshtastic.core.resources.sambung_location_here
import org.meshtastic.core.resources.sambung_location_open
import org.meshtastic.core.resources.sambung_location_shared
import org.meshtastic.core.ui.icon.LocationOn
import org.meshtastic.core.ui.icon.MeshtasticIcons

/**
 * A position that arrived as text, shown as a card that opens the phone's map app.
 *
 * Added for Sambung, November 2026.
 */
@Composable
internal fun SambungLocationCard(
    latitude: Double,
    longitude: Double,
    contentColor: Color,
    modifier: Modifier = Modifier,
    /** Where this radio is, to say how far away and which way the shared position lies. */
    origin: Pair<Double, Double>? = null,
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = MeshtasticIcons.LocationOn, contentDescription = null, tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(Res.string.sambung_location_shared),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                )
                if (origin != null) {
                    val meters = SambungLocationMessage.distanceMeters(origin, latitude to longitude)
                    val compass = COMPASS_NAMES[SambungLocationMessage.compassPoint(origin, latitude to longitude)]
                    Text(
                        text =
                        if (meters < HERE_METERS) {
                            stringResource(Res.string.sambung_location_here)
                        } else {
                            stringResource(
                                Res.string.sambung_location_distance,
                                SambungLocationMessage.distanceLabel(meters),
                                stringResource(compass),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                }
                Text(text = "$latitude, $longitude", style = MaterialTheme.typography.bodySmall, color = contentColor)
            }
        }
        TextButton(
            onClick = {
                // No map app installed is not worth a crash.
                runCatching { uriHandler.openUri("geo:$latitude,$longitude?q=$latitude,$longitude") }
            },
        ) {
            Text(text = stringResource(Res.string.sambung_location_open), color = contentColor)
        }
    }
}

private const val HERE_METERS = 10.0
private val COMPASS_NAMES =
    listOf(
        Res.string.sambung_compass_n,
        Res.string.sambung_compass_ne,
        Res.string.sambung_compass_e,
        Res.string.sambung_compass_se,
        Res.string.sambung_compass_s,
        Res.string.sambung_compass_sw,
        Res.string.sambung_compass_w,
        Res.string.sambung_compass_nw,
    )
