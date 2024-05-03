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
import android.os.AsyncTask
import com.android.devicediagnostics.bluetooth.BluetoothClient
import com.android.devicediagnostics.bluetooth.BluetoothServer
import com.android.settingslib.qrcode.QrCamera

interface ApplicationInterface {
  fun getRandomBytes(count: Int) : ByteArray
  fun getQrCamera(context: Context, activity: QrCamera.ScannerCallback) : QrCamera
  fun getBluetoothClient() : BluetoothClient
  fun getBluetoothServer() : BluetoothServer
  fun verifyAttestation(attestation: ByteArray, challenge: ByteArray): AttestationResult
}

// Must only be called on the UI thread.
fun runInBackground(handler: () -> Unit) {
  val task = object : AsyncTask<Void, Void, Void>() {
    override fun doInBackground(vararg params: Void?): Void? {
      handler()
      return null
    }
  }
  task.execute()
}