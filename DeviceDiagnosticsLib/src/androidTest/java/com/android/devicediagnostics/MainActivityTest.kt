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
import android.os.Build
import android.view.View
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.devicediagnostics.Protos.DeviceReport
import com.android.devicediagnostics.Protos.TestResults
import com.android.devicediagnostics.Protos.TrustedDeviceInfo
import com.android.devicediagnostics.bluetooth.BluetoothClient
import com.android.devicediagnostics.bluetooth.BluetoothServer
import com.android.devicediagnostics.evaluated.ScreenTestFinalizeActivity
import com.android.devicediagnostics.evaluated.TouchTestFinalizeActivity
import com.android.devicediagnostics.trusted.DisplayResultActivity
import com.android.devicediagnostics.trusted.WaitForResultActivity
import com.android.settingslib.qrcode.QrCamera
import com.google.android.attestation.AuthorizationList
import com.google.android.attestation.ParsedAttestationRecord
import com.google.protobuf.ByteString
import java.nio.charset.Charset
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private fun touchDownAndUp(): ViewAction? {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View> {
            return isDisplayed()
        }

        override fun getDescription(): String {
            return "Send touch events."
        }

        override fun perform(uiController: UiController, view: View) {
            // Get view absolute position
            val location = IntArray(2)
            view.getLocationOnScreen(location)

            // Offset coordinates by view position
            val coordinates =
                floatArrayOf(
                    view.width.toFloat() / 2 + location[0],
                    view.height.toFloat() / 2 + location[1]
                )
            val precision = floatArrayOf(1f, 1f)

            // Send down event, pause, and send up
            val down = MotionEvents.sendDown(uiController, coordinates, precision).down
            uiController.loopMainThreadForAtLeast(50)
            MotionEvents.sendUp(uiController, down, coordinates)
        }
    }
}

private const val QR_PAYLOAD = "{\"PSM\":134}"

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private var oldApp: ApplicationInterface = ApplicationInterface.app

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

    private val app: ApplicationInterface
        get() = ApplicationInterface.app

    @Test
    fun testEvaluationFlow() {
        val activityScenario = launch(MainActivity::class.java)
        activityScenario.onActivity { activity: Activity ->
            assertEquals(activity.title, "Device diagnostics")
        }
        onView(withText("Evaluation mode")).perform(click())
        onView(withText("Evaluated device")).check(matches(isDisplayed()))

        // Set up the mock QR code scanner.
        val qrCaptor = argumentCaptor<QrCamera.ScannerCallback>()
        val qrCamera = Mockito.mock(QrCamera::class.java)
        whenever(app.getQrCamera(any(), qrCaptor.capture())).thenAnswer { qrCamera }

        val btClient = app.getBluetoothClient()

        // Set up the mock bluetooth client.
        val challenge = ByteString.copyFrom("test challenge", Charset.defaultCharset())
        val deviceInfo = TrustedDeviceInfo.newBuilder().setChallenge(challenge).build()
        whenever(btClient.readTrustedDeviceInfo()).thenReturn(deviceInfo)

        // Move to the QrCodeScanner activity.
        onView(withText("Evaluated device")).perform(click())

        // Check that the camera was started.
        verify(qrCamera, times(1)).start(any())

        // Simulate scanning a QR code.
        val qrScanner = qrCaptor.firstValue
        qrScanner.handleSuccessfulResult(QR_PAYLOAD)

        // Check that bluetooth was started.
        val btCaptor = argumentCaptor<BluetoothClient.ScanListener>()
        verify(btClient).start(btCaptor.capture())
        btCaptor.firstValue.onBluetoothConnected()

        setupAttestation()

        onView(withText("Continue")).check(matches(isDisplayed()))
        onView(withText("Continue")).perform(click())
        onView(withId(R.id.mainLayout)).perform(touchDownAndUp())
        onView(withId(R.id.mainLayout)).perform(touchDownAndUp())
        onView(withId(R.id.mainLayout)).perform(touchDownAndUp())
        intended(hasComponent(ScreenTestFinalizeActivity::class.java.name))
        onView(withText("No obvious defects")).check(matches(isDisplayed()))
        onView(withText("Defective in some way")).check(matches(isDisplayed()))
        onView(withText("No obvious defects")).perform(click())
        onView(withText("Continue")).check(matches(isDisplayed()))
        onView(withText("Continue")).perform(click())
        onView(withId(R.id.touchTest)).check(matches(isDisplayed()))
        onView(withId(R.id.touchTest)).perform(touchDownAndUp())
        intended(hasComponent(TouchTestFinalizeActivity::class.java.name))
        onView(withText("The touch sensor test failed")).check(matches(isDisplayed()))
        onView(withText("Continue")).perform(click())
        onView(withText("Send report")).check(matches(isDisplayed()))
        onView(withText("Send report")).perform(click())

        // Verify the report was sent.
        val reportCaptor = argumentCaptor<DeviceReport>()
        verify(btClient, times(1)).sendReport(reportCaptor.capture())
        val report = reportCaptor.firstValue

        assert(report.tests.screenTest)
        assert(!report.tests.touchTest)
    }

    @Test
    fun testTrustedFlow() {
        val activityScenario = launch(MainActivity::class.java)
        activityScenario.onActivity { activity: Activity ->
            assertEquals(activity.title, "Device diagnostics")
        }

        whenever(app.getRandomBytes(any())).thenReturn(ByteArray(32))

        onView(withText("Evaluation mode")).perform(click())
        onView(withText("Trusted device")).check(matches(isDisplayed()))
        onView(withText("Trusted device")).perform(click())
        onView(withText("Scan now")).check(matches(isDisplayed()))

        // Verify that we started waiting for the bluetooth server.
        val btServer = app.getBluetoothServer()
        val callbackCaptor = argumentCaptor<() -> Unit>()
        verify(btServer, times(1)).start(any(), callbackCaptor.capture())

        // Set up the mocked test results.
        val deviceReportBuilder = DeviceReport.newBuilder()
        val testResultsBuilder = TestResults.newBuilder()
        testResultsBuilder.setScreenTest(true)
        testResultsBuilder.setTouchTest(false)
        deviceReportBuilder.setTests(testResultsBuilder)
        val deviceReport = deviceReportBuilder.build()

        // Invoke the server completion callback.
        callbackCaptor.firstValue()

        intended(hasComponent(WaitForResultActivity::class.java.name))
        waitForActivity<WaitForResultActivity>()

        onView(withText("Continue on the device being evaluated")).check(matches(isDisplayed()))

        setupAttestation()
        whenever(btServer.readDeviceReport()).thenReturn(deviceReport)

        intended(hasComponent(DisplayResultActivity::class.java.name))
        waitForActivity<DisplayResultActivity>()

        onView(withText("Pass")).check(matches(isDisplayed()))
        onView(withText("Fail")).check(matches(isDisplayed()))
    }

    private fun setupAttestation() {
        // Set up the verifyAttestation call.
        val authorizationList = AuthorizationList.builder().build()
        val attestationRecord =
            ParsedAttestationRecord.create(
                1,
                ParsedAttestationRecord.SecurityLevel.SOFTWARE,
                1,
                ParsedAttestationRecord.SecurityLevel.SOFTWARE,
                ByteArray(0),
                ByteArray(0),
                authorizationList,
                authorizationList
            )

        val attestationResult = Pair(attestationRecord, AttestationResult.UNVERIFIED)
        whenever(app.verifyAttestation(any(), any())).thenReturn(attestationResult)
    }
}
