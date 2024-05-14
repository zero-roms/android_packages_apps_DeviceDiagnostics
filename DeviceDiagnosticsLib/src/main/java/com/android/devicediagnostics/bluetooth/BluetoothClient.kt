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
import com.android.devicediagnostics.Protos.TrustedDeviceInfo

private const val TAG = "BluetoothClient"

object BluetoothClient : ScanCallback() {
    interface ScanListener {
        fun onBluetoothConnected(trustedDeviceInfo: TrustedDeviceInfo)
        fun onBluetoothConnectionFailed()
    }

    var connectionData: BluetoothConnectionData? = null

    fun start(listener: ScanListener) {
        this.listener = listener
        scanner = adapter.bluetoothLeScanner

        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        scanner!!.startScan(filters, settings, this)
        Log.d(TAG, "Started scan $scanner")
    }

    fun readTrustedDeviceInfo(): TrustedDeviceInfo {
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

    fun reset() {
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
        scanner?.stopScan(this)
        adapter.cancelDiscovery()
        listener = null
    }

    fun sendReport(report: DeviceReport) {
        val packet = BluetoothPacket.newBuilder().setDeviceReport(report).build()
        writeBluetoothPacket(socket!!, packet)
        val reply = readBluetoothPacket(socket!!)
        if (!reply.hasAck()) {
            throw Exception("Expected acknowledgement packet")
        }
        socket!!.close()
    }

    private fun connect(device: BluetoothDevice?): Boolean {
        if (device == null) {
            return false
        }

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
            return false
        }
        listener?.onBluetoothConnected(trustedDeviceInfo)
        stopScanning()
        return true
    }

    override fun onBatchScanResults(results: List<ScanResult>) {
        for (item in results) {
            if (connect(item.device)) {
                break
            }
        }
    }

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        connect(result.device)
    }

    override fun onScanFailed(errorCode: Int) {
        Log.d(TAG, "Scan failed with error: $errorCode")
        listener?.onBluetoothConnectionFailed()
    }
}
