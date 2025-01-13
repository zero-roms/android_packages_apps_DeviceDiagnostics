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
import android.os.IBinder
import android.os.ITradeInMode
import android.os.ServiceManager
import android.util.Slog
import com.android.tradeinmode.flags.Flags.enableTradeInMode

private const val TAG = "TradeInMode"

class TradeInModeTestingContentProvider : ContentProvider() {
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
        return TestingCursor(context!!, selection)
    }

    override fun getType(uri: Uri): String? {
        Slog.d(TAG, "Not implemented")
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Slog.d(TAG, "Not implemented")
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        Slog.d(TAG, "Not implemented")
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        Slog.d(TAG, "Not implemented")
        return 0
    }

    class TestingCursor(val context: Context, val selection: String?) : AbstractCursor() {
        override fun getCount(): Int {
            return 1
        }

        override fun getColumnNames(): Array<String> {
            return arrayOf("Status")
        }

        override fun getString(column: Int): String {
            if (!enableTradeInMode()) {
                throw Exception("Trade-in mode flag not enabled")
            }

            var b: IBinder = ServiceManager.getServiceOrThrow("tradeinmode")
            var service: ITradeInMode = ITradeInMode.Stub.asInterface(b)
            if (selection == "start") {
                service.startTesting()
            } else if (selection == "wipe") {
                service.scheduleWipeForTesting()
            } else if (selection == "stop") {
                service.stopTesting()
            } else if (selection == "status") {
                if (service.isTesting()) {
                    return "testing"
                }
                return "none"
            } else {
                throw Exception("Invalid selection: " + selection)
            }
            return "ok"
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
