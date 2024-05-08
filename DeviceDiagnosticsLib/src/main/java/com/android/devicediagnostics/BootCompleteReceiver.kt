/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.devicediagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.ITradeInMode
import android.os.ServiceManager
import android.util.Slog
import com.android.tradeinmode.flags.Flags.enableTradeInMode

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!enableTradeInMode()) return
        var b: IBinder = ServiceManager.getServiceOrThrow("tradeinmode")
        var service: ITradeInMode = ITradeInMode.Stub.asInterface(b)
        if (service.start()) {
            Slog.i("BootCompleteReceiver", "Entered trade-in mode.")
        }
    }
}
