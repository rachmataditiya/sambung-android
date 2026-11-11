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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.model.hasDisplayName
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.sambung_contacts
import org.meshtastic.core.resources.sambung_new_message
import org.meshtastic.core.resources.sambung_no_people
import org.meshtastic.core.resources.sambung_other_people
import org.meshtastic.core.resources.sambung_search_names
import org.meshtastic.core.ui.component.SambungNodeRow
import org.meshtastic.core.ui.icon.Add
import org.meshtastic.core.ui.icon.MeshtasticIcons

private val LIST_MAX_HEIGHT = 420.dp

/**
 * Starts a direct conversation: pick a person, saved contacts first. [onOpen] gets the conversation's key.
 *
 * Added for Sambung, November 2026.
 */
@Composable
fun SambungNewMessageButton(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    var isOpen by remember { mutableStateOf(false) }
    IconButton(onClick = { isOpen = true }, modifier = modifier) {
        Icon(imageVector = MeshtasticIcons.Add, contentDescription = stringResource(Res.string.sambung_new_message))
    }
    if (isOpen) {
        SambungNewMessageDialog(
            onPick = {
                isOpen = false
                onOpen(it)
            },
            onDismiss = { isOpen = false },
        )
    }
}

@Composable
private fun SambungNewMessageDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val nodeRepository = koinInject<NodeRepository>()
    val nodes by nodeRepository.nodeDBbyNum.collectAsState()
    val ourNode by nodeRepository.ourNodeInfo.collectAsState()
    var query by remember { mutableStateOf("") }
    val people =
        nodes.values
            .filter { it.num != ourNode?.num && it.matches(query) }
            .sortedBy { it.effectiveLongName.lowercase() }
    val (contacts, others) = people.partition { it.hasDisplayName }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sambung_new_message)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.sambung_search_names)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (people.isEmpty()) {
                    Text(text = stringResource(Res.string.sambung_no_people), modifier = Modifier.padding(top = 16.dp))
                }
                LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
                    if (contacts.isNotEmpty()) item { SectionLabel(stringResource(Res.string.sambung_contacts)) }
                    items(contacts, key = { it.num }) { node -> PersonRow(node) { onPick(directKey(node, ourNode)) } }
                    if (others.isNotEmpty()) item { SectionLabel(stringResource(Res.string.sambung_other_people)) }
                    items(others, key = { it.num }) { node -> PersonRow(node) { onPick(directKey(node, ourNode)) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

private fun Node.matches(query: String): Boolean = query.isBlank() ||
    effectiveLongName.contains(query.trim(), ignoreCase = true) ||
    user.long_name.contains(query.trim(), ignoreCase = true) ||
    user.short_name.contains(query.trim(), ignoreCase = true)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PersonRow(node: Node, onClick: () -> Unit) {
    SambungNodeRow(
        node = node,
        isOwnRadio = false,
        isActive = false,
        onClick = onClick,
        onLongClick = null,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
