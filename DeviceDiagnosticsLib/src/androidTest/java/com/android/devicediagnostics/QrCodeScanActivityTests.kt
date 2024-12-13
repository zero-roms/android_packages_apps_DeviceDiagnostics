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
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.devicediagnostics.bluetooth.BluetoothServer
import com.android.devicediagnostics.trusted.QrCodeDisplayActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class QrCodeScanActivityTests : ActivityTest() {
    @Test
    fun testBluetoothError() {
        val challenge = ByteArray(32)
        whenever(app.getRandomBytes(any())).thenReturn(challenge)

        val activityScenario = launch(QrCodeDisplayActivity::class.java)
        activityScenario.onActivity { activity: Activity ->
            assertEquals(activity.title, getString(R.string.evaluation_mode_title))
        }

        // Verify that we started waiting for the bluetooth server.
        val btServer = app.getBluetoothServer()
        val callbackCaptor = argumentCaptor<BluetoothServer.StartListener>()
        verify(btServer, times(1)).start(any(), callbackCaptor.capture())

        val listener = callbackCaptor.firstValue
        Handler(Looper.getMainLooper()).post() { listener.onServerError() }

        onView(withText(R.string.bluetooth_error_title)).check(matches(isDisplayed()))
        onView(withText(R.string.bluetooth_error_summary)).check(matches(isDisplayed()))
    }

    private fun getString(id: Int): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.resources.getString(id)
    }
}
