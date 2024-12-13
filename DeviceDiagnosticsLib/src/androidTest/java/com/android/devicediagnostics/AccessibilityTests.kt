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
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.devicediagnostics.evaluated.ScreenTestIntroActivity
import com.android.devicediagnostics.evaluated.TouchTestIntroActivity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class AccessibilityTests : ActivityTest() {
    @Before
    fun setupAccessibilityMocks() {
        whenever(app.isUsingScreenReader(any())).thenReturn(true)
    }

    @Test
    fun testScreenTestContinue() {
        val activityScenario = launch(ScreenTestIntroActivity::class.java)
        activityScenario.onActivity { activity: Activity ->
            assertEquals(activity.title, "Display test")
        }

        onView(withText(R.string.cancel)).check(matches(isDisplayed()))
        onView(withText(R.string.visual_test_warning_summary)).check(matches(isDisplayed()))
        onView(withText(R.string.proceed)).perform(click())
        intended(hasComponent(ScreenTestIntroActivity::class.java.name))
        onView(withText(R.string.activity_continue)).check(matches(isDisplayed()))
    }

    @Test
    fun testTouchTestContinue() {
        val activityScenario = launch(TouchTestIntroActivity::class.java)
        activityScenario.onActivity { activity: Activity ->
            assertEquals(activity.title, "Touch test")
        }

        onView(withText(R.string.cancel)).check(matches(isDisplayed()))
        onView(withText(R.string.visual_test_warning_summary)).check(matches(isDisplayed()))
        onView(withText(R.string.proceed)).perform(click())
        intended(hasComponent(TouchTestIntroActivity::class.java.name))
        onView(withText(R.string.activity_continue)).check(matches(isDisplayed()))
    }
}
