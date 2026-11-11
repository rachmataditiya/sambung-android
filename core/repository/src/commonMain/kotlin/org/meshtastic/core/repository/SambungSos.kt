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
package org.meshtastic.core.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.SambungLocationMessage
import org.meshtastic.core.repository.usecase.SendMessageOutcome
import org.meshtastic.core.repository.usecase.SendMessageUseCase

/** Where an SOS goes, what it says and how often it repeats. An empty [text] means the app's default wording. */
data class SambungSosSettings(
    val contactKey: String = PRIMARY_CHANNEL,
    val text: String = "",
    val intervalSeconds: Int = SambungSos.DEFAULT_INTERVAL_SECONDS,
) {
    fun encode(): String = "$intervalSeconds|$contactKey|$text"

    companion object {
        /** The primary channel, to everyone: in an emergency, anyone in range is who you want. */
        const val PRIMARY_CHANNEL = "0^all"
        private const val FIELDS = 3

        /** Anything unreadable decodes to the defaults. */
        fun decode(value: String?): SambungSosSettings {
            val parts = value?.split('|', limit = FIELDS)
            val interval = parts?.getOrNull(0)?.toIntOrNull()
            return if (parts?.size == FIELDS && interval != null) {
                SambungSosSettings(parts[1], parts[2], SambungSos.clampedInterval(interval))
            } else {
                SambungSosSettings()
            }
        }
    }
}

interface SambungSosPrefs {
    val settings: StateFlow<SambungSosSettings>

    fun setSettings(settings: SambungSosSettings)
}

/** Null [startedAtMillis] means no SOS is running. */
data class SambungSosState(val startedAtMillis: Long? = null, val sentCount: Int = 0) {
    val isActive: Boolean
        get() = startedAtMillis != null
}

/**
 * The emergency message: who is calling, what for, and where from, sent as ordinary text so every Meshtastic client can
 * read it, and again every few minutes with the latest position until it is stopped. The bell character at the front
 * makes radios with an alert set up sound it. Same message as the iPhone app (SambungSOS.swift).
 *
 * Added for Sambung, November 2026.
 */
class SambungSos(
    private val prefs: SambungSosPrefs,
    private val sendMessageUseCase: SendMessageUseCase,
    private val nodeRepository: NodeRepository,
    private val locationService: LocationService,
    private val scope: ApplicationCoroutineScope,
) {
    private val _state = MutableStateFlow(SambungSosState())
    val state: StateFlow<SambungSosState> = _state.asStateFlow()

    private var loop: Job? = null

    /** [defaultText] is what the call says when no text was set: the app's wording, in the app's language. */
    fun start(defaultText: String) {
        if (_state.value.isActive) return
        val startedAt = nowMillis
        _state.value = SambungSosState(startedAtMillis = startedAt)
        loop = scope.launch {
            while (!isOver(startedAt, nowMillis)) {
                sendOnce(defaultText)
                delay(prefs.settings.value.intervalSeconds * MILLIS_PER_SECOND)
            }
            _state.value = SambungSosState()
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        _state.value = SambungSosState()
    }

    private suspend fun sendOnce(defaultText: String) {
        val settings = prefs.settings.value
        val ourNode = nodeRepository.ourNodeInfo.value
        val phone = locationService.getCurrentLocation()?.let { it.latitude to it.longitude }
        val radio = ourNode?.validPosition?.let { ourNode.latitude to ourNode.longitude }
        val text = message(ourNode?.user?.long_name ?: "?", settings.text.ifBlank { defaultText }, phone ?: radio)
        runCatching { sendMessageUseCase(text, settings.contactKey) }
            .onSuccess { outcome ->
                if (outcome is SendMessageOutcome.Queued) _state.update { it.copy(sentCount = it.sentCount + 1) }
            }
            .onFailure {
                if (it is CancellationException) throw it
                Logger.w(it) { "Sambung SOS was not sent" }
            }
    }

    companion object {
        /** An hour of repeats is enough to be heard; after that it is airtime and battery. */
        const val MAX_DURATION_MILLIS = 60 * 60 * 1000L

        /** A public channel is shared airtime, so no faster than once a minute. */
        const val MIN_INTERVAL_SECONDS = 60
        const val DEFAULT_INTERVAL_SECONDS = 120
        private const val MILLIS_PER_SECOND = 1000L
        private const val BELL = '\u0007'

        fun clampedInterval(seconds: Int): Int = maxOf(MIN_INTERVAL_SECONDS, seconds)

        fun isOver(startedAtMillis: Long, now: Long): Boolean = now - startedAtMillis >= MAX_DURATION_MILLIS

        /** The call, and when there is a position the same location line a shared position uses. */
        fun message(name: String, text: String, position: Pair<Double, Double>?): String {
            val call = "$BELL🆘 SOS $name: ${text.trim()}"
            return if (position == null) {
                call
            } else {
                call + "\n" + SambungLocationMessage.text(position.first, position.second)
            }
        }
    }
}
