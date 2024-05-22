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

import android.app.Activity
import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.test.espresso.intent.Intents
import androidx.test.platform.app.InstrumentationRegistry
import com.android.devicediagnostics.bluetooth.BluetoothClient
import com.android.devicediagnostics.bluetooth.BluetoothServer
import org.junit.After
import org.junit.Before
import org.mockito.Mockito
import org.mockito.kotlin.whenever

open class ActivityTest {
    private var oldApp: ApplicationInterface = ApplicationInterface.app
    protected var handler = Handler(Looper.getMainLooper())

    @Before
    fun setupMocks() {
        Intents.init()

        ApplicationInterface.setApp(Mockito.mock(ApplicationInterface::class.java))
        whenever(app.getLaunchLevel()).thenReturn(Build.VERSION_CODES.VANILLA_ICE_CREAM)

        val btClient = Mockito.mock(BluetoothClient::class.java)
        whenever(app.getBluetoothClient()).thenReturn(btClient)

        val btServer = Mockito.mock(BluetoothServer::class.java)
        whenever(app.getBluetoothServer()).thenReturn(btServer)
    }

    @After
    fun teardownMocks() {
        ApplicationInterface.setApp(oldApp)
        Intents.release()
    }

    protected val app: ApplicationInterface
        get() = ApplicationInterface.app
}

inline fun getVisibleActivity(): String? {
    val am =
        InstrumentationRegistry.getInstrumentation().context.getSystemService(ACTIVITY_SERVICE)
            as ActivityManager
    return am.appTasks[0].taskInfo.topActivity?.className
}

inline fun <reified T : Activity> waitForActivity() {
    val timeout = 5000
    val startTime = System.currentTimeMillis()
    while (true) {
        val actual = getVisibleActivity()
        if (actual == T::class.java.name) return
        Thread.sleep(50)
        if (System.currentTimeMillis() - startTime >= timeout) {
            throw AssertionError(
                "Activity ${T::class.java.simpleName} not visible after $timeout milliseconds, " +
                    "got $actual"
            )
        }
    }
}
