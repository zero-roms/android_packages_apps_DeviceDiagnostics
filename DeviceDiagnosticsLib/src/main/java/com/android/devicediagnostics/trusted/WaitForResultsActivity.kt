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

import android.Manifest
import android.os.Bundle
import androidx.fragment.app.commit
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.ApplicationInterface
import com.android.devicediagnostics.R
import com.android.devicediagnostics.PermissionsHelper
import com.android.devicediagnostics.Protos.DeviceReport
import com.android.devicediagnostics.bluetooth.BluetoothServer
import com.android.devicediagnostics.runInBackground
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import java.lang.Exception

const val DEVICE_REPORT_KEY = "device_report"

class WaitForResultFragment() : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_trusted_scan, rootKey)
    }
}

class WaitForResultActivity : CollapsingToolbarBaseActivity(), BluetoothServer.ReadListener {
    private var mFragment = WaitForResultFragment()

    private val permissions = PermissionsHelper(this,
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN)
    ) { onPermissionsGranted() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_fragment)
        setTitle(R.string.evaluation_mode_title)
    }

    private fun onPermissionsGranted() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            add(R.id.fragment_container_view, mFragment)
        }
        val btServer = (applicationContext as ApplicationInterface).getBluetoothServer()
        btServer.startAsyncRead(this)
    }

    override fun onBluetoothRead(bytes: ByteArray) {
        val report = DeviceReport.parseFrom(bytes)
        runOnUiThread {
            onReportReceived(report)
        }
    }

    override fun onBluetoothError(e: Exception) {
        // :TODO: handle
    }

    fun onReportReceived(report: DeviceReport) {
        val state = TrustedState.fromActivity(this)!!
        state.report = report
        launchTrustedActivity(this, DisplayResultActivity::class.java.name, state)
        finish()
    }

    override fun onResume() {
        super.onResume()
        permissions.requestPermissions()
    }
}
