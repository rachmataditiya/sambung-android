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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.model.SambungTransferWire
import org.meshtastic.core.repository.SambungTransfers
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.sambung_talk_cancel
import org.meshtastic.core.resources.sambung_talk_hold
import org.meshtastic.core.resources.sambung_talk_release
import org.meshtastic.core.resources.sambung_voice_placeholder
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Mic
import org.meshtastic.core.ui.util.SAMBUNG_VOICE_MAX_SECONDS
import org.meshtastic.core.ui.util.rememberSambungVoiceRecorder
import org.meshtastic.core.ui.util.sambungVoiceSeconds
import kotlin.time.Duration.Companion.seconds

/** Codec2 700C, the codec id the iPhone app puts in the manifest. */
private const val VOICE_CODEC = 1
private const val SECONDS_PER_MINUTE = 60
private val TALK_BUTTON_SIZE = 44.dp
private val CANCEL_DISTANCE = 70.dp

/** What the message field's place shows while the talk button is held. */
data class SambungTalkState(val elapsedSeconds: Int, val willCancel: Boolean)

/** "0:07" */
internal fun sambungDurationText(seconds: Int): String =
    "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"

/**
 * Push to talk: hold to record, let go to send, slide left to cancel. There is no second step, so a voice message is as
 * quick as on a handheld radio. [onTalk] gets what to show while the button is held, and null when it is let go.
 *
 * Added for Sambung, November 2026.
 */
@Composable
internal fun SambungTalkButton(
    contactKey: String,
    ourNodeNum: Int?,
    onTalk: (SambungTalkState?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    var willCancel by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var clipToSend by remember { mutableStateOf<ByteArray?>(null) }
    val currentOnTalk by rememberUpdatedState(onTalk)
    // The clip arrives a moment after letting go; by then willCancel says what the finger decided.
    val recorder = rememberSambungVoiceRecorder { clip -> if (!willCancel) clipToSend = clip }

    // Counts the seconds while held, and ends the recording at the limit.
    LaunchedEffect(isPressed) {
        if (isPressed) {
            elapsed = 0
            while (elapsed < SAMBUNG_VOICE_MAX_SECONDS) {
                delay(1.seconds)
                elapsed++
            }
            recorder.stop()
        }
    }
    LaunchedEffect(isPressed, elapsed, willCancel) {
        currentOnTalk(if (isPressed) SambungTalkState(elapsed, willCancel) else null)
    }

    val label = stringResource(Res.string.sambung_talk_hold)
    Box(
        modifier =
        modifier
            .size(TALK_BUTTON_SIZE)
            .clip(CircleShape)
            .background(
                if (isPressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                val cancelDistance = CANCEL_DISTANCE.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    willCancel = false
                    isPressed = true
                    recorder.start()
                    do {
                        val change = awaitPointerEvent().changes.first()
                        willCancel = down.position.x - change.position.x > cancelDistance
                    } while (change.pressed)
                    isPressed = false
                    recorder.stop()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MeshtasticIcons.Mic,
            contentDescription = null,
            tint = if (isPressed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary,
        )
    }

    val clip = clipToSend
    if (clip != null && ourNodeNum != null) {
        SambungTalkSender(
            clip = clip,
            contactKey = contactKey,
            ourNodeNum = ourNodeNum,
            onFinish = { clipToSend = null },
        )
    }
}

/** Hands a finished clip to the transfer. Only composed when there is one, so the button itself needs no services. */
@Composable
private fun SambungTalkSender(clip: ByteArray, contactKey: String, ourNodeNum: Int, onFinish: () -> Unit) {
    val transfers = koinInject<SambungTransfers>()
    val placeholder =
        stringResource(Res.string.sambung_voice_placeholder, sambungDurationText(sambungVoiceSeconds(clip)))
    val currentOnFinish by rememberUpdatedState(onFinish)
    LaunchedEffect(clip) {
        transfers.send(clip, SambungTransferWire.KIND_VOICE, VOICE_CODEC, placeholder, contactKey, ourNodeNum)
        currentOnFinish()
    }
}

/** Takes the message field's place while talking: how long so far, and what letting go will do. */
@Composable
internal fun SambungTalkStrip(state: SambungTalkState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = sambungDurationText(state.elapsedSeconds), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text =
            stringResource(
                if (state.willCancel) Res.string.sambung_talk_cancel else Res.string.sambung_talk_release,
            ),
            style = MaterialTheme.typography.bodySmall,
            color =
            if (state.willCancel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
