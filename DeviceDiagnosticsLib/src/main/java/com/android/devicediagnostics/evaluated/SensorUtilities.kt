/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.devicediagnostics.evaluated

import android.os.IBinder
import android.os.ITradeInMode
import android.os.ServiceManager
import com.android.devicediagnostics.Protos.SensorInfo

const val SENSOR_TIMEOUT_MILLIS: Long = 1000

fun getSensorInfo(): SensorInfo {
    var b: IBinder = ServiceManager.getServiceOrThrow("tradeinmode")
    var service: ITradeInMode = ITradeInMode.Stub.asInterface(b)
    val builder = SensorInfo.newBuilder()

    val moistureIntrusion = service.getMoistureIntrusionDetected(SENSOR_TIMEOUT_MILLIS)
    if (moistureIntrusion == ITradeInMode.MoistureIntrusionStatus.DETECTED) {
        builder.setMoistureIntrusion(true)
    } else if (moistureIntrusion == ITradeInMode.MoistureIntrusionStatus.UNDETECTED) {
        builder.setMoistureIntrusion(false)
    }

    return builder.build()
}
