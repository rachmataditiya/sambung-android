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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.SambungTransferWire
import org.meshtastic.core.repository.SambungMedia
import org.meshtastic.core.repository.SambungMediaBoard
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.sambung_media_delivered
import org.meshtastic.core.resources.sambung_media_incomplete
import org.meshtastic.core.resources.sambung_media_not_sent
import org.meshtastic.core.resources.sambung_media_receiving
import org.meshtastic.core.resources.sambung_media_sending
import org.meshtastic.core.resources.sambung_media_unreadable
import org.meshtastic.core.resources.sambung_photo
import org.meshtastic.core.resources.sambung_voice_message
import org.meshtastic.core.resources.sambung_voice_play
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PlayArrow
import org.meshtastic.core.ui.util.sambungPlayVoice
import org.meshtastic.core.ui.util.sambungVoiceSeconds

/** True when a photo or voice clip stands in for this message's text. */
@Composable
internal fun sambungHasMedia(senderNodeNum: Int, packetId: Int): Boolean {
    val all by SambungMediaBoard.media.collectAsState()
    return all.containsKey(SambungMediaBoard.key(senderNodeNum, packetId))
}

/**
 * The photo (or voice clip) that travelled with a message, in place of its text: the picture once it is whole, and how
 * far the transfer is until then.
 *
 * Added for Sambung, November 2026.
 */
@Composable
internal fun SambungMediaView(senderNodeNum: Int, packetId: Int, contentColor: Color, modifier: Modifier = Modifier) {
    // Plain collectAsState: the lifecycle-aware one needs a main dispatcher, which the row's host tests lack.
    val all by SambungMediaBoard.media.collectAsState()
    val media = all[SambungMediaBoard.key(senderNodeNum, packetId)]
    if (media != null) {
        Column(modifier = modifier.padding(bottom = 6.dp)) {
            val isImage = media.kind == SambungTransferWire.KIND_IMAGE
            val bitmap =
                remember(media.bytes) {
                    // A format this phone cannot read (HEIC on an old Android) is not worth a crash.
                    media.bytes?.takeIf { isImage }?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
                }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(Res.string.sambung_photo),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(PHOTO_SIZE).clip(RoundedCornerShape(PHOTO_CORNER)),
                )
            }
            val voice = media.bytes?.takeIf { !isImage }
            if (voice != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { sambungPlayVoice(voice) }) {
                        Icon(
                            imageVector = MeshtasticIcons.PlayArrow,
                            contentDescription = stringResource(Res.string.sambung_voice_play),
                            tint = contentColor,
                        )
                    }
                    Text(
                        text =
                        stringResource(Res.string.sambung_voice_message) +
                            " " +
                            sambungDurationText(sambungVoiceSeconds(voice)),
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor,
                    )
                }
            }
            status(media, isImage = isImage, readable = bitmap != null)?.let {
                Text(text = it, style = MaterialTheme.typography.labelSmall, color = contentColor)
            }
        }
    }
}

@Composable
private fun status(media: SambungMedia, isImage: Boolean, readable: Boolean): String? = when {
    media.failed ->
        stringResource(
            if (media.isOutgoing) Res.string.sambung_media_not_sent else Res.string.sambung_media_incomplete,
        )

    media.isOutgoing && media.delivered -> stringResource(Res.string.sambung_media_delivered)

    media.isOutgoing && media.received < media.total ->
        stringResource(Res.string.sambung_media_sending, media.received, media.total)

    !media.isOutgoing && media.bytes == null ->
        stringResource(Res.string.sambung_media_receiving, media.received, media.total)

    !isImage -> null

    !readable -> stringResource(Res.string.sambung_media_unreadable)

    else -> null
}

private val PHOTO_SIZE = 208.dp
private val PHOTO_CORNER = 12.dp
