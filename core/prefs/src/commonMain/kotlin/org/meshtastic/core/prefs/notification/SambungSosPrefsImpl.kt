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
package org.meshtastic.core.prefs.notification

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.prefs.di.UiDataStore
import org.meshtastic.core.repository.SambungSosPrefs
import org.meshtastic.core.repository.SambungSosSettings

/** Added for Sambung, November 2026. */
@Single
class SambungSosPrefsImpl(private val dataStore: UiDataStore, private val scope: ApplicationCoroutineScope) :
    SambungSosPrefs {

    override val settings: StateFlow<SambungSosSettings> =
        dataStore.data
            .map { SambungSosSettings.decode(it[KEY_SOS]) }
            .stateIn(scope, SharingStarted.Eagerly, SambungSosSettings())

    override fun setSettings(settings: SambungSosSettings) {
        scope.launch { dataStore.edit { it[KEY_SOS] = settings.encode() } }
    }

    private companion object {
        val KEY_SOS = stringPreferencesKey("sambung_sos")
    }
}
