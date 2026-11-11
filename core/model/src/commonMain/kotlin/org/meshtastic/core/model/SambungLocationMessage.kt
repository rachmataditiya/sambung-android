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
package org.meshtastic.core.model

/**
 * A position sent as ordinary text ("📍 Lokasi saya: -6.20880, 106.84560"), so any client can read it and Sambung can
 * draw it as a location card.
 *
 * Added for Sambung, November 2026.
 */
object SambungLocationMessage {
    private val pattern = Regex("""📍[^\d\-]*(-?\d{1,2}\.\d{3,}),\s*(-?\d{1,3}\.\d{3,})""")
    private const val MAX_LATITUDE = 90.0
    private const val MAX_LONGITUDE = 180.0

    /** Latitude and longitude in [text], or null when it carries no valid position. */
    fun coordinate(text: String): Pair<Double, Double>? {
        val match = pattern.find(text) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        if (latitude !in -MAX_LATITUDE..MAX_LATITUDE || longitude !in -MAX_LONGITUDE..MAX_LONGITUDE) return null
        return latitude to longitude
    }
}
