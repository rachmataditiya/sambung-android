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

/**
 * Opens the system's picture picker and hands back the picked file's bytes, or null when nothing was picked.
 *
 * Added for Sambung, November 2026. Only Android does anything here so far.
 */
@Composable expect fun rememberSambungPhotoPicker(onPick: (ByteArray?) -> Unit): () -> Unit

/**
 * A JPEG of [photo] whose longer side is at most [maxSide] pixels and that is at most [maxBytes] long, or null when the
 * picture cannot be read or cannot be made that small.
 */
expect fun sambungShrinkPhoto(photo: ByteArray, maxSide: Int, maxBytes: Int): ByteArray?
