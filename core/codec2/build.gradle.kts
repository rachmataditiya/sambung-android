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
import com.android.build.api.dsl.LibraryExtension

// Added for Sambung, November 2026: Codec2 1.2.0 (https://github.com/drowe67/codec2), the speech codec only, for
// voice messages. LGPL-2.1, see COPYING. The C sources under src/main/cpp/codec2 are unmodified and are the same
// files the iPhone app builds, so both apps speak the same 700C bitstream.
plugins { alias(libs.plugins.meshtastic.android.library) }

configure<LibraryExtension> {
    namespace = "org.meshtastic.core.codec2"
    ndkVersion = "28.2.13676358"

    defaultConfig {
        // The ABIs the app ships.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
}
