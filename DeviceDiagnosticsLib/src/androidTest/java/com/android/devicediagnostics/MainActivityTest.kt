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
import android.view.View
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.devicediagnostics.Protos.DeviceReport
import kotlin.concurrent.withLock
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

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
      val coordinates = floatArrayOf(view.width.toFloat() / 2 + location[0],
                                               view.height.toFloat() / 2 + location[1])
      val precision = floatArrayOf(1f, 1f)

      // Send down event, pause, and send up
      val down = MotionEvents.sendDown(uiController, coordinates, precision).down
      uiController.loopMainThreadForAtLeast(50)
      MotionEvents.sendUp(uiController, down, coordinates)
    }
  }
}

@RunWith(AndroidJUnit4::class)
class MainActivityKtTest {

  @Test
  fun testEvaluationFlow() {
    val activityScenario = launch(MainActivity::class.java)
    activityScenario.onActivity {activity : Activity ->
      assertEquals(activity.title, "Device Diagnostics")
    }
    onView(withText("Evaluation mode")).perform(click())
    onView(withText("Evaluated Device")).check(matches(isDisplayed()))
    onView(withText("Evaluated Device")).perform(click())
    onView(withText("Continue")).check(matches(isDisplayed()))
    onView(withText("Continue")).perform(click())
    Thread.sleep(100)
    onView(withId(R.id.mainLayout)).perform(touchDownAndUp());
    Thread.sleep(100)
    onView(withId(R.id.mainLayout)).perform(touchDownAndUp());
    Thread.sleep(100)
    onView(withId(R.id.mainLayout)).perform(touchDownAndUp());
    Thread.sleep(100)
    onView(withText("No obvious defects")).check(matches(isDisplayed()))
    onView(withText("Defective in some way")).check(matches(isDisplayed()))
    onView(withText("No obvious defects")).perform(click())
    onView(withText("Continue")).check(matches(isDisplayed()))
    onView(withText("Continue")).perform(click())
    Thread.sleep(200)
    onView(withId(R.id.touchTest)).perform(touchDownAndUp())
    Thread.sleep(100)
    onView(withText("The touch sensor test failed")).check(matches(isDisplayed()))
    onView(withText("Continue")).perform(click())
    Thread.sleep(100)
    onView(withText("Evaluation complete")).check(matches(isDisplayed()))
    Thread.sleep(1000)

    while((getApplicationContext() as TestApplication).writtenData == null)
      Thread.sleep(1000)
    val report = DeviceReport.parseFrom((getApplicationContext() as TestApplication).writtenData)
    assert(report.tests.screenTest)
    assert(!report.tests.touchTest)
  }

  private fun notifyMock () {
    (getApplicationContext() as TestApplication).lock.withLock() {
      (getApplicationContext() as TestApplication).condition.signal()
    }
  }
  @Test
  fun testTrustedFlow() {
    val activityScenario = launch(MainActivity::class.java)
    activityScenario.onActivity {activity : Activity ->
      assertEquals(activity.title, "Device Diagnostics")
    }
    onView(withText("Evaluation mode")).perform(click())
    onView(withText("Trusted Device")).check(matches(isDisplayed()))
    onView(withText("Trusted Device")).perform(click())
    onView(withText("Scan now")).check(matches(isDisplayed()))
    Thread.sleep(200)
    notifyMock()
    Thread.sleep(200)
    onView(withText("Continue on the device being evaluated")).check(matches(isDisplayed()))
    Thread.sleep(200)
    notifyMock()
    Thread.sleep(200)
    onView(withText("Pass")).check(matches(isDisplayed()))
    onView(withText("Fail")).check(matches(isDisplayed()))
    onView(withText("Retry attestation")).check(matches(isDisplayed()))
  }
}