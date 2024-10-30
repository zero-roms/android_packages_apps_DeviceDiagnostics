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

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.AbstractCursor
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.android.devicediagnostics.Protos.DeviceReport
import com.android.devicediagnostics.evaluated.createAttestationRecord
import com.android.devicediagnostics.evaluated.getBatteryInfo
import com.android.devicediagnostics.evaluated.getLockInfo
import com.android.devicediagnostics.evaluated.getStorageInfo
import com.google.protobuf.ByteString
import org.json.JSONArray

private const val TAG = "GetStatus"

class GetStatusContentProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        return StatusCursor(context!!, selection)
    }

    override fun getType(uri: Uri): String? {
        Log.d(TAG, "Not implemented")
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Log.d(TAG, "Not implemented")
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        Log.d(TAG, "Not implemented")
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        Log.d(TAG, "Not implemented")
        return 0
    }

    class StatusCursor(val context: Context, val selection: String?) : AbstractCursor() {
        override fun getCount(): Int {
            return 1
        }

        override fun getColumnNames(): Array<String> {
            return arrayOf("Status")
        }

        override fun getString(column: Int): String {
            val report =
                DeviceReport.newBuilder().run {
                    setLocks(getLockInfo(context))
                    if (!locks.factoryResetProtection) {
                        setBattery(getBatteryInfo(context))
                        setStorage(getStorageInfo(context))
                        setLaunchLevel(ApplicationInterface.app.getLaunchLevel())
                        selection?.run {
                            setAttestation(
                                ByteString.copyFrom(createAttestationRecord(toByteArray()))
                            )
                        }
                    }
                    build()
                }
            val json = deviceReportToJson(report)
            if (!report.locks.factoryResetProtection) {
                val tm = context.getSystemService(TelephonyManager::class.java)!!
                val imeis = JSONArray()
                for (i in 0 until tm.activeModemCount) {
                    try {
                        imeis.put(tm.getImei(i))
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not get device identifiers", e)
                        break
                    }
                }
                json.put("imeis", imeis)
                json.put("serial", Build.getSerial())
            }
            return json.toString(2)
        }

        override fun getShort(column: Int): Short {
            TODO("Not implemented")
        }

        override fun getInt(column: Int): Int {
            TODO("Not implemented")
        }

        override fun getLong(column: Int): Long {
            TODO("Not implemented")
        }

        override fun getFloat(column: Int): Float {
            TODO("Not implemented")
        }

        override fun getDouble(column: Int): Double {
            TODO("Not implemented")
        }

        override fun isNull(column: Int): Boolean {
            TODO("Not implemented")
        }
    }
}
