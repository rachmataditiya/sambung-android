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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.model.SambungTransferWire
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.SambungMedia
import org.meshtastic.core.repository.SambungMediaBoard
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.sambung_media_incomplete
import org.meshtastic.core.resources.sambung_media_not_sent
import org.meshtastic.core.resources.sambung_media_receiving
import org.meshtastic.core.resources.sambung_media_sending
import org.meshtastic.core.resources.sambung_media_unreadable
import org.meshtastic.core.resources.sambung_photo
import org.meshtastic.core.resources.sambung_photo_open
import org.meshtastic.core.resources.sambung_voice_message
import org.meshtastic.core.resources.sambung_voice_play
import org.meshtastic.core.resources.sambung_voice_stop
import org.meshtastic.core.resources.save
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.Download
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PlayArrow
import org.meshtastic.core.ui.icon.Stop
import org.meshtastic.core.ui.util.SambungVoicePlayback
import org.meshtastic.core.ui.util.rememberSaveFileLauncher
import org.meshtastic.core.ui.util.sambungPlayVoice
import org.meshtastic.core.ui.util.sambungStopVoice
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
    val key = SambungMediaBoard.key(senderNodeNum, packetId)
    val media = all[key]
    if (media != null) {
        Column(modifier = modifier.padding(bottom = 6.dp)) {
            val isImage = media.kind == SambungTransferWire.KIND_IMAGE
            val bitmap =
                remember(media.bytes) {
                    // A format this phone cannot read (HEIC on an old Android) is not worth a crash.
                    media.bytes?.takeIf { isImage }?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
                }
            if (bitmap != null) SambungPhoto(bitmap = bitmap, media = media)
            media.bytes?.takeIf { !isImage }?.let { SambungVoiceRow(clip = it, key = key, contentColor = contentColor) }
            status(media, isImage = isImage, readable = bitmap != null)?.let {
                Text(text = it, style = MaterialTheme.typography.labelSmall, color = contentColor)
            }
            // How far a transfer that is still under way has come.
            val isUnderWay =
                !media.failed && if (media.isOutgoing) media.received < media.total else media.bytes == null
            if (isUnderWay) {
                LinearProgressIndicator(
                    progress = { media.received.toFloat() / media.total.coerceAtLeast(1) },
                    modifier = Modifier.padding(top = 4.dp).width(BAR_WIDTH),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = TRACK_ALPHA),
                )
            }
        }
    }
}

/** A voice clip: play or stop, a bar that fills while it plays, and its length. */
@Composable
private fun SambungVoiceRow(clip: ByteArray, key: String, contentColor: Color) {
    val playing by SambungVoicePlayback.playing.collectAsState()
    val progress by SambungVoicePlayback.progress.collectAsState()
    val isPlaying = playing == key
    val seconds = sambungVoiceSeconds(clip)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (isPlaying) sambungStopVoice() else sambungPlayVoice(clip, key) }) {
            Icon(
                imageVector = if (isPlaying) MeshtasticIcons.Stop else MeshtasticIcons.PlayArrow,
                contentDescription =
                stringResource(if (isPlaying) Res.string.sambung_voice_stop else Res.string.sambung_voice_play),
                tint = contentColor,
            )
        }
        Column(modifier = Modifier.width(BAR_WIDTH)) {
            LinearProgressIndicator(
                progress = { if (isPlaying) progress else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = contentColor,
                trackColor = contentColor.copy(alpha = TRACK_ALPHA),
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = stringResource(Res.string.sambung_voice_message),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = sambungDurationText(if (isPlaying) (progress * seconds).toInt() else seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                )
            }
        }
    }
}

/** The picture in the bubble; a tap opens it on its own. */
@Composable
private fun SambungPhoto(bitmap: ImageBitmap, media: SambungMedia) {
    var isOpen by remember { mutableStateOf(false) }
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(Res.string.sambung_photo_open),
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(PHOTO_SIZE).clip(RoundedCornerShape(PHOTO_CORNER)).clickable { isOpen = true },
    )
    if (isOpen) SambungPhotoViewer(bitmap = bitmap, media = media, onDismiss = { isOpen = false })
}

/** A photo from the chat filling the screen, with a way to save the file just as it arrived. */
@Composable
private fun SambungPhotoViewer(bitmap: ImageBitmap, media: SambungMedia, onDismiss: () -> Unit) {
    val files = koinInject<FileService>()
    val scope = rememberCoroutineScope()
    val bytes = media.bytes
    val save = rememberSaveFileLauncher { uri ->
        if (bytes != null) scope.launch { files.write(uri) { sink -> sink.write(bytes) } }
    }
    val isHeic = media.codec == CODEC_HEIC
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(Res.string.sambung_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Row(modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)) {
                IconButton(
                    onClick = {
                        if (isHeic) save("sambung-foto.heic", "image/heic") else save("sambung-foto.jpg", "image/jpeg")
                    },
                ) {
                    Icon(MeshtasticIcons.Download, stringResource(Res.string.save), tint = Color.White)
                }
                IconButton(onClick = onDismiss) {
                    Icon(MeshtasticIcons.Close, stringResource(Res.string.close), tint = Color.White)
                }
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

    // The message's own status line already says it arrived.
    media.isOutgoing && media.delivered -> null

    media.isOutgoing && media.received < media.total ->
        stringResource(Res.string.sambung_media_sending, media.received, media.total)

    !media.isOutgoing && media.bytes == null ->
        stringResource(Res.string.sambung_media_receiving, media.received, media.total)

    !isImage -> null

    !readable -> stringResource(Res.string.sambung_media_unreadable)

    else -> null
}

private val PHOTO_SIZE = 208.dp
private val BAR_WIDTH = 150.dp
private const val TRACK_ALPHA = 0.25f

/** The picture codecs on the wire: the iPhone sends HEIC, which travels smaller. */
private const val CODEC_HEIC = 1
private val PHOTO_CORNER = 12.dp
