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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.hasDisplayName
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.SambungSos
import org.meshtastic.core.repository.SambungSosPrefs
import org.meshtastic.core.repository.SambungSosSettings
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.sambung_sos_active
import org.meshtastic.core.resources.sambung_sos_default_text
import org.meshtastic.core.resources.sambung_sos_explain
import org.meshtastic.core.resources.sambung_sos_hold
import org.meshtastic.core.resources.sambung_sos_interval
import org.meshtastic.core.resources.sambung_sos_primary_channel
import org.meshtastic.core.resources.sambung_sos_send_to
import org.meshtastic.core.resources.sambung_sos_stop
import org.meshtastic.core.resources.sambung_sos_text
import org.meshtastic.core.resources.sambung_sos_title

private const val HOLD_MILLIS = 2000L
private const val SECONDS_PER_MINUTE = 60
private const val MAX_INTERVAL_MINUTES = 30
private val HOLD_BUTTON_SIZE = 140.dp

/**
 * The SOS button in the conversations bar. It only opens the dialog: the call itself needs a two-second hold.
 *
 * Added for Sambung, November 2026.
 */
@Composable
fun SambungSosButton(modifier: Modifier = Modifier) {
    var isOpen by remember { mutableStateOf(false) }
    TextButton(onClick = { isOpen = true }, modifier = modifier) {
        Text(text = "SOS", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
    }
    if (isOpen) SambungSosDialog(onDismiss = { isOpen = false })
}

/** Shown above every screen while an SOS is running, with the way to stop it. */
@Composable
fun SambungSosBar(modifier: Modifier = Modifier) {
    val sos = koinInject<SambungSos>()
    val state by sos.state.collectAsState()
    if (state.isActive) {
        Row(
            modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.error)
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.sambung_sos_active, state.sentCount),
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = sos::stop) {
                Text(text = stringResource(Res.string.sambung_sos_stop), color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}

@Composable
private fun SambungSosDialog(onDismiss: () -> Unit) {
    val sos = koinInject<SambungSos>()
    val prefs = koinInject<SambungSosPrefs>()
    val state by sos.state.collectAsState()
    val settings by prefs.settings.collectAsState()
    val defaultText = stringResource(Res.string.sambung_sos_default_text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sambung_sos_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.isActive) {
                    Text(
                        text = stringResource(Res.string.sambung_sos_active, state.sentCount),
                        style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    SosHoldButton(onSend = { sos.start(defaultText) })
                    Text(stringResource(Res.string.sambung_sos_explain, settings.intervalSeconds / SECONDS_PER_MINUTE))
                    SosSettings(settings = settings, defaultText = defaultText, onChange = prefs::setSettings)
                }
            }
        },
        confirmButton = {
            if (state.isActive) {
                TextButton(onClick = sos::stop) { Text(stringResource(Res.string.sambung_sos_stop)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
            }
        },
    )
}

/** Two seconds of holding, so a pocket cannot send it. */
@Composable
private fun SosHoldButton(onSend: () -> Unit) {
    var isHolding by remember { mutableStateOf(false) }
    Box(
        modifier =
        Modifier.size(HOLD_BUTTON_SIZE).clip(CircleShape).background(MaterialTheme.colorScheme.error).pointerInput(
            Unit,
        ) {
            detectTapGestures(
                onPress = {
                    isHolding = true
                    // Null means the finger stayed down for the whole two seconds.
                    val released = withTimeoutOrNull(HOLD_MILLIS) { tryAwaitRelease() }
                    isHolding = false
                    if (released == null) onSend()
                },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        if (isHolding) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(HOLD_BUTTON_SIZE - 16.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SOS",
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = stringResource(Res.string.sambung_sos_hold),
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SosSettings(settings: SambungSosSettings, defaultText: String, onChange: (SambungSosSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Typed text is kept here and written through: the stored value arrives back a moment later.
        var text by remember { mutableStateOf(settings.text) }
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(settings.copy(text = it))
            },
            label = { Text(stringResource(Res.string.sambung_sos_text)) },
            placeholder = { Text(defaultText) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(text = stringResource(Res.string.sambung_sos_send_to), style = MaterialTheme.typography.labelLarge)
        sosDestinations().forEach { (key, title) ->
            FilterChip(
                selected = settings.contactKey == key,
                onClick = { onChange(settings.copy(contactKey = key)) },
                label = { Text(title) },
            )
        }
        val minutes = settings.intervalSeconds / SECONDS_PER_MINUTE
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = minutes > 1,
                onClick = { onChange(settings.copy(intervalSeconds = (minutes - 1) * SECONDS_PER_MINUTE)) },
            ) {
                Text("−")
            }
            Text(stringResource(Res.string.sambung_sos_interval, minutes))
            TextButton(
                enabled = minutes < MAX_INTERVAL_MINUTES,
                onClick = { onChange(settings.copy(intervalSeconds = (minutes + 1) * SECONDS_PER_MINUTE)) },
            ) {
                Text("+")
            }
        }
    }
}

/** The conversations an SOS can go to: the radio's channels, then the saved contacts. */
@Composable
private fun sosDestinations(): List<Pair<String, String>> {
    val channels by koinInject<RadioConfigRepository>().channelSetFlow.collectAsState(initial = null)
    val nodes by koinInject<NodeRepository>().nodeDBbyNum.collectAsState()
    val ourNode by koinInject<NodeRepository>().ourNodeInfo.collectAsState()
    val primary = SambungSosSettings.PRIMARY_CHANNEL to stringResource(Res.string.sambung_sos_primary_channel)
    val others =
        channels?.settings.orEmpty().indices.drop(1).mapNotNull { index ->
            channels?.getChannel(index)?.name?.takeIf(String::isNotBlank)?.let {
                "$index${NodeAddress.ID_BROADCAST}" to it
            }
        }
    val contacts =
        nodes.values
            .filter { it.hasDisplayName && it.num != ourNode?.num }
            .sortedBy { it.displayName }
            .map { directKey(it, ourNode) to it.displayName }
    return listOf(primary) + others + contacts
}

/** The same key the node list opens a direct conversation with. */
private fun directKey(node: Node, ourNode: Node?): String {
    val channel = if (ourNode?.hasPKC == true && node.hasPKC) NodeAddress.PKC_CHANNEL_INDEX else node.channel
    return "$channel${node.user.id}"
}
