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

import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import com.android.devicediagnostics.bluetooth.BluetoothConnectionData
import com.android.devicediagnostics.bluetooth.IBluetoothClient
import com.android.devicediagnostics.bluetooth.IBluetoothDevice
import com.android.devicediagnostics.evaluated.IQrCamera
import com.android.devicediagnostics.Protos.DeviceReport
import com.android.devicediagnostics.Protos.TestResults
import com.android.devicediagnostics.Protos.TrustedDeviceInfo
import com.android.devicediagnostics.bluetooth.IBluetoothServer
import com.android.settingslib.qrcode.QrCamera
import com.google.protobuf.ByteString
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val QR_PAYLOAD = "{\"PSM\":134}"
class TestApplication : Application(), ApplicationInterface {
  override fun getRandomBytes(count: Int) : ByteArray {
    return (1..count).map{it.toByte()}.toByteArray()
  }

  override fun getQrCamera(context: Context, activity: QrCamera.ScannerCallback) : IQrCamera {
    return object : IQrCamera {
      override fun start(surface: SurfaceTexture) {
        Thread {
          if (activity.isValid(QR_PAYLOAD))
            activity.handleSuccessfulResult(QR_PAYLOAD)
        }.run()
      }

      override fun stop() {}
    }
  }

  var writtenData : ByteArray? = null

  override fun getBluetoothClient() : IBluetoothClient {
    return object : IBluetoothClient {
      override var connectionData: BluetoothConnectionData? = null

      override fun start(foundDevice: (device: IBluetoothDevice) -> Unit) {
        foundDevice(object : IBluetoothDevice { override val name = "Test" })
      }

      override fun read(device: IBluetoothDevice): ByteArray {
        val infoBuilder = TrustedDeviceInfo.newBuilder()
        infoBuilder.setChallenge(ByteString.copyFrom((1..32).map{it.toByte()}.toByteArray()))
        return infoBuilder.build().toByteArray()
      }

      override fun write(bytes: ByteArray) {
        writtenData = bytes
      }
    }
  }

  val lock = ReentrantLock()
  val condition: Condition = lock.newCondition()
  override fun getBluetoothServer() : IBluetoothServer {
    return object : IBluetoothServer {

      override var connectionData = BluetoothConnectionData(123)

      override val bluetoothEnabled = true

      override fun start(trustedDeviceInfo: TrustedDeviceInfo, written: () -> Unit) {
        Thread {
          lock.withLock() {
            condition.await()
            written()
          }
        }.start()
      }

      override fun read(): ByteArray {
        lock.withLock() {
          condition.await()
        }
        return DeviceReport.newBuilder().also {
          it.setTests(TestResults.newBuilder().also {
            it.setScreenTest(true)
            it.build()
          })
        }.build().toByteArray()
      }
    }
  }
}