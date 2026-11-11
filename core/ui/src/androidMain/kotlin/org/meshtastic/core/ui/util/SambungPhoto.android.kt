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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@Composable
actual fun rememberSambungPhotoPicker(onPick: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val bytes = uri?.let {
                // A picture the picker offers but cannot hand over is treated as nothing picked.
                runCatching { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } }
                    .getOrNull()
            }
            onPick(bytes)
        }
    return remember(launcher) { { launcher.launch("image/*") } }
}

actual fun sambungShrinkPhoto(photo: ByteArray, maxSide: Int, maxBytes: Int): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(photo, 0, photo.size, bounds)
    // Decode at a power of two that still leaves at least twice the target size to scale down from.
    val sample = Integer.highestOneBit((maxOf(bounds.outWidth, bounds.outHeight) / (maxSide * 2)).coerceAtLeast(1))
    val decoded =
        BitmapFactory.decodeByteArray(photo, 0, photo.size, BitmapFactory.Options().apply { inSampleSize = sample })
    return decoded?.let { bitmap ->
        val scale = (maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
        val matrix =
            Matrix().apply {
                postScale(scale, scale)
                postRotate(rotationDegrees(photo))
            }
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        JPEG_QUALITIES.firstNotNullOfOrNull { quality ->
            ByteArrayOutputStream()
                .also { upright.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                .toByteArray()
                .takeIf { it.size <= maxBytes }
        }
    }
}

/** Cameras store a sideways picture plus a note on how to turn it; the small copy has no room for the note. */
private fun rotationDegrees(photo: ByteArray): Float = runCatching {
    when (
        ExifInterface(ByteArrayInputStream(photo))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> QUARTER_TURN
        ExifInterface.ORIENTATION_ROTATE_180 -> HALF_TURN
        ExifInterface.ORIENTATION_ROTATE_270 -> THREE_QUARTER_TURN
        else -> 0f
    }
}
    .getOrDefault(0f)

private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f
private const val THREE_QUARTER_TURN = 270f
private val JPEG_QUALITIES = listOf(70, 55, 40, 30, 20, 12)
