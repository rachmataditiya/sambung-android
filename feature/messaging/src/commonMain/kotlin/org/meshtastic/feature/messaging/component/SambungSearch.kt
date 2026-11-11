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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.SambungSearchHit
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.sambung_search_empty
import org.meshtastic.core.resources.sambung_search_everything
import org.meshtastic.core.resources.sambung_search_hint
import org.meshtastic.core.resources.you
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Search
import kotlin.time.Duration.Companion.milliseconds

private val RESULTS_MAX_HEIGHT = 420.dp
private val TYPING_PAUSE = 250.milliseconds

/**
 * Searches the text of every conversation. [onOpen] gets the key of the conversation a hit is in.
 *
 * Added for Sambung, November 2026.
 */
@Composable
fun SambungSearchButton(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    var isOpen by remember { mutableStateOf(false) }
    IconButton(onClick = { isOpen = true }, modifier = modifier) {
        Icon(
            imageVector = MeshtasticIcons.Search,
            contentDescription = stringResource(Res.string.sambung_search_everything),
        )
    }
    if (isOpen) {
        SambungSearchDialog(
            onPick = {
                isOpen = false
                onOpen(it)
            },
            onDismiss = { isOpen = false },
        )
    }
}

@Composable
private fun SambungSearchDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val packetRepository = koinInject<PacketRepository>()
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SambungSearchHit>>(emptyList()) }
    LaunchedEffect(query) {
        // Wait for a pause in typing before searching.
        delay(TYPING_PAUSE)
        hits = packetRepository.searchAllConversations(query)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sambung_search_everything)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.sambung_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isNotBlank() && hits.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.sambung_search_empty),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = RESULTS_MAX_HEIGHT)) {
                    items(hits) { hit -> SearchHitRow(hit) { onPick(hit.contactKey) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}

@Composable
private fun SearchHitRow(hit: SambungSearchHit, onClick: () -> Unit) {
    val nodeRepository = koinInject<NodeRepository>()
    val sender =
        if (hit.fromId == null || hit.fromId == NodeAddress.ID_LOCAL) {
            stringResource(Res.string.you)
        } else {
            nodeRepository.getNode(hit.fromId.orEmpty()).effectiveLongName
        }
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(text = sender, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = hit.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
