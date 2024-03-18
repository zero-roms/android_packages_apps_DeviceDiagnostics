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
package com.android.devicediagnostics.trusted

import android.app.Activity
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import com.android.devicediagnostics.Protos.DeviceReport

private const val TRUSTED_STATE_KEY = "trusted_state"

class TrustedState(
    var challenge: ByteArray,
    var report: DeviceReport,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.createByteArray()!!,
        DeviceReport.parseFrom(parcel.createByteArray()!!)
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByteArray(challenge)
        parcel.writeByteArray(report.toByteArray())
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TrustedState> {
        override fun createFromParcel(parcel: Parcel): TrustedState {
            return TrustedState(parcel)
        }

        override fun newArray(size: Int): Array<TrustedState?> {
            return arrayOfNulls(size)
        }

        fun fromActivity(activity: Activity): TrustedState? {
            return activity.intent.getParcelableExtra(TRUSTED_STATE_KEY, TrustedState::class.java)
        }
    }
}

fun launchTrustedActivity(from: Activity, className: String, state: TrustedState? = null) {
    val intent = Intent()
    intent.setClassName(from, className)

    var ts = state
    if (ts == null)
        ts = TrustedState.fromActivity(from)

    if (ts != null)
        intent.putExtra(TRUSTED_STATE_KEY, ts)

    from.startActivity(intent)
}