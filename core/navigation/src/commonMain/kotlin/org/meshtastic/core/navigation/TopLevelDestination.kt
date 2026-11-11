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
package org.meshtastic.core.navigation

import androidx.navigation3.runtime.NavKey
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connect
import org.meshtastic.core.resources.map
import org.meshtastic.core.resources.sambung_tab_chat
import org.meshtastic.core.resources.sambung_tab_device
import org.meshtastic.core.resources.sambung_tab_network

/**
 * Shared top-level destinations for the application shell.
 *
 * Defines the canonical set of destinations and their corresponding labels and routes, ensuring parity between Android
 * and Desktop navigation shells.
 */
enum class TopLevelDestination(val label: StringResource, val route: Route) {
    // Sambung: Chat, Network, Map, Device. Connect stays a destination but is not a tab of its own.
    Messages(Res.string.sambung_tab_chat, ContactsRoute.Contacts),
    Nodes(Res.string.sambung_tab_network, NodesRoute.Nodes),
    Map(Res.string.map, MapRoute.Map()),
    Settings(Res.string.sambung_tab_device, SettingsRoute.Settings()),
    Connect(Res.string.connect, ConnectionsRoute.Connections()),
    ;

    companion object {
        fun fromNavKey(key: NavKey?): TopLevelDestination? = entries.find { dest ->
            key?.let { it::class == dest.route::class } == true
        }
    }
}
