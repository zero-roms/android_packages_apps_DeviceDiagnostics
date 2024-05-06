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

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.Protos.BatteryInfo
import com.android.devicediagnostics.evaluated.getBatteryInfo
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.UsageProgressBarPreference

class BatteryInfoFragment(private var diagnostics: BatteryInfo) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.battery_preferences, rootKey)

        findPreference<BatteryHealthPreference>("health")!!.also {
            if (diagnostics.stateOfHealth != -1) {
                it.setPercent(diagnostics.stateOfHealth.toLong(), 100)
                it.setBottomSummary(getString(R.string.battery_health_summary,
                    diagnostics.stateOfHealth))
            } else {
                it.isVisible = false
            }
        }

        findPreference<Preference>("manufacture_date")!!.also {
            if (diagnostics.manufactureDate != null) {
                it.summary = diagnostics.manufactureDate
            }
        }

        findPreference<Preference>("first_usage_date")!!.also {
            if (diagnostics.firstUsageDate != null) {
                it.summary = diagnostics.firstUsageDate
            }
        }

        findPreference<Preference>("cycle_count")!!.also {
            if (diagnostics.cycleCount != -1) {
                it.summary = diagnostics.cycleCount.toString()
            }
        }

        findPreference<Preference>("serial_number")!!.also {
            if (diagnostics.serial != null) {
                it.summary = diagnostics.serial
            }
        }

        findPreference<Preference>("serial_number")!!.also {
            if (diagnostics.serial != null) {
                it.summary = diagnostics.serial
            }
        }
    }
}

class BatteryHealthPreference(context: Context, attrs: AttributeSet) :
    UsageProgressBarPreference(context, attrs) {

    val diagnostics: BatteryInfo
        get() = (context as BatteryActivity).diagnostics

    override fun getSummary(): CharSequence? {
        if (diagnostics.stateOfHealth == -1) {
            return context.getString(R.string.unavailable)
        }
        return diagnostics.stateOfHealth.toString() + "%"
    }
}

class BatteryActivity : CollapsingToolbarBaseActivity() {
    lateinit var diagnostics: BatteryInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_fragment)
        setTitle(R.string.battery_title)

        diagnostics = getBatteryInfo(this)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, BatteryInfoFragment(diagnostics))
            }
        }
    }
}
