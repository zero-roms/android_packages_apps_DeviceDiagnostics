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
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer

private const val TAG = "bluetooth/ReceiveMessage"
class ReceiveMessage {
  private var message = ""
  private var serverStarted = false
  var address = ""
    private set

  fun reset() {
    message = ""
  }

  fun stop() {
    if (serverStarted) {
      ChatServer.stopServer()
      serverStarted = false
    }
  }

  fun receive(owner: LifecycleOwner, application: Application, receiver: (String) -> Boolean) {
    ChatServer.startServer(application)
    ChatServer.deviceConnection.observe(owner, Observer {
        Log.d(TAG, "deviceConnection, observe $it")
      })
    ChatServer.connectionRequest.observe(owner, Observer {
        Log.d(TAG, "connectionRequest, observe $it")
        message = ""
        address = it.address
      })
    ChatServer.messages.observe(owner, Observer {
        Log.d(TAG, "received ${it.text}")
        message += it.text
        if (receiver(message)) stop()
      })
    ChatServer.requestEnableBluetooth.observe(owner) {
      Log.d(TAG, "requestEnableBluetooth, observe $it")
    }
  }
}