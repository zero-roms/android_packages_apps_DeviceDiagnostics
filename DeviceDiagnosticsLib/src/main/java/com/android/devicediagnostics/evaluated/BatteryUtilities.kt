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
package com.android.devicediagnostics.evaluated

import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.android.devicediagnostics.Protos.BatteryInfo
import com.android.devicediagnostics.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun formatSecondsAsDate(seconds: Long): String {
    val instant = Instant.ofEpochSecond(seconds)
    return instant
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
}

fun getBatteryInfo(context: Context): BatteryInfo {
    val builder = BatteryInfo.newBuilder()
    val bm = context.getSystemService(BATTERY_SERVICE) as? BatteryManager
    if (bm == null) return builder.build()

    try {
        var date = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_MANUFACTURING_DATE)
        if (date >= 0) {
            builder.setManufactureDate(formatSecondsAsDate(date))
        }

        date = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_FIRST_USAGE_DATE)
        if (date >= 0) {
            builder.setFirstUsageDate(formatSecondsAsDate(date))
        }
    } catch (e: Exception) {}

    builder.setStateOfHealth(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATE_OF_HEALTH))

    if (android.os.Flags.batteryPartStatusApi()) {
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_PART_STATUS)
        if (status == BatteryManager.PART_STATUS_ORIGINAL) {
            builder.setPartStatus(context.resources.getString(R.string.battery_original))
        } else if (status == BatteryManager.PART_STATUS_REPLACED) {
            builder.setPartStatus(context.resources.getString(R.string.battery_replaced))
        }
        val serial = bm.getStringProperty(BatteryManager.BATTERY_PROPERTY_SERIAL_NUMBER)
        if (!serial.isNullOrEmpty()) {
            builder.setSerial(serial)
        }
    }

    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val batteryStatus = context.registerReceiver(null, filter)
    if (batteryStatus != null) {
        builder.setCycleCount(batteryStatus.getIntExtra("android.os.extra.CYCLE_COUNT", -1))
        builder.setLegacyHealth(batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1))
    }

    return builder.build()
}
