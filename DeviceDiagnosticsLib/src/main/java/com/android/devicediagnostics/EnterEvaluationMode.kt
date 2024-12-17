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
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ITradeInMode
import android.os.ServiceManager
import android.util.Slog
import com.android.tradeinmode.flags.Flags.enableTradeInMode

class EnterEvaluationMode : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterEvaluationMode()
        finish()
    }

    private fun enterEvaluationMode() {
        if (!enableTradeInMode()) {
            return
        }

        var b: IBinder = ServiceManager.getServiceOrThrow("tradeinmode")
        var service: ITradeInMode = ITradeInMode.Stub.asInterface(b)
        if (!service.isEvaluationModeAllowed()) {
            Slog.e("EnterEvaluationMode", "Evaluation mode is not allowed on this device.")
            return
        }

        if (service.enterEvaluationMode()) {
            // Dismiss suw
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setAction("com.android.setupwizard.TIM")
            startActivity(intent)
        }
    }
}
