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
package com.android.devicediagnostics.commands

import android.app.ActivityManager
import android.app.ContentProviderHolder
import android.content.AttributionSource
import android.content.ContentResolver
import android.content.IContentProvider
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.os.ResultReceiver
import android.os.SystemProperties
import android.os.UserHandle
import java.io.FileDescriptor
import kotlin.system.exitProcess

class Tokenizer(private val args: Array<String>) {
    private var cursor = 0

    fun next(): String? {
        if (cursor >= args.size) return null
        return args[cursor++]
    }

    fun more(): Boolean {
        return cursor < args.size
    }
}

fun isDebuggable(): Boolean {
    return SystemProperties.getInt("ro.debuggable", 0) == 1
}

fun isTradeInModeEnabled(): Boolean {
    return SystemProperties.getInt("persist.adb.tradeinmode", 0) > 0
}

fun isBootCompleted(): Boolean {
    return SystemProperties.getInt("sys.boot_completed", 0) != 0
}

fun ensureTradeInModeAllowed() {
    if (!isDebuggable() && !isTradeInModeEnabled()) {
        throw Exception("Command not available.")
    }
}

class Commands {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                doMain(Tokenizer(args))
            } catch (e: Exception) {
                System.err.println("Error: $e")
                exitProcess(1)
            }
        }
    }
}

fun doMain(args: Tokenizer) {
    var cmd = args.next()
    if (cmd == null) {
        throw IllegalArgumentException("Expected command.")
    }

    // Optional wait-until-ready prefix to make testing easier.
    if (cmd == "wait-until-ready") {
        doWaitUntilReady()

        cmd = args.next()
        if (cmd == null) {
            return
        }
    }

    if (!isBootCompleted()) {
        throw Exception("Device not fully booted")
    }

    ensureTradeInModeAllowed()

    if (cmd == "getstatus") {
        doGetStatus(args)
    } else if (cmd == "evaluate") {
        doEvaluate(args)
    } else if (cmd == "poweroff") {
        SystemProperties.set("sys.powerctl", "shutdown")
    } else {
        throw IllegalArgumentException("Unknown command.")
    }
}

fun callingPackage(): String? {
    return when (Process.myUid()) {
        Process.ROOT_UID -> "root"
        Process.SHELL_UID -> "com.android.shell"
        else -> null
    }
}

fun doEvaluate(args: Tokenizer) {
    if (args.more()) {
        throw IllegalArgumentException("Unexpected argument.")
    }

    val am = ActivityManager.getService()
    if (am == null) {
        throw Exception("ActivityManager is not available.")
    }

    val path = "com.android.devicediagnostics/.EnterEvaluationMode"
    val shellArgs = arrayOf("start", "-n", path)
    am.asBinder()
        .shellCommand(
            FileDescriptor.`in`,
            FileDescriptor.out,
            FileDescriptor.err,
            shellArgs,
            null,
            ResultReceiver(null),
        )
}

fun queryGetStatusProvider(provider: IContentProvider, uri: Uri, extras: Bundle): Int {
    val cursor =
        provider.query(
            AttributionSource(Binder.getCallingUid(), callingPackage(), null),
            uri,
            arrayOf<String>(),
            extras,
            null,
        )
    if (cursor == null) {
        System.err.println("No result found.")
        return 1
    }
    try {
        if (!cursor.moveToFirst()) {
            System.err.println("No result found.")
            return 1
        }
        if (cursor.getColumnCount() < 1) {
            System.err.println("No result found.")
            return 1
        }
        if (cursor.getType(0) != Cursor.FIELD_TYPE_STRING) {
            System.err.println("No result found.")
            return 1
        }
        println(cursor.getString(0))
    } finally {
        cursor.close()
    }
    return 0
}

fun doGetStatus(args: Tokenizer) {
    val extras = Bundle()

    val arg = args.next()
    if (arg == "--challenge") {
        val challenge = args.next()
        if (challenge == null) {
            throw IllegalArgumentException("Expected challenge.")
        }
        extras.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, challenge)
    } else if (arg != null) {
        throw IllegalArgumentException("Unexpected argument.")
    }

    if (args.more()) {
        throw IllegalArgumentException("Unexpected argument.")
    }

    val am = ActivityManager.getService()
    if (am == null) {
        throw Exception("ActivityManager is not available.")
    }

    val token = Binder()
    val uriString = "content://com.android.devicediagnostics/.GetStatusContentProvider"
    val uri = Uri.parse(uriString)
    var holder: ContentProviderHolder? = null
    try {
        holder =
            am.getContentProviderExternal(uri.authority, UserHandle.USER_SYSTEM, token, "*cmd*")
        if (holder == null) {
            throw Exception("Could not find provider: " + uri.authority)
        }
        queryGetStatusProvider(holder.provider, uri, extras)
    } finally {
        if (holder != null && holder.provider != null) {
            am.removeContentProviderExternalAsUser(uri.authority, token, UserHandle.USER_SYSTEM)
        }
    }
}

fun isTradeInModeReady(): Boolean {
    if (!isBootCompleted()) {
        return false
    }

    val am = ActivityManager.getService()
    if (am == null) {
        return false
    }

    val token = Binder()
    val uriString = "content://com.android.devicediagnostics/.GetStatusContentProvider"
    val uri = Uri.parse(uriString)
    var holder: ContentProviderHolder? = null
    try {
        holder =
            am.getContentProviderExternal(uri.authority, UserHandle.USER_SYSTEM, token, "*cmd*")
        return holder != null && holder.provider != null
    } catch (e: Exception) {
        return false
    } finally {
        if (holder != null && holder.provider != null) {
            am.removeContentProviderExternalAsUser(uri.authority, token, UserHandle.USER_SYSTEM)
        }
    }
}

fun doWaitUntilReady() {
    while (!isTradeInModeReady()) {
        Thread.sleep(500)
    }
}
