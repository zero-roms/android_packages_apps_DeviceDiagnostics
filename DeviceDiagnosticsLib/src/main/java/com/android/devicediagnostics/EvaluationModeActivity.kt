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
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.bluetooth.BluetoothServer
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class EvaluationModeFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.evaluation_mode_landing, rootKey)
    }

    override fun onResume() {
        super.onResume()

        val enabled = BluetoothServer.bluetoothEnabled
        findPreference<Preference>("trusted_device")!!.isVisible = enabled
        findPreference<Preference>("evaluation_mode")!!.isVisible = enabled
        findPreference<Preference>("bluetooth_disabled")!!.also {
            it.isVisible = !enabled
            it.setOnPreferenceClickListener {
                startActivity(Intent().setAction(Settings.ACTION_BLUETOOTH_SETTINGS))
                true
            }
        }
    }
}

class EvaluationModeActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_fragment)
        setTitle(R.string.evaluation_mode_title)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, EvaluationModeFragment())
            }
        }
    }
}