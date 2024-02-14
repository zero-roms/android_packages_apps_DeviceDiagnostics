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

package com.android.devicediagnostics.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID
import org.json.JSONObject

val SERVICE_UUID: UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb")
private const val TAG = "BluetoothServer"
const val CHUNK = 4096

private const val PSM = "PSM"

class BluetoothConnectionData(var psm: Int = 0) {
    constructor(json: String) : this() {
        val json = JSONObject(json)
        psm = json.getInt(PSM)
    }

    override fun toString(): String {
        val json = JSONObject()
        json.put(PSM, psm)
        return json.toString()
    }
}

object BluetoothServer {
    private val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    var connectionData = BluetoothConnectionData()
        private set

    fun start(string: String, written: () -> Unit = {}) {
        startAdvertisement()
        serverSocket = adapter.listenUsingInsecureL2capChannel()
        Log.d(TAG, "Server started with psm ${serverSocket?.psm}")
        connectionData.psm = serverSocket!!.psm

        Thread {
            socket = serverSocket!!.accept()
            serverSocket!!.close()
            serverSocket = null
            advertiser!!.stopAdvertising(advertiseCallback)
            var output = socket!!.outputStream
            output.write(string.toByteArray(Charsets.UTF_8))
            Log.d(TAG, "wrote $string")
            written()
        }.start()
    }

    fun read(): String {
        var input = socket!!.inputStream
        var b = ByteArray(0)
        while (true) {
            val chunk = ByteArray(CHUNK)
            val size = input.read(chunk)
            b += chunk.slice(IntRange(0, size - 1))
            if (size < CHUNK) break;
        }
        Log.d(TAG, "read: ${b.size} ${b!!.toString(Charsets.UTF_8)}")
        socket!!.outputStream.write("Done".toByteArray())
        socket!!.close()
        socket = null
        return b!!.toString(Charsets.UTF_8)
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseSettings = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
        .setTimeout(0)
        .build()
    private var advertiseData = AdvertiseData.Builder()
        .addServiceUuid(ParcelUuid(SERVICE_UUID))
        .setIncludeDeviceName(true).build()

    private var advertiseCallback: AdvertiseCallback? = null

    private fun startAdvertisement() {
        advertiser = adapter.bluetoothLeAdvertiser
        Log.d(TAG, "startAdvertisement: with advertiser ${advertiser}")

        advertiseCallback = DeviceAdvertiseCallback()
        advertiser!!.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
    }

    private class DeviceAdvertiseCallback : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.d(TAG, "Advertising failed with error: $errorCode")
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertising successfully started")
        }
    }
}