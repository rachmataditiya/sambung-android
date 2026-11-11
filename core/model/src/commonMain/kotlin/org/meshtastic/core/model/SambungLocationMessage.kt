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

import org.meshtastic.core.common.util.bearing
import org.meshtastic.core.common.util.latLongToMeter
import kotlin.math.abs
import kotlin.math.roundToLong

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
        val match = pattern.find(text)
        val latitude = match?.groupValues?.get(1)?.toDoubleOrNull()
        val longitude = match?.groupValues?.get(2)?.toDoubleOrNull()
        val onTheGlobe =
            latitude != null &&
                longitude != null &&
                latitude in -MAX_LATITUDE..MAX_LATITUDE &&
                longitude in -MAX_LONGITUDE..MAX_LONGITUDE
        return if (onTheGlobe) latitude!! to longitude!! else null
    }

    /**
     * The text to send for a position. Five decimals is about a metre. The words are the iPhone app's and stay as they
     * are in every language, so both apps send the same message.
     */
    fun text(latitude: Double, longitude: Double): String = "📍 Lokasi saya: ${fixed5(latitude)}, ${fixed5(longitude)}"

    private fun fixed5(value: Double): String {
        val scaled = (abs(value) * DECIMALS_SCALE).roundToLong()
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        return "$sign${scaled / DECIMALS_SCALE}.${(scaled % DECIMALS_SCALE).toString().padStart(DECIMALS, '0')}"
    }

    fun distanceMeters(from: Pair<Double, Double>, to: Pair<Double, Double>): Double =
        latLongToMeter(from.first, from.second, to.first, to.second)

    /** Which of the eight compass points [to] lies toward, seen from [from]: 0 is north, then clockwise. */
    fun compassPoint(from: Pair<Double, Double>, to: Pair<Double, Double>): Int {
        val degrees = (bearing(from.first, from.second, to.first, to.second) % FULL_TURN + FULL_TURN) % FULL_TURN
        return ((degrees + COMPASS_STEP / 2) / COMPASS_STEP).toInt() % COMPASS_POINTS
    }

    /** "120 m" below a kilometre, to the nearest ten; "1.1 km" from there on. */
    fun distanceLabel(meters: Double): String = if (meters < METERS_PER_KM) {
        "${(meters / ROUND_TO_METERS).roundToLong() * ROUND_TO_METERS} m"
    } else {
        val tenths = (meters / METERS_PER_KM * TENTHS).roundToLong()
        "${tenths / TENTHS}.${tenths % TENTHS} km"
    }

    private const val DECIMALS = 5
    private const val DECIMALS_SCALE = 100_000L
    private const val FULL_TURN = 360.0
    private const val COMPASS_POINTS = 8
    private const val COMPASS_STEP = FULL_TURN / COMPASS_POINTS
    private const val METERS_PER_KM = 1000.0
    private const val ROUND_TO_METERS = 10L
    private const val TENTHS = 10L
}
