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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.model.SambungTransferWire
import org.meshtastic.core.repository.SambungTransfers
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.sambung_send_voice
import org.meshtastic.core.resources.sambung_voice_failed
import org.meshtastic.core.resources.sambung_voice_hint_idle
import org.meshtastic.core.resources.sambung_voice_hint_recorded
import org.meshtastic.core.resources.sambung_voice_hint_recording
import org.meshtastic.core.resources.sambung_voice_listen
import org.meshtastic.core.resources.sambung_voice_placeholder
import org.meshtastic.core.resources.sambung_voice_record
import org.meshtastic.core.resources.sambung_voice_redo
import org.meshtastic.core.resources.sambung_voice_stop
import org.meshtastic.core.resources.send
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Mic
import org.meshtastic.core.ui.util.SAMBUNG_VOICE_MAX_SECONDS
import org.meshtastic.core.ui.util.rememberSambungVoiceRecorder
import org.meshtastic.core.ui.util.sambungPlayVoice
import org.meshtastic.core.ui.util.sambungVoiceSeconds
import kotlin.time.Duration.Companion.seconds

private enum class VoiceStep {
    Idle,
    Recording,
    Recorded,
    Failed,
}

/** Codec2 700C, the codec id the iPhone app puts in the manifest. */
private const val VOICE_CODEC = 1
private const val SECONDS_PER_PACKET = 4
private const val SECONDS_PER_MINUTE = 60

/** "0:07" */
internal fun sambungDurationText(seconds: Int): String =
    "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"

/**
 * Records up to ten seconds, lets you hear what the other side will hear, and sends it.
 *
 * Added for Sambung, November 2026.
 */
@Composable
internal fun SambungVoiceButton(contactKey: String, ourNodeNum: Int?, modifier: Modifier = Modifier) {
    var isOpen by remember { mutableStateOf(false) }
    IconButton(onClick = { isOpen = true }, modifier = modifier) {
        Icon(imageVector = MeshtasticIcons.Mic, contentDescription = stringResource(Res.string.sambung_send_voice))
    }
    if (isOpen && ourNodeNum != null) {
        SambungVoiceDialog(contactKey = contactKey, ourNodeNum = ourNodeNum, onDismiss = { isOpen = false })
    }
}

@Composable
private fun SambungVoiceDialog(contactKey: String, ourNodeNum: Int, onDismiss: () -> Unit) {
    val transfers = koinInject<SambungTransfers>()
    var step by remember { mutableStateOf(VoiceStep.Idle) }
    var clip by remember { mutableStateOf<ByteArray?>(null) }
    var elapsed by remember { mutableIntStateOf(0) }
    val recorder = rememberSambungVoiceRecorder { recorded ->
        clip = recorded
        step = if (recorded != null) VoiceStep.Recorded else VoiceStep.Failed
    }
    // Counts the seconds while recording and stops at the limit.
    LaunchedEffect(step) {
        if (step == VoiceStep.Recording) {
            elapsed = 0
            while (elapsed < SAMBUNG_VOICE_MAX_SECONDS) {
                delay(1.seconds)
                elapsed++
            }
            recorder.stop()
        }
    }
    val seconds = clip?.let(::sambungVoiceSeconds) ?: elapsed
    val placeholder = stringResource(Res.string.sambung_voice_placeholder, sambungDurationText(seconds))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sambung_send_voice)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = sambungDurationText(seconds), style = MaterialTheme.typography.displaySmall)
                VoiceControls(
                    step = step,
                    onRecord = {
                        step = VoiceStep.Recording
                        recorder.start()
                    },
                    onStop = recorder.stop,
                    onListen = { clip?.let(::sambungPlayVoice) },
                    onRedo = {
                        clip = null
                        elapsed = 0
                        step = VoiceStep.Idle
                    },
                )
                Text(voiceHint(step, clip))
            }
        },
        confirmButton = {
            TextButton(
                enabled = step == VoiceStep.Recorded,
                onClick = {
                    clip?.let {
                        transfers.send(
                            it,
                            SambungTransferWire.KIND_VOICE,
                            VOICE_CODEC,
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

@Composable
private fun VoiceControls(
    step: VoiceStep,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onListen: () -> Unit,
    onRedo: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (step) {
            VoiceStep.Idle,
            VoiceStep.Failed,
            ->
                FilledTonalButton(onClick = onRecord) { Text(stringResource(Res.string.sambung_voice_record)) }

            VoiceStep.Recording ->
                FilledTonalButton(onClick = onStop) { Text(stringResource(Res.string.sambung_voice_stop)) }

            VoiceStep.Recorded -> {
                FilledTonalButton(onClick = onListen) { Text(stringResource(Res.string.sambung_voice_listen)) }
                TextButton(onClick = onRedo) { Text(stringResource(Res.string.sambung_voice_redo)) }
            }
        }
    }
}

@Composable
private fun voiceHint(step: VoiceStep, clip: ByteArray?): String = when (step) {
    VoiceStep.Idle -> stringResource(Res.string.sambung_voice_hint_idle, SAMBUNG_VOICE_MAX_SECONDS)

    VoiceStep.Recording -> stringResource(Res.string.sambung_voice_hint_recording)

    VoiceStep.Failed -> stringResource(Res.string.sambung_voice_failed)

    VoiceStep.Recorded -> {
        val packets = clip?.let { SambungTransferWire.packets(it, 1, SambungTransferWire.KIND_VOICE, 0, 0L) }
        stringResource(Res.string.sambung_voice_hint_recorded, (packets?.size ?: 0) * SECONDS_PER_PACKET)
    }
}
