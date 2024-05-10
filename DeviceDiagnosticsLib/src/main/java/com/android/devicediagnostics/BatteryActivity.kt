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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.Protos.BatteryInfo
import com.android.devicediagnostics.evaluated.getBatteryInfo
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.UsageProgressBarPreference
import kotlin.math.max

fun legacyHealthToStringId(value: Int): Int {
    return when (value) {
        BatteryManager.BATTERY_HEALTH_COLD -> R.string.battery_health_cold
        BatteryManager.BATTERY_HEALTH_DEAD -> R.string.battery_health_dead
        BatteryManager.BATTERY_HEALTH_GOOD -> R.string.battery_health_good
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.battery_health_overheat
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.battery_health_over_voltage
        else -> R.string.unknown
    }
}

class BatteryInfoFragment(private var diagnostics: BatteryInfo) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.battery_preferences, rootKey)

        val app = ApplicationInterface.app
        val vsrLevel = app.getLaunchLevel()

        findPreference<BatteryHealthPreference>("health")!!.also {
            it.diagnostics = diagnostics
            if (vsrLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                it.setPercent(max(0, diagnostics.stateOfHealth.toLong()), 100)
                if (diagnostics.stateOfHealth >= 0) {
                    it.setBottomSummary(
                        getString(R.string.battery_health_summary, diagnostics.stateOfHealth)
                    )
                } else {
                    it.setBottomSummary(getString(R.string.battery_health_unavailable))
                }
            } else {
                it.isVisible = false
            }
        }

        findPreference<Preference>("legacy_health")!!.also {
            if (vsrLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                it.isVisible = false
            } else {
                it.summary = getString(legacyHealthToStringId(diagnostics.legacyHealth))
                it.isVisible = true
            }
        }

        findPreference<Preference>("manufacture_date")!!.also {
            if (diagnostics.manufactureDate != null) {
                it.summary = diagnostics.manufactureDate
            } else if (vsrLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                it.summary = getString(R.string.unknown)
            } else {
                it.isVisible = false
            }
        }

        findPreference<Preference>("first_usage_date")!!.also {
            if (diagnostics.firstUsageDate != null) {
                it.summary = diagnostics.firstUsageDate
            } else if (vsrLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                it.summary = getString(R.string.unknown)
            } else {
                it.isVisible = false
            }
        }

        findPreference<Preference>("cycle_count")!!.also {
            if (diagnostics.cycleCount != -1) {
                it.summary = diagnostics.cycleCount.toString()
            } else if (vsrLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                it.summary = getString(R.string.unknown)
            } else {
                it.isVisible = false
            }
        }

        findPreference<Preference>("serial_number")!!.also {
            if (diagnostics.serial != null) {
                it.summary = diagnostics.serial
            } else if (vsrLevel >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                it.summary = getString(R.string.unknown)
            } else {
                it.isVisible = false
            }
        }

        findPreference<Preference>("part_status")!!.also {
            if (diagnostics.partStatus != null) {
                it.summary = diagnostics.partStatus
            } else if (vsrLevel >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                it.summary = getString(R.string.unknown)
            } else {
                it.isVisible = false
            }
        }
    }
}

class BatteryHealthPreference(context: Context, attrs: AttributeSet) :
    UsageProgressBarPreference(context, attrs) {

    var diagnostics: BatteryInfo = BatteryInfo.getDefaultInstance()

    override fun getSummary(): CharSequence? {
        if (diagnostics.stateOfHealth == -1) {
            return context.getString(R.string.unavailable)
        }
        return diagnostics.stateOfHealth.toString() + "%"
    }
}

class BatteryActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_fragment)
        setTitle(R.string.battery_title)

        if (savedInstanceState == null) {
            val diagnostics = readBatteryInfo()
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, BatteryInfoFragment(diagnostics))
            }
        }
    }

    // Get either current battery info or battery info from the intent.
    private fun readBatteryInfo(): BatteryInfo {
        if (intent.extras != null) {
            val array = intent.extras!!.getByteArray(INTENT_BATTERY_INFO)
            return BatteryInfo.parseFrom(array)
        }
        return getBatteryInfo(this)
    }

    companion object Static {
        const val INTENT_BATTERY_INFO = "battery_info"

        fun createIntent(from: Activity, diagnostics: BatteryInfo): Intent {
            return Intent(from, BatteryActivity::class.java)
                .putExtra(INTENT_BATTERY_INFO, diagnostics.toByteArray())
        }
    }
}
