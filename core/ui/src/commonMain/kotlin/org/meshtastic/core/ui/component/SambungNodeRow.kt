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

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.model.hasDisplayName
import org.meshtastic.core.model.sambungReach
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.sambung_external_power
import org.meshtastic.core.resources.sambung_favorite
import org.meshtastic.core.resources.sambung_your_radio
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.MeshtasticIcons

private const val FULL_BATTERY = 100

/**
 * A node in the Network list: who it is and whether a message would get there. The radio's technical detail (role,
 * logs, keys) lives on the node's own screen.
 *
 * Added for Sambung, November 2026.
 */
@Composable
fun SambungNodeRow(
    node: Node,
    isOwnRadio: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .background(if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NodeChip(node = node)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = node.effectiveLongName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (node.isFavorite) {
                    Icon(
                        imageVector = MeshtasticIcons.Favorite,
                        contentDescription = stringResource(Res.string.sambung_favorite),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // For a saved contact, the radio's own name goes underneath.
            if (node.hasDisplayName && node.user.long_name.isNotBlank()) {
                Text(
                    text = node.user.long_name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (isOwnRadio) stringResource(Res.string.sambung_your_radio) else node.sambungReach().text(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        node.batteryLevel
            ?.takeIf { it > 0 }
            ?.let { level ->
                Text(
                    // Above 100 is how the firmware reports "on external power".
                    text = if (level > FULL_BATTERY) stringResource(Res.string.sambung_external_power) else "$level%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

/** The top of a node's screen: the person first, and whether a message would get there, before the radio's detail. */
@Composable
fun SambungNodeHeader(node: Node, isOwnRadio: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NodeChip(node = node)
        Text(
            text = node.effectiveLongName,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (node.hasDisplayName && node.user.long_name.isNotBlank()) {
            Text(
                text = node.user.long_name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (isOwnRadio) stringResource(Res.string.sambung_your_radio) else node.sambungReach().text(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
