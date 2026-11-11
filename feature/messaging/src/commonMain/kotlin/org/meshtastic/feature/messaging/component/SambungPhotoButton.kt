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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.model.SambungTransferWire
import org.meshtastic.core.repository.SambungTransfers
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.sambung_photo_estimate
import org.meshtastic.core.resources.sambung_photo_large
import org.meshtastic.core.resources.sambung_photo_medium
import org.meshtastic.core.resources.sambung_photo_placeholder
import org.meshtastic.core.resources.sambung_photo_small
import org.meshtastic.core.resources.sambung_photo_too_big
import org.meshtastic.core.resources.sambung_send_photo
import org.meshtastic.core.resources.send
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PhotoCamera
import org.meshtastic.core.ui.util.rememberSambungPhotoPicker
import org.meshtastic.core.ui.util.sambungShrinkPhoto

/** The same three sizes and byte budgets the iPhone app offers. */
private enum class SambungPhotoSize(val label: StringResource, val side: Int, val maxBytes: Int) {
    Small(Res.string.sambung_photo_small, side = 96, maxBytes = 1100),
    Medium(Res.string.sambung_photo_medium, side = 144, maxBytes = 2400),
    Large(Res.string.sambung_photo_large, side = 208, maxBytes = 4600),
}

private const val JPEG_CODEC = 2
private const val SECONDS_PER_PACKET = 4
private val PREVIEW_SIZE = 160.dp

/**
 * Picks a photo, shows it as small as it will travel, and sends it.
 *
 * Added for Sambung, November 2026.
 */
@Composable
internal fun SambungPhotoButton(contactKey: String, ourNodeNum: Int?, modifier: Modifier = Modifier) {
    var picked by remember { mutableStateOf<ByteArray?>(null) }
    val pick = rememberSambungPhotoPicker { picked = it }
    IconButton(onClick = pick, modifier = modifier) {
        Icon(
            imageVector = MeshtasticIcons.PhotoCamera,
            contentDescription = stringResource(Res.string.sambung_send_photo),
        )
    }
    val photo = picked
    if (photo != null && ourNodeNum != null) {
        SambungPhotoDialog(
            photo = photo,
            contactKey = contactKey,
            ourNodeNum = ourNodeNum,
            onDismiss = { picked = null },
        )
    }
}

@Composable
private fun SambungPhotoDialog(photo: ByteArray, contactKey: String, ourNodeNum: Int, onDismiss: () -> Unit) {
    val transfers = koinInject<SambungTransfers>()
    var size by remember { mutableStateOf(SambungPhotoSize.Small) }
    // shortcut: shrinks on the UI thread, a brief stall on a very large picture; move it off when it is noticed.
    val encoded = remember(photo, size) { sambungShrinkPhoto(photo, size.side, size.maxBytes) }
    val placeholder = stringResource(Res.string.sambung_photo_placeholder)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sambung_send_photo)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val preview =
                    remember(encoded) { encoded?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(PREVIEW_SIZE),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SambungPhotoSize.entries.forEach { option ->
                        FilterChip(
                            selected = option == size,
                            onClick = { size = option },
                            label = { Text(stringResource(option.label)) },
                        )
                    }
                }
                val packets = encoded?.let { SambungTransferWire.packets(it, 1, SambungTransferWire.KIND_IMAGE, 0, 0L) }
                Text(
                    if (packets != null) {
                        stringResource(Res.string.sambung_photo_estimate, packets.size * SECONDS_PER_PACKET)
                    } else {
                        stringResource(Res.string.sambung_photo_too_big)
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = encoded != null,
                onClick = {
                    encoded?.let {
                        transfers.send(
                            it,
                            SambungTransferWire.KIND_IMAGE,
                            JPEG_CODEC,
                            placeholder,
                            contactKey,
                            ourNodeNum,
                        )
                    }
                    onDismiss()
                },
            ) {
                Text(stringResource(Res.string.send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
