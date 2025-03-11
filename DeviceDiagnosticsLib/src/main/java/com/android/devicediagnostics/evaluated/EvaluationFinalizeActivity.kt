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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.ApplicationInterface
import com.android.devicediagnostics.AttestationController
import com.android.devicediagnostics.DisplayResultFragment
import com.android.devicediagnostics.MainActivity
import com.android.devicediagnostics.Protos.DeviceReport
import com.android.devicediagnostics.R
import com.android.devicediagnostics.runInBackground
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

private const val TAG = "EvaluationFinalize"

class EvaluationFinalizeFragment(private val ok: Boolean) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_evaluated_done, rootKey)
        findPreference<Preference>("Done")!!.also {
            if (!ok) {
                it.setTitle(R.string.bluetooth_error_title)
                it.setSummary(R.string.bluetooth_error_summary)
            }
            it.setOnPreferenceClickListener {
                val intent = Intent(activity, MainActivity::class.java)
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                activity!!.startActivity(intent)
                activity!!.finish()
                true
            }
        }
    }
}

class EvaluationFinalizeActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.evaluated_finalize)
        setTitle(R.string.evaluation_mode_title)

        if (savedInstanceState == null) {
            showProgressIndicator(R.string.evaluated_finalizing_report)
            buildReport()
        }
    }

    private fun buildReport() {
        val state = TestState.fromActivity(this)!!

        // Launch in separate thread since gathering attestation takes time and we don't want to
        // block the UI from appearing
        runInBackground {
            val reportBuilder = DeviceReport.newBuilder()

            reportBuilder.setTests(state.testResults)

            val batteryInfo = getBatteryInfo(this)
            val storageInfo = getStorageInfo(this)
            val launchLevel = ApplicationInterface.app.getLaunchLevel()

            val attestation = createAttestationRecord(state.trustedDevice.challenge.toByteArray())

            val bluetoothClient = ApplicationInterface.app.getBluetoothClient()
            val connectionData = bluetoothClient.connectionData
            if (connectionData != null && connectionData.version == 4) {
                reportBuilder.setOldAttestation(attestation.certificates)
            } else {
                reportBuilder.setAttestation(attestation)
            }

            reportBuilder.setBattery(batteryInfo)
            reportBuilder.setStorage(storageInfo)
            reportBuilder.setLaunchLevel(launchLevel)

            val report = reportBuilder.build()

            runOnUiThread { onReportReady(report) }
        }
    }

    private fun onReportReady(report: DeviceReport) {
        val confirmation = showHeaderLayout(R.layout.evaluated_final_confirmation)
        val button = confirmation.findViewById<Button>(R.id.send_button)
        button!!.setOnClickListener {
            sendReport(report)
            true
        }

        val state = TestState.fromActivity(this)!!
        val attestationController =
            AttestationController(state.trustedDevice.challenge.toByteArray(), true)

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.fragment_container_view,
                DisplayResultFragment(report, attestationController),
            )
        }
    }

    private fun sendReport(report: DeviceReport) {
        showProgressIndicator(R.string.sending_report)

        runInBackground {
            val bluetoothClient = ApplicationInterface.app.getBluetoothClient()
            try {
                bluetoothClient.sendReport(report)
                runOnUiThread { onReportSent(true) }
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth error: $e")
                runOnUiThread { onReportSent(false) }
            }
        }

        supportFragmentManager.commit {
            hide(supportFragmentManager.findFragmentById(R.id.fragment_container_view)!!)
        }
    }

    private fun onReportSent(sent: Boolean) {
        hideHeaderLayout()
        supportFragmentManager.commit {
            replace(R.id.fragment_container_view, EvaluationFinalizeFragment(sent))
        }
    }

    private fun showHeaderLayout(layoutId: Int): View {
        val inflater = LayoutInflater.from(this)
        val container = findViewById<LinearLayout>(R.id.header_container)!!

        val view = inflater.inflate(layoutId, container, false)

        container.removeAllViews()
        container.addView(view)
        container.visibility = View.VISIBLE
        return view
    }

    private fun hideHeaderLayout() {
        val container = findViewById<LinearLayout>(R.id.header_container)!!
        container.visibility = View.GONE
    }

    private fun showProgressIndicator(stringResId: Int) {
        val progress = showHeaderLayout(R.layout.progress_indicator)
        val text = progress.findViewById<TextView>(R.id.progress_explainer)
        text!!.setText(stringResId)
    }
}
