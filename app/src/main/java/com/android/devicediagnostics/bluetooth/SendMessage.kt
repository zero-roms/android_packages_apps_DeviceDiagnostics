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

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer

private const val TAG = "bluetooth/SendMessage"
open class SendMessage {
  open fun foundDevice(device: BluetoothDevice) {
    setDevice(device)
  }

  open fun sent() {
    stop()
  }

  fun setDevice(device: BluetoothDevice) {
    ChatServer.setCurrentChatConnection(device)
    stopScanning()
  }

  fun stop() {
    stopScanning()
    ChatServer.stopServer()
  }

  fun send(message: String, owner: LifecycleOwner, application: Application) {
    scanner = adapter.bluetoothLeScanner
    scanCallback = DeviceScanCallback()
    scanner?.startScan(scanFilters, scanSettings, scanCallback)

    ChatServer.startServer(application)
    ChatServer.deviceConnection.observe(owner, Observer {
        Log.d(TAG, "deviceConnection, observe $it")
      })
    ChatServer.connectionRequest.observe(
      owner, Observer {
        Log.d(TAG, "connectionRequest, observe $it")
        if (bluetoothDevice == 0) {
          bluetoothDevice = it.hashCode()
          ChatServer.setCurrentChatConnection(it)
          Thread {
            Log.d(TAG, "Sending ${message.length} $message")
            for (i in message.indices step 20) {
              val string = message.substring(
                i,
                Integer.min(i + 20, message.length)
              )

              var tries = 1
              while (!ChatServer.sendMessage(string)) {
                tries++
              }
              Log.d(TAG, "Sent $i of ${message.length} $string on after $tries tries")
            }
            sent()
          }.start()
        }
      })

    ChatServer.messages.observe(owner, Observer {
        Log.d(TAG, "messages, observe $it")
      })

    ChatServer.requestEnableBluetooth.observe(owner) {
      Log.d(TAG, "requestEnableBluetooth, observe $it")
    }
  }

  private val adapter = BluetoothAdapter.getDefaultAdapter()
  private var scanner: BluetoothLeScanner? = null
  private val scanFilters: List<ScanFilter> = listOf(
    ScanFilter.Builder()
      .setServiceUuid(ParcelUuid(SERVICE_UUID))
      .build())

  private val scanSettings: ScanSettings = ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
    .build()

  private var scanCallback : DeviceScanCallback? = null
  private var bluetoothDevice = 0

  private fun stopScanning() {
    scanner?.stopScan(scanCallback)
  }

  private inner class DeviceScanCallback : ScanCallback() {
    override fun onBatchScanResults(results: List<ScanResult>) {
      super.onBatchScanResults(results)
      for (item in results) {
        item.device?.let { foundDevice(it) }
      }
    }

    override fun onScanResult(callbackType: Int, result: ScanResult) {
      super.onScanResult(callbackType, result)
      result.device?.let { foundDevice(it) }
    }

    override fun onScanFailed(errorCode: Int) {
      super.onScanFailed(errorCode)
      Log.d(TAG, "Scan failed with error: $errorCode")
    }
  }
}