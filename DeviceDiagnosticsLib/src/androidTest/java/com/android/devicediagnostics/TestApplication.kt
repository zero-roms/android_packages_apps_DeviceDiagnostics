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

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.android.devicediagnostics.bluetooth.BluetoothClient
import com.android.devicediagnostics.bluetooth.BluetoothServer
import com.android.settingslib.qrcode.QrCamera

interface QrCameraProvider {
  fun getQrCamera(activity: QrCamera.ScannerCallback) : QrCamera
}

interface AttestationVerifier {
  fun verifyAttestation(attestation: ByteArray, challenge: ByteArray): AttestationResult
}

class TestApplication : Application(), ApplicationInterface {
  override fun getRandomBytes(count: Int) : ByteArray {
    return (1..count).map{it.toByte()}.toByteArray()
  }

  var btClient : BluetoothClient? = null
  var btServer : BluetoothServer? = null
  var qrCameraProvider : QrCameraProvider? = null
  var attestationVerifier : AttestationVerifier? = null

  override fun getBluetoothClient() : BluetoothClient {
    return btClient!!
  }

  override fun getBluetoothServer() : BluetoothServer {
    return btServer!!
  }

  override fun getQrCamera(context: Context, activity: QrCamera.ScannerCallback) : QrCamera {
    return qrCameraProvider!!.getQrCamera(activity)
  }

  override fun verifyAttestation(attestation: ByteArray, challenge: ByteArray): AttestationResult {
    return attestationVerifier!!.verifyAttestation(attestation, challenge)
  }

  // Not mocked yet.
  override fun getLaunchLevel() : Int {
    return Build.VERSION_CODES.VANILLA_ICE_CREAM
  }
}

inline fun getVisibleActivity() : String? {
  val am = InstrumentationRegistry.getInstrumentation().context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
  return am.appTasks[0].taskInfo.topActivity?.className
}

inline fun <reified T : Activity> waitForActivity() {
  val timeout = 5000;
  val startTime = System.currentTimeMillis()
  while (true) {
    val actual = getVisibleActivity()
    if (actual == T::class.java.name)
      return
    Thread.sleep(50)
    if (System.currentTimeMillis() - startTime >= timeout) {
      throw AssertionError("Activity ${T::class.java.simpleName} not visible after $timeout milliseconds, got $actual")
    }
  }
}