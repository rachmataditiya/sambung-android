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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow

/** The longest voice message: ten seconds is 1000 bytes, six packets, about 25 seconds of airtime on LongFast. */
const val SAMBUNG_VOICE_MAX_SECONDS = 10

private const val VOICE_BYTES_PER_SECOND = 100

/** How long a compressed clip plays, rounded to whole seconds. */
fun sambungVoiceSeconds(clip: ByteArray): Int = (clip.size + VOICE_BYTES_PER_SECOND / 2) / VOICE_BYTES_PER_SECOND

/**
 * [start] begins recording from the microphone and [stop] ends it; the clip then arrives where the recorder was made.
 */
class SambungVoiceRecorder(val start: () -> Unit, val stop: () -> Unit)

/**
 * A microphone recorder for voice messages. [onClip] gets the clip already compressed for the radio, or null when the
 * microphone was refused or the recording was too short to send.
 *
 * Added for Sambung, November 2026. Only Android does anything here so far.
 */
@Composable expect fun rememberSambungVoiceRecorder(onClip: (ByteArray?) -> Unit): SambungVoiceRecorder

/** The voice clip being played, if any: the name it was started under and how far through it is, 0 to 1. */
object SambungVoicePlayback {
    val playing = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow(0f)
}

/**
 * Plays a compressed clip: what was recorded is heard exactly as the other side will hear it. [key] names it in
 * [SambungVoicePlayback] while it plays; a clip already playing is cut off.
 */
expect fun sambungPlayVoice(clip: ByteArray, key: String? = null)

/** Stops whatever clip is playing. */
expect fun sambungStopVoice()
