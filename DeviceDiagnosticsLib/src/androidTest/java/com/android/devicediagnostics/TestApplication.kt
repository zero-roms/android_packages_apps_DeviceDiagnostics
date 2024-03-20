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
import com.android.devicediagnostics.evaluated.IQrCamera
import com.android.settingslib.qrcode.QrCamera

private const val payload = "{\"PSM\":134}"
class TestApplication : Application(), ApplicationInterface {
  override fun getRandomBytes(count: Int) : ByteArray {
    return (1..count).map{it.toByte()}.toByteArray()
  }

  override fun getQrCamera(context: Context, activity: QrCamera.ScannerCallback) : IQrCamera {
    return object : IQrCamera {
      override fun start(surface: SurfaceTexture) {
        Thread {
          if (activity.isValid(payload))
            activity.handleSuccessfulResult(payload)
        }.run()
      }

      override fun stop() {}
    }
  }
}