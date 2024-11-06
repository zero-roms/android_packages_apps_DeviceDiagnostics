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

import android.app.usage.IStorageStatsManager
import android.content.Context
import android.os.ServiceManager
import android.os.storage.StorageManager
import com.android.devicediagnostics.Protos.StorageInfo

fun getStorageInfo(context: Context): StorageInfo {
    val builder = StorageInfo.newBuilder()

    val ssm =
        IStorageStatsManager.Stub.asInterface(
            ServiceManager.getService(Context.STORAGE_STATS_SERVICE)
        )
    val totalBytes = ssm.getTotalBytes(StorageManager.UUID_PRIVATE_INTERNAL, context.opPackageName)
    builder.setCapacityBytes(totalBytes)

    if (android.os.Flags.storageLifetimeApi()) {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        builder.setUsefulLifetimeRemaining(sm.internalStorageRemainingLifetime)
    }
    return builder.build()
}
