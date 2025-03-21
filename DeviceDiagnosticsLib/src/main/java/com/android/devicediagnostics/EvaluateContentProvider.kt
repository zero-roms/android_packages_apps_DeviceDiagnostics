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
package com.android.devicediagnostics

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.AbstractCursor
import android.database.Cursor
import android.net.Uri
import android.os.IBinder
import android.os.ITradeInMode
import android.os.ServiceManager
import android.util.Log

private const val TAG = "Evaluate"

class EvaluateContentProvider : ContentProvider() {
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
            var b: IBinder = ServiceManager.getServiceOrThrow("tradeinmode")
            var service: ITradeInMode = ITradeInMode.Stub.asInterface(b)
            if (!service.isEvaluationModeAllowed()) {
                throw IllegalStateException("Trade-in mode is not available on this device.")
            }

            if (!service.enterEvaluationMode()) {
                throw IllegalStateException("Evaluation mode is not available.")
            }

            // Dismiss suw the new way via a broadcast
            val broadcast = Intent()
            broadcast.setAction("com.google.android.setupwizard.ENTER_TRADE_IN_MODE")
            broadcast.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND)
            context.sendBroadcast(broadcast)
            return ""
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
