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
package com.android.devicediagnostics.evaluated

import android.app.Activity
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import com.android.devicediagnostics.DiagnosticsActivity
import com.android.devicediagnostics.Protos.TestResults
import com.android.devicediagnostics.Protos.TrustedDeviceInfo

// When running through a trusted/evaluated test sequence, this key is used to communicate
// test state between activities.
private const val TEST_STATE_KEY = "test_state"

// Helper class for currying data between activities. This is only constructed when running
// multiple tests in succession.
class TestState(
    var testResults: TestResults,
    var trustedDevice: TrustedDeviceInfo,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        testResults = TestResults.parseFrom(parcel.createByteArray()),
        trustedDevice = TrustedDeviceInfo.parseFrom(parcel.createByteArray())) {
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByteArray(testResults.toByteArray())
        dest.writeByteArray(trustedDevice.toByteArray())
    }

    override fun describeContents(): Int { return 0 }

    companion object CREATOR : Parcelable.Creator<TestState> {
        override fun createFromParcel(parcel: Parcel): TestState {
            return TestState(parcel)
        }

        override fun newArray(size: Int): Array<TestState?> {
            return arrayOfNulls(size)
        }

        fun fromActivity(activity: Activity): TestState? {
            return activity.intent.getParcelableExtra(TEST_STATE_KEY, TestState::class.java)
        }
    }
}

fun startFullTestFlow(from: Activity, trustedDeviceInfo: TrustedDeviceInfo) {
    nextTestActivity(from, ScreenTestIntroActivity::class.java.name) {
        val results = TestResults.getDefaultInstance()
        val state = TestState(results, trustedDeviceInfo)
        it.putExtra(TEST_STATE_KEY, state)
    }
}

// Advance through test subactivities (eg TouchTestIntro -> TouchTest).
fun nextTestActivity(from: Activity, nextClass: String,
                     withIntent: ((intent: Intent) -> Unit)? = null) {
    val intent = Intent()
    intent.setClassName(from, nextClass)

    val state = TestState.fromActivity(from)
    if (state != null)
        intent.putExtra(TEST_STATE_KEY, state)
    if (withIntent != null)
        withIntent(intent)

    from.startActivity(intent)
}

// Advance to the next activity group in the test flow, concluding the current test.
fun nextTest(from: Activity, withReport: (builder: TestResults.Builder) -> Unit) {
    if (isOneShotTest(from)) {
        Intent(from, DiagnosticsActivity::class.java).also {
            it.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            from.startActivity(it)
            from.finish()
        }
        return
    }

    val state = TestState.fromActivity(from)!!
    val builder = state.testResults.toBuilder()
    if (withReport != null)
        withReport(builder)
    state.testResults = builder.build()

    val nextActivityClass =
        when (from.javaClass.name) {
            ScreenTestFinalizeActivity::class.java.name -> TouchTestIntroActivity::class.java.name
            TouchTestFinalizeActivity::class.java.name -> EvaluationFinalizeActivity::class.java.name
            else -> null
        }

    val intent = Intent()
    intent.setClassName(from, nextActivityClass!!)
    intent.putExtra(TEST_STATE_KEY, state)

    // Don't allow navigating back once we're submitting the results.
    if (nextActivityClass == EvaluationFinalizeActivity::class.java.name)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK.or(Intent.FLAG_ACTIVITY_CLEAR_TASK)

    from.startActivity(intent)
}

// Returns whether the current activity is part of one-shot test flow.
fun isOneShotTest(from: Activity): Boolean {
    val extras = from.intent.extras
    if (extras == null)
        return true
    return !extras.containsKey(TEST_STATE_KEY)
}