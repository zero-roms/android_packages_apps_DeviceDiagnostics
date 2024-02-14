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
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log

private const val TAG = "BluetoothClient"

object BluetoothClient {
    var connectionData: BluetoothConnectionData? = null

    fun start(foundDevice: (device: BluetoothDevice) -> Unit) {
        foundDevice_ = foundDevice
        scanner = adapter.bluetoothLeScanner
        scanCallback = DeviceScanCallback()

        val filters = listOf(ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()
        scanner!!.startScan(filters, settings, scanCallback)
        Log.d(TAG, "Started scan $scanner");
    }

    fun read(device: BluetoothDevice): String {
        stopScanning()
        socket = device.createInsecureL2capChannel(connectionData!!.psm)
        socket!!.connect()
        var input = socket!!.inputStream
        var b = ByteArray(0)
        while (true) {
            val chunk = ByteArray(CHUNK)
            val size = input.read(chunk)
            b += chunk.slice(IntRange(0, size - 1))
            if (size < CHUNK) break;
        }
        Log.d(TAG, "read ${b.size} ${b!!.toString(Charsets.UTF_8)}")
        return b!!.toString(Charsets.UTF_8)
    }

    fun write(s: String) {
        var output = socket!!.outputStream
        output.write(s.toByteArray(Charsets.UTF_8))
        Log.d(TAG, "wrote ${s}")
    }

    private var socket: BluetoothSocket? = null
    private var foundDevice_: ((BluetoothDevice) -> Unit)? = null
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: DeviceScanCallback? = null

    private fun stopScanning() {
        Log.d(TAG, "stopped scan");
        scanner?.stopScan(scanCallback)
        adapter.cancelDiscovery()
    }

    private class DeviceScanCallback : ScanCallback() {
        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            for (item in results) {
                item.device?.let { foundDevice_!!(it) }
            }
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            result.device?.let { foundDevice_!!(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d(TAG, "Scan failed with error: $errorCode")
        }
    }
}