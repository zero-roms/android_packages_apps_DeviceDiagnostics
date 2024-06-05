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
import com.android.devicediagnostics.Protos.BluetoothPacket
import com.android.devicediagnostics.Protos.DeviceReport
import com.android.devicediagnostics.Protos.PacketCommand
import com.android.devicediagnostics.Protos.TrustedDeviceInfo
import kotlinx.coroutines.sync.Mutex

private const val TAG = "BluetoothClient"

class BluetoothClient {
    interface ScanListener {
        fun onBluetoothConnected(trustedDeviceInfo: TrustedDeviceInfo)

        fun onBluetoothConnectionFailed()
    }

    var connectionData: BluetoothConnectionData? = null

    fun start(listener: ScanListener) {
        synchronized(lock) { startImpl(listener) }
    }

    private fun startImpl(listener: ScanListener) {
        this.listener = listener
        scanner = adapter.bluetoothLeScanner
        scanCallback = MyScanCallback()

        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        scanner!!.startScan(filters, settings, scanCallback!!)
        Log.d(TAG, "Started scan $scanner")
    }

    private fun readTrustedDeviceInfo(): TrustedDeviceInfo {
        val message = readBluetoothPacket(socket!!)
        if (!message.hasTrustedDeviceInfo()) {
            throw Exception("Expected TrustedDeviceInfo message")
        }
        return message.trustedDeviceInfo
    }

    private var socket: BluetoothSocket? = null
    private var listener: ScanListener? = null
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var lock = Mutex()

    inner class MyScanCallback : ScanCallback() {
        override fun onBatchScanResults(results: List<ScanResult>) {
            for (item in results) {
                onScanResult(0, item)
            }
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device == null) {
                return
            }

            synchronized(lock) {
                if (scanCallback != this) {
                    return
                }
                connect(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            synchronized(lock) {
                if (scanCallback != this) {
                    return
                }
                Log.d(TAG, "Scan failed with error: $errorCode")
                listener?.onBluetoothConnectionFailed()
            }
        }
    }

    fun reset() {
        synchronized(lock) { resetImpl() }
    }

    private fun resetImpl() {
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
        Log.d(TAG, "stopped scan")
        if (scanCallback != null) {
            scanner?.stopScan(scanCallback!!)
            scanCallback = null
        }
        adapter.cancelDiscovery()
        listener = null
    }

    fun sendReport(report: DeviceReport) {
        synchronized(lock) {
            val packet = BluetoothPacket.newBuilder().setDeviceReport(report)
            writeBluetoothPacket(socket!!, packet)
            val reply = readBluetoothPacket(socket!!)
            if (reply.command != PacketCommand.COMMAND_ACK) {
                throw Exception("Expected acknowledgement packet")
            }
            resetImpl()
        }
    }

    private fun connect(device: BluetoothDevice) {
        var trustedDeviceInfo: TrustedDeviceInfo? = null
        try {
            Log.d(TAG, "Found device ${device.name}")
            socket = device.createInsecureL2capChannel(connectionData!!.psm)
            socket!!.connect()

            trustedDeviceInfo = readTrustedDeviceInfo()
            val challenge = trustedDeviceInfo.challenge.toByteArray()
            if (!challenge.contentEquals(connectionData!!.challenge)) {
                throw Exception("Trusted device has unexpected challenge")
            }
            Log.i(TAG, "Bluetooth connection succeeded")
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth connection error: $e")
            return
        }
        listener?.onBluetoothConnected(trustedDeviceInfo)
        stopScanning()
    }
}
