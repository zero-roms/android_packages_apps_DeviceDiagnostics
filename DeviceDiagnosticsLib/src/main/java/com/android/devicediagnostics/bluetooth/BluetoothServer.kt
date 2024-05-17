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
import com.android.devicediagnostics.Protos.BluetoothPacket
import com.android.devicediagnostics.Protos.DeviceReport
import com.android.devicediagnostics.Protos.TrustedDeviceInfo
import com.android.devicediagnostics.runInBackground
import java.util.UUID

val SERVICE_UUID: UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb")
private const val TAG = "BluetoothServer"
const val CHUNK = 4096

object BluetoothServer {
    private val adapter
        get() = BluetoothAdapter.getDefaultAdapter()
    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    var connectionData = BluetoothConnectionData(0, ByteArray(0))
    val bluetoothEnabled
        get() = adapter.isEnabled

    fun start(trustedDeviceInfo: TrustedDeviceInfo, written: () -> Unit) {
        startAdvertisement()
        serverSocket = adapter.listenUsingInsecureL2capChannel()
        Log.d(TAG, "Server started with psm ${serverSocket?.psm}")

        connectionData =
            BluetoothConnectionData(serverSocket!!.psm, trustedDeviceInfo.challenge.toByteArray())

        // Thread since this waits for connections and otherwise will block Ux
        runInBackground() {
            socket = serverSocket!!.accept()
            serverSocket!!.close()
            serverSocket = null
            advertiser!!.stopAdvertising(advertiseCallback)

            val packet =
                BluetoothPacket.newBuilder().setTrustedDeviceInfo(trustedDeviceInfo).build()
            writeBluetoothPacket(socket!!, packet)
            Log.d(TAG, "wrote start message")
            written()
        }
    }

    private fun read(): BluetoothPacket {
        return readBluetoothPacket(socket!!)
    }

    fun readDeviceReport(): DeviceReport {
        val message = read()
        if (!message.hasDeviceReport()) {
            throw Exception("Expected DeviceReport message, got " + message.payloadCase.toString())
        }

        val ack = BluetoothPacket.newBuilder().setAck(true).build()
        writeBluetoothPacket(socket!!, ack)

        close()
        return message.deviceReport
    }

    private fun close() {
        socket!!.close()
        socket = null
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseSettings =
        AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTimeout(0)
            .build()
    private var advertiseData =
        AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(true)
            .build()

    private var advertiseCallback: AdvertiseCallback? = null

    private fun startAdvertisement() {
        advertiser = adapter.bluetoothLeAdvertiser
        Log.d(TAG, "startAdvertisement: with advertiser $advertiser")

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
