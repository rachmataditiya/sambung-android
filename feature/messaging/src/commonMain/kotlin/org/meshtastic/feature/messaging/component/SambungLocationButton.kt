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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.SambungLocationMessage
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.okay
import org.meshtastic.core.resources.sambung_location_missing
import org.meshtastic.core.resources.sambung_share_location
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MyLocation

/**
 * Puts this phone's position into the message field as text any client can read; Sambung draws it as a location card.
 * Falls back to the radio's own position when the phone has none to give.
 *
 * Added for Sambung, November 2026.
 */
@Composable
internal fun SambungLocationButton(textFieldState: TextFieldState, ourNode: Node?, modifier: Modifier = Modifier) {
    val locationService = koinInject<LocationService>()
    val scope = rememberCoroutineScope()
    var isMissing by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            scope.launch {
                val phone = locationService.getCurrentLocation()?.let { it.latitude to it.longitude }
                val radio = ourNode?.validPosition?.let { ourNode.latitude to ourNode.longitude }
                val position = phone ?: radio
                if (position != null) {
                    textFieldState.setTextAndPlaceCursorAtEnd(
                        SambungLocationMessage.text(position.first, position.second),
                    )
                } else {
                    isMissing = true
                }
            }
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = MeshtasticIcons.MyLocation,
            contentDescription = stringResource(Res.string.sambung_share_location),
        )
    }
    if (isMissing) {
        AlertDialog(
            onDismissRequest = { isMissing = false },
            text = { Text(stringResource(Res.string.sambung_location_missing)) },
            confirmButton = { TextButton(onClick = { isMissing = false }) { Text(stringResource(Res.string.okay)) } },
        )
    }
}
