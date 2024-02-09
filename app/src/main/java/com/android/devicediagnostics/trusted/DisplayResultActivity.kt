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

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.R
import com.android.devicediagnostics.TestStatus
import com.android.devicediagnostics.getAttestation
import com.google.android.attestation.ParsedAttestationRecord
import java.io.IOException

const val ATTESTATION = "ATTESTATION"
private const val TAG = "DisplayResultActivity"

class DisplayResultFragment(private var testStatus: TestStatus) : PreferenceFragmentCompat() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_trusted_display_result, rootKey)

    findPreference<Preference>("screen")?.summary = if(testStatus.screenTest) "Good" else "Bad"
    findPreference<Preference>("touch")?.summary = if(testStatus.touchTest) "Good" else "Bad"

    findPreference<Preference>("done")?.setOnPreferenceClickListener{
      Intent(activity, QrCodeDisplayActivity::class.java).also {
        requireActivity().finishAffinity()
      }
      true
    }

    Thread {
      var attestationRecord: ParsedAttestationRecord? = null
      try {
        attestationRecord = getAttestation(testStatus.attestation)
      } catch (e : IOException) {
        Log.e(TAG, "Failed to get attestation record")
        Log.e(TAG, e.toString())
      }
      requireActivity().runOnUiThread {
        if (attestationRecord != null)
          onAttestationReceived(attestationRecord)
        else
          onAttestationError()
      }
    }.start()
  }

  private fun onAttestationReceived(attestationRecord: ParsedAttestationRecord) {
    if (!attestationRecord.attestationChallenge.contentEquals(testStatus.challenge)) {
      findPreference<Preference>("failure")?.also {
        it.summary = resources.getString(R.string.attestation_challenge_does_not_match) +
                "\n" + attestationRecord.attestationChallenge.toString(Charsets.UTF_8) +
                "\n" + testStatus.challenge.toString(Charsets.UTF_8)
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
          attestationRecord.teeEnforced.attestationIdSecondImei.get().toString(Charsets.UTF_8)
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
        startActivity(Intent(requireActivity(), DisplayAttestationDetailsActivity::class.java)
                .putExtra (ATTESTATION, com.android.devicediagnostics.toString(attestationRecord)))
        true
      }
      it.isVisible = true
    }
  }

  private fun onAttestationError() {
    findPreference<Preference>("failure")?.also {
      it.summary = "Failed to get attestation"
      it.isVisible = true
    }
  }
}

class DisplayResultActivity : FragmentActivity(R.layout.activity_one_fragment) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (savedInstanceState == null) {
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(R.id.fragment_container_view,
            DisplayResultFragment(TestStatus(intent!!.extras!!)))
      }
    }
  }
}