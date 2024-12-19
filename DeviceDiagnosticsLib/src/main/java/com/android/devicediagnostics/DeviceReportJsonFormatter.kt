/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.util.Base64
import com.android.devicediagnostics.Protos.BatteryInfo
import com.android.devicediagnostics.Protos.DeviceReport
import com.android.devicediagnostics.Protos.LockInfo
import com.android.devicediagnostics.Protos.ProductInfo
import com.android.devicediagnostics.Protos.StorageInfo
import com.android.devicediagnostics.Protos.TestResults
import org.json.JSONObject

private fun putIfNotEmpty(holder: JSONObject, key: String, obj: JSONObject) {
    if (obj.length() > 0) holder.put(key, obj)
}

fun <T> JSONObject.putIfPresent(present: Boolean, key: String, value: T) {
    if (present) put(key, value)
}

private fun testResultsToJson(results: TestResults): JSONObject {
    val obj = JSONObject()
    obj.putIfPresent(results.hasScreenTest(), "screen_test", results.screenTest)
    obj.putIfPresent(results.hasTouchTest(), "touch_test", results.touchTest)
    return obj
}

private fun batteryInfoToJson(battery: BatteryInfo): JSONObject {
    val obj = JSONObject()
    obj.putIfPresent(battery.hasCycleCount(), "cycle_count", battery.cycleCount)
    obj.putIfPresent(battery.hasStateOfHealth(), "health", battery.stateOfHealth)
    obj.putIfPresent(battery.hasSerial(), "serial", battery.serial)
    obj.putIfPresent(battery.hasPartStatus(), "part_status", battery.partStatus)
    obj.put("state", battery.legacyHealth)
    obj.putIfPresent(
        battery.hasManufactureTimestamp(),
        "manufacturing_date",
        battery.manufactureTimestamp,
    )
    obj.putIfPresent(
        battery.hasFirstUsageTimestamp(),
        "first_usage_date",
        battery.firstUsageTimestamp,
    )
    return obj
}

private fun productInfoToJson(product: ProductInfo): JSONObject {
    val obj = JSONObject()
    obj.putIfPresent(product.hasBrand(), "brand", product.brand)
    obj.putIfPresent(product.hasDevice(), "device", product.device)
    obj.putIfPresent(product.hasManufacturer(), "manufacturer", product.manufacturer)
    obj.putIfPresent(product.hasModel(), "model", product.model)
    obj.putIfPresent(product.hasName(), "name", product.name)
    return obj
}

private fun storageInfoToJson(storage: StorageInfo): JSONObject {
    val obj = JSONObject()
    obj.putIfPresent(
        storage.hasUsefulLifetimeRemaining(),
        "useful_lifetime_remaining",
        storage.usefulLifetimeRemaining,
    )
    obj.put("capacity_bytes", storage.capacityBytes.toString())
    return obj
}

private fun lockInfoToJson(info: LockInfo): JSONObject {
    val obj = JSONObject()
    obj.put("factory_reset_protection", info.factoryResetProtection)
    return obj
}

fun deviceReportToJson(report: DeviceReport): JSONObject {
    val obj = JSONObject()
    if (report.hasTests()) putIfNotEmpty(obj, "tests", testResultsToJson(report.tests))
    if (!report.attestation.isEmpty)
        obj.put(
            "attestation",
            Base64.encodeToString(report.attestation.toByteArray(), Base64.DEFAULT),
        )
    if (report.hasBattery()) putIfNotEmpty(obj, "battery", batteryInfoToJson(report.battery))
    if (report.hasStorage()) putIfNotEmpty(obj, "storage", storageInfoToJson(report.storage))
    obj.putIfPresent(report.hasLaunchLevel(), "launch_level", report.launchLevel)
    obj.put("locks", lockInfoToJson(report.locks))
    if (report.hasProduct()) putIfNotEmpty(obj, "product", productInfoToJson(report.product))
    return obj
}
