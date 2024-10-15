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
import android.content.Context.PERSISTENT_DATA_BLOCK_SERVICE
import android.service.persistentdata.PersistentDataBlockManager
import com.android.devicediagnostics.Protos.LockInfo

fun getLockInfo(context: Context): LockInfo {
    val builder = LockInfo.newBuilder()
    val pdb = context.getSystemService(PERSISTENT_DATA_BLOCK_SERVICE) as? PersistentDataBlockManager
    if (pdb == null) return builder.build()
    try {
        builder.setFactoryResetProtection(pdb.isFactoryResetProtectionActive())
    } catch (e: Exception) {}
    return builder.build()
}
