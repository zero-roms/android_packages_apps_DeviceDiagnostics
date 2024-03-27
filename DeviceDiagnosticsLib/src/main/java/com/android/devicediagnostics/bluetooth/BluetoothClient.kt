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

interface IBluetoothClient {
    var connectionData: BluetoothConnectionData?

    fun start(callback: ScanListener)
    fun read(device: IBluetoothDevice): ByteArray
    fun write(bytes: ByteArray)
    fun reset()

    interface ScanListener {
        fun onBluetoothConnected(device: IBluetoothDevice)
        fun onBluetoothConnectionFailed()
    }
}

interface IBluetoothDevice {
    val name : String
}

class BluetoothDeviceAdapter(val device: BluetoothDevice) : IBluetoothDevice {
    override val name : String
        get() = device.name
}

object BluetoothClient : IBluetoothClient {
    override var connectionData: BluetoothConnectionData? = null

    override fun start(listener: IBluetoothClient.ScanListener) {
        this.listener = listener
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

    override fun read(device: IBluetoothDevice): ByteArray {
        socket = (device as BluetoothDeviceAdapter).device.createInsecureL2capChannel(connectionData!!.psm)
        socket!!.connect()
        if (socket == null)
            throw Exception()
        stopScanning()
        val input = socket!!.inputStream
        var b = ByteArray(0)
        while (true) {
            val chunk = ByteArray(CHUNK)
            val size = input.read(chunk)
            b += chunk.slice(IntRange(0, size - 1))
            if (size < CHUNK) break;
        }
        Log.d(TAG, "read ${b.size} bytes")
        return b
    }

    override fun write(bytes: ByteArray) {
        val output = socket!!.outputStream
        output.write(bytes)
        socket!!.inputStream.readNBytes(4)
        socket!!.close()
    }

    private var socket: BluetoothSocket? = null
    private var listener: IBluetoothClient.ScanListener? = null
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: DeviceScanCallback = DeviceScanCallback()

    override fun reset() {
        connectionData = null
        try {
            stopScanning()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        listener = null
        scanner = null
    }

    private fun stopScanning() {
        Log.d(TAG, "stopped scan");
        scanner?.stopScan(scanCallback)
        adapter.cancelDiscovery()
        listener = null
    }

    private class DeviceScanCallback : ScanCallback() {
        override fun onBatchScanResults(results: List<ScanResult>) {
            for (item in results) {
                item.device?.let { listener?.onBluetoothConnected(BluetoothDeviceAdapter(it)) }
            }
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.device?.let { listener?.onBluetoothConnected(BluetoothDeviceAdapter(it)) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.d(TAG, "Scan failed with error: $errorCode")
            listener?.onBluetoothConnectionFailed()
        }
    }
}