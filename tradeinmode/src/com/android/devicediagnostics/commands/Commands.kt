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

const val STATE_PROP = "persist.adb.tradeinmode"

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
    return SystemProperties.getInt(STATE_PROP, 0) > 0
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

    if (cmd == "testing" && isDebuggable()) {
        doTesting(args)
        return
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
    } else if (cmd == "reboot") {
        SystemProperties.set("sys.powerctl", "reboot")
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

fun startActivity(activity: String) {
    val am = ActivityManager.getService()
    if (am == null) {
        throw Exception("ActivityManager is not available.")
    }

    val shellArgs = arrayOf("start", "-n", activity)
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

fun doTesting(args: Tokenizer) {
    if (!args.more()) {
        throw IllegalArgumentException("Expected argument.")
    }

    val uriString = "content://com.android.devicediagnostics.TradeInModeTestingContentProvider"

    val cmd = args.next()

    if (cmd == "status") {
        val result = queryStringContentProvider(uriString, cmd)
        println(result)
        return
    }

    var reboot: Boolean = false
    if (cmd == "start") {
        println("Device will reboot in trade-in mode.")
        reboot = true
    } else if (cmd == "wipe") {
        println("Device will reboot to wipe userdata.")
        reboot = true
    } else if (cmd == "stop") {
        println("Device will restart adb.")
    } else {
        throw IllegalArgumentException("Unknown argument.")
    }

    val result = queryStringContentProvider(uriString, cmd)
    if (result != "ok") {
        throw Exception("Failed to query testing content provider")
    }

    if (reboot) {
        SystemProperties.set("sys.powerctl", "reboot")
    }
}

fun doEvaluate(args: Tokenizer) {
    if (args.more()) {
        throw IllegalArgumentException("Unexpected argument.")
    }

    val uriString = "content://com.android.devicediagnostics.EvaluateContentProvider"
    queryStringContentProvider(uriString, null)
    println("Entering evaluation mode.")
}

fun doGetStatus(args: Tokenizer) {
    val arg = args.next()
    var challenge: String? = null
    if (arg == "--challenge") {
        challenge = args.next()
        if (challenge == null) {
            throw IllegalArgumentException("Expected challenge.")
        }
    } else if (arg != null) {
        throw IllegalArgumentException("Unexpected argument.")
    }

    if (args.more()) {
        throw IllegalArgumentException("Unexpected argument.")
    }

    val uriString = "content://com.android.devicediagnostics.GetStatusContentProvider"
    val result = queryStringContentProvider(uriString, challenge)
    if (result == null) {
        System.err.println("No result found.")
        return
    }
    println(result)
}

fun queryStringContentProvider(uriString: String, selection: String?): String? {
    val am = ActivityManager.getService()
    if (am == null) {
        throw Exception("ActivityManager is not available.")
    }

    val extras = Bundle()
    if (selection != null) {
        extras.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
    }

    val token = Binder()
    val uri = Uri.parse(uriString)
    var holder: ContentProviderHolder? = null
    try {
        holder =
            am.getContentProviderExternal(uri.authority, UserHandle.USER_SYSTEM, token, "*cmd*")
        if (holder == null) {
            throw Exception("Could not find provider: " + uri.authority)
        }
        val cursor =
            holder.provider.query(
                AttributionSource(Binder.getCallingUid(), callingPackage(), null),
                uri,
                arrayOf<String>(),
                extras,
                null,
            )
        if (cursor == null) {
            return null
        }
        try {
            if (!cursor.moveToFirst()) {
                return null
            }
            if (cursor.getColumnCount() < 1) {
                return null
            }
            if (cursor.getType(0) != Cursor.FIELD_TYPE_STRING) {
                return null
            }
            return cursor.getString(0)
        } finally {
            cursor.close()
        }
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
    val uriString = "content://com.android.devicediagnostics.GetStatusContentProvider"
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
