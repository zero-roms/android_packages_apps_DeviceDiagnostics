/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.devicediagnostics

import android.content.Context
import android.os.SystemProperties
import com.android.devicediagnostics.bluetooth.BluetoothClient
import com.android.devicediagnostics.bluetooth.BluetoothServer
import com.android.settingslib.qrcode.QrCamera
import com.google.android.attestation.ParsedAttestationRecord
import kotlin.math.min
import kotlin.random.Random

open class ApplicationInterface {
    val btClient = BluetoothClient()

    open fun getRandomBytes(count: Int): ByteArray {
        return Random.nextBytes(count)
    }
    open fun getQrCamera(context: Context, activity: QrCamera.ScannerCallback): QrCamera {
        return QrCamera(context, activity)
    }
    open fun getBluetoothClient(): BluetoothClient {
        return btClient
    }
    open fun getBluetoothServer(): BluetoothServer {
        return BluetoothServer
    }
    open fun verifyAttestation(
        attestation: ByteArray,
        challenge: ByteArray
    ): Pair<ParsedAttestationRecord?, AttestationResult> {
        return getAttestation(attestation, challenge)
    }
    open fun getLaunchLevel(): Int {
        val vendorApiLevel = getPropertyInt("ro.vendor.api_level", -1)
        if (vendorApiLevel != -1) {
            return vendorApiLevel
        }
        val boardApiLevel = getPropertyInt("ro.board.api_level", -1)
        val firstApiLevel = getPropertyInt("ro.product.first_api_level", -1)
        if (boardApiLevel != -1) {
            if (firstApiLevel != -1) {
                return min(boardApiLevel, firstApiLevel)
            }
            return boardApiLevel
        }
        return firstApiLevel
    }

    private fun getPropertyInt(key: String, defaultValue: Int): Int {
        var value: String? = null
        value = SystemProperties.get(key, null)
        if (value == null) {
            return defaultValue
        }
        return try {
            Integer.parseInt(value)
        } catch (e: Exception) {
            defaultValue
        }
    }

    companion object {
        private var singleton = ApplicationInterface()

        val app
            get() = singleton

        fun setApp(app: ApplicationInterface) {
            this.singleton = app
        }
    }
}
