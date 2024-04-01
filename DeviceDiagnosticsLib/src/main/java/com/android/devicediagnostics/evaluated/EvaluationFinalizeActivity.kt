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

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.ApplicationInterface
import com.android.devicediagnostics.MainActivity
import com.android.devicediagnostics.Protos.DeviceReport
import com.android.devicediagnostics.R
import com.android.devicediagnostics.runInBackground
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.google.protobuf.ByteString

class EvaluationFinalizeFragment() : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_evaluated_qr_display, rootKey)
        findPreference<Preference>("Done")?.setOnPreferenceClickListener {
            val intent = Intent(activity, MainActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            activity!!.startActivity(intent)
            activity!!.finish()
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Launch in separate thread since gathering attestation takes time and we don't want to block
        // the UI from appearing
        runInBackground {
            val reportBuilder = DeviceReport.newBuilder()

            val state = TestState.fromActivity(activity!!)!!
            reportBuilder.setTests(state.testResults)

            val attestation = createAttestationRecord(state.trustedDevice.challenge.toByteArray())
            reportBuilder.setAttestation(ByteString.copyFrom(attestation))
            reportBuilder.setBattery(getBatteryInfo(activity!!))
            reportBuilder.setStorage(getStorageInfo(activity!!))

            val report = reportBuilder.build()

            val bluetoothClient = (requireActivity().applicationContext as ApplicationInterface)
                .getBluetoothClient()
            bluetoothClient.write(report.toByteArray())
        }
    }
}

class EvaluationFinalizeActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_fragment)
        setTitle(R.string.evaluation_mode_title)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(
                    R.id.fragment_container_view,
                    EvaluationFinalizeFragment()
                )
            }
        }
    }
}
