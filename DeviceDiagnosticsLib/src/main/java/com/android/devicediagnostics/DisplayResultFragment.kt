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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.trusted.ATTESTATION
import com.android.devicediagnostics.trusted.DisplayAttestationDetailsActivity
import com.android.devicediagnostics.trusted.QrCodeDisplayActivity
import com.android.devicediagnostics.trusted.TrustedState
import com.google.android.attestation.ParsedAttestationRecord
import java.io.IOException

private const val TAG = "DisplayResultFragment"

class DisplayResultFragment(private var state : TrustedState) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_trusted_display_result, rootKey)

        val report = state.report
        val tests = report.tests

        val pass = getString(R.string.pass)
        val fail = getString(R.string.fail)

        findPreference<Preference>("screen")?.summary = if (tests.screenTest) pass else fail
        findPreference<Preference>("touch")?.summary = if (tests.touchTest) pass else fail

        findPreference<Preference>("evaluate_another")?.setOnPreferenceClickListener {
            val intent = Intent(activity, QrCodeDisplayActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            activity!!.startActivity(intent)
            activity!!.finish()
            true
        }

        findPreference<Preference>("done")?.setOnPreferenceClickListener {
            val intent = Intent(activity, MainActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            activity!!.startActivity(intent)
            activity!!.finish()
            true
        }

        findPreference<Preference>("retry_failure")?.also {
            it.setOnPreferenceClickListener {
                verifyAttestation(report.attestation.toByteArray())
                true
            }
        }

        verifyAttestation(state.report.attestation.toByteArray())
    }

    private fun verifyAttestation(bytes: ByteArray) {
        // Attestation check does a network lookup, so must be on separate thread
        runInBackground {
            if (bytes.isEmpty()) {
                onAttestationError()
            } else {
                var attestationRecord: ParsedAttestationRecord? = null
                try {
                    attestationRecord = getAttestation(bytes)
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to get attestation record")
                    Log.e(TAG, e.toString())
                }
                requireActivity().runOnUiThread {
                    if (attestationRecord != null)
                        onAttestationReceived(attestationRecord)
                    else
                        onAttestationError()
                }
            }
        }
    }

    private fun onAttestationReceived(attestationRecord: ParsedAttestationRecord) {
        findPreference<Preference>("retry_failure")?.also {
            it.isVisible = false
        }

        if (!attestationRecord.attestationChallenge.contentEquals(state.challenge)) {
            findPreference<Preference>("non_retry_failure")?.also {
                it.summary = resources.getString(R.string.attestation_challenge_does_not_match) +
                    "\n" + attestationRecord.attestationChallenge.toString(Charsets.UTF_8) +
                    "\n" + state.challenge.toString(Charsets.UTF_8)
                it.isVisible = true
            }
            return
        }

        findPreference<Preference>("imei")?.also {
            if (attestationRecord.teeEnforced.attestationIdImei.isPresent) {
                it.summary =
                    attestationRecord.teeEnforced.attestationIdImei.get().toString(Charsets.UTF_8)
                it.isVisible = true
            }
        }

        findPreference<Preference>("imei2")?.also {
            if (attestationRecord.teeEnforced.attestationIdSecondImei.isPresent) {
                it.summary =
                    attestationRecord.teeEnforced.attestationIdSecondImei.get()
                        .toString(Charsets.UTF_8)
                it.isVisible = true
            }
        }

        findPreference<Preference>("serial")?.also {
            if (attestationRecord.teeEnforced.attestationIdSerial.isPresent) {
                it.summary =
                    attestationRecord.teeEnforced.attestationIdSerial.get().toString(Charsets.UTF_8)
                it.isVisible = true
            }
        }

        findPreference<Preference>("locked")?.also {
            it.summary = if (attestationRecord.teeEnforced.rootOfTrust.get().deviceLocked)
                resources.getString(R.string.locked)
            else
                resources.getString(R.string.not_locked)
            it.isVisible = true
        }
        findPreference<Preference>("trustworthy")?.also {
            it.summary = attestationRecord.teeEnforced.rootOfTrust.get().verifiedBootState.name
            it.isVisible = true
        }
        findPreference<Preference>("attestation_details")?.also {
            it.setOnPreferenceClickListener {
                startActivity(
                    Intent(requireActivity(),
                    DisplayAttestationDetailsActivity::class.java)
                    .putExtra(
                        ATTESTATION,
                        com.android.devicediagnostics.toString(attestationRecord)))
                true
            }
            if (Build.IS_USERDEBUG || Build.IS_ENG) {
                it.isVisible = true
            }
        }
    }

    private fun onAttestationError() {
        findPreference<Preference>("retry_failure")?.also {
            it.setOnPreferenceClickListener {
                verifyAttestation(state.report.attestation.toByteArray())
                true
            }
            it.isVisible = true
        }
    }
}
