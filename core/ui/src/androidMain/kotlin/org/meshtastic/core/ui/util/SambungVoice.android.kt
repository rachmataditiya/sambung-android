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

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.meshtastic.core.codec2.SambungCodec2
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

private const val MAX_SAMPLES = SAMBUNG_VOICE_MAX_SECONDS * SambungCodec2.SAMPLE_RATE

/** Anything under half a second is a slip of the finger, not a message. */
private const val MIN_SAMPLES = SambungCodec2.SAMPLE_RATE / 2
private const val BYTES_PER_SAMPLE = 2
private const val MILLIS_PER_SECOND = 1000L
private const val PLAYBACK_TAIL_MILLIS = 200L
private const val PROGRESS_STEP_MILLIS = 50L

@Composable
actual fun rememberSambungVoiceRecorder(onClip: (ByteArray?) -> Unit): SambungVoiceRecorder {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnClip by rememberUpdatedState(onClip)
    val isRecording = remember { AtomicBoolean(false) }

    fun record() {
        scope.launch { currentOnClip(withContext(Dispatchers.IO) { recordClip(isRecording) }) }
    }
    // The system dialog took the press away, so a grant only makes the next press work.
    val askPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { currentOnClip(null) }
    // Leaving the screen mid-recording lets go of the microphone.
    DisposableEffect(Unit) { onDispose { isRecording.set(false) } }

    return remember(askPermission) {
        SambungVoiceRecorder(
            start = {
                val granted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                if (granted) {
                    // Set here, not on the recording thread, so letting go early is never overtaken.
                    isRecording.set(true)
                    record()
                } else {
                    askPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            stop = { isRecording.set(false) },
        )
    }
}

/** Records 8 kHz mono, the format Codec2 takes, until [isRecording] is cleared or the ten seconds are up. */
@SuppressLint("MissingPermission") // The caller records only once the permission is granted.
private fun recordClip(isRecording: AtomicBoolean): ByteArray? {
    val minBuffer =
        AudioRecord.getMinBufferSize(
            SambungCodec2.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
    val recorder =
        AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SambungCodec2.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, SambungCodec2.SAMPLE_RATE * BYTES_PER_SAMPLE),
        )
    val samples = ShortArray(MAX_SAMPLES)
    var count = 0
    if (recorder.state == AudioRecord.STATE_INITIALIZED) {
        recorder.startRecording()
        while (isRecording.get() && count < MAX_SAMPLES) {
            val read = recorder.read(samples, count, minOf(SambungCodec2.SAMPLES_PER_FRAME, MAX_SAMPLES - count))
            if (read <= 0) break
            count += read
        }
        isRecording.set(false)
        recorder.stop()
    }
    recorder.release()
    return if (count >= MIN_SAMPLES) SambungCodec2.encode(samples.copyOf(count)) else null
}

/** Each play or stop takes the next number; a playing clip ends when the number is no longer its own. */
private val playbackTurn = AtomicInteger(0)

actual fun sambungPlayVoice(clip: ByteArray, key: String?) {
    val turn = playbackTurn.incrementAndGet()
    thread(name = "sambung-voice") {
        val samples = SambungCodec2.decode(clip)
        if (samples.isNotEmpty()) {
            val track =
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SambungCodec2.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(samples.size * BYTES_PER_SAMPLE)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            track.write(samples, 0, samples.size)
            SambungVoicePlayback.playing.value = key
            track.play()
            // By the clock, not the track's own position, which a static track does not always bring to the end.
            val length = samples.size * MILLIS_PER_SECOND / SambungCodec2.SAMPLE_RATE
            val started = System.currentTimeMillis()
            var elapsed = 0L
            while (playbackTurn.get() == turn && elapsed < length + PLAYBACK_TAIL_MILLIS) {
                SambungVoicePlayback.progress.value = (elapsed.toFloat() / length).coerceAtMost(1f)
                Thread.sleep(PROGRESS_STEP_MILLIS)
                elapsed = System.currentTimeMillis() - started
            }
            track.release()
            if (playbackTurn.get() == turn) clearPlayback()
        }
    }
}

actual fun sambungStopVoice() {
    playbackTurn.incrementAndGet()
    clearPlayback()
}

private fun clearPlayback() {
    SambungVoicePlayback.playing.value = null
    SambungVoicePlayback.progress.value = 0f
}
