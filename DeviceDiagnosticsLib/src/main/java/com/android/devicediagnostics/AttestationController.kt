/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.devicediagnostics

import android.app.Activity
import com.android.devicediagnostics.Protos.DeviceReport
import com.google.android.attestation.ParsedAttestationRecord

private const val TAG = "Attestation"

class AttestationController(val challenge: ByteArray, val selfCheck: Boolean) {
    interface Callbacks {
        fun onAttestationReceived(result: Pair<ParsedAttestationRecord, AttestationResult>)

        fun onAttestationRetry()

        fun onAttestationError()
    }

    // If "selfCheck" is true, we allow network verification to soft fail.
    fun verifyAttestation(activity: Activity, report: DeviceReport, callbacks: Callbacks) {
        // Attestation check does a network lookup, so must be on separate thread
        runInBackground {
            var attestation =
                ApplicationInterface.app.verifyAttestation(
                    report.attestation.toByteArray(),
                    challenge,
                )
            if (attestation.second == AttestationResult.NETWORK_ERROR && selfCheck)
                attestation = Pair(attestation.first, AttestationResult.SKIPPED_VERIFICATION)

            activity.runOnUiThread {
                if (attestation.second == AttestationResult.GENERIC_ERROR) {
                    callbacks.onAttestationError()
                } else if (attestation.second == AttestationResult.NETWORK_ERROR) {
                    callbacks.onAttestationRetry()
                } else {
                    callbacks.onAttestationReceived(Pair(attestation.first!!, attestation.second))
                }
            }
        }
    }
}
