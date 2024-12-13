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
import android.content.Intent
import android.icu.number.NumberFormatter
import android.icu.util.MeasureUnit
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

class BatteryInfoFragment(private val diagnostics: BatteryInfo, private val launchLevel: Int) :
    PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.battery_preferences, rootKey)

        val locale = getDefaultLocale(activity!!)
        val formatter = NumberFormatter.withLocale(locale)
        findPreference<BatteryHealthPreference>("health")!!.also {
            it.diagnostics = diagnostics
            it.setPercent(max(0, diagnostics.stateOfHealth.toLong()), 100)
            if (diagnostics.hasStateOfHealth()) {
                val percentString =
                    formatter.unit(MeasureUnit.PERCENT).format(diagnostics.stateOfHealth).toString()
                it.setBottomSummary(getString(R.string.battery_health_summary_fmt, percentString))
            } else {
                it.setBottomSummary(getString(R.string.battery_health_unavailable))
                it.isVisible = launchLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            }
        }

        findPreference<Preference>("legacy_health")!!.also {
            if (diagnostics.hasStateOfHealth()) {
                it.isVisible = false
            } else {
                it.summary = getString(legacyHealthToStringId(diagnostics.legacyHealth))
                it.isVisible = true
            }
        }

        findPreference<Preference>("manufacture_date")!!.also {
            if (diagnostics.hasManufactureTimestamp()) {
                it.summary = localizeDateTimestamp(locale, diagnostics.manufactureTimestamp)
            } else {
                it.summary = getString(R.string.unknown)
                it.isVisible = launchLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            }
        }

        findPreference<Preference>("first_usage_date")!!.also {
            if (diagnostics.hasFirstUsageTimestamp()) {
                it.summary = localizeDateTimestamp(locale, diagnostics.firstUsageTimestamp)
            } else {
                it.summary = getString(R.string.unknown)
                it.isVisible = launchLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            }
        }

        findPreference<Preference>("cycle_count")!!.also {
            if (diagnostics.hasCycleCount()) {
                it.summary = formatter.format(diagnostics.cycleCount).toString()
            } else {
                it.summary = getString(R.string.unknown)
                it.isVisible = launchLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            }
        }

        findPreference<Preference>("serial_number")!!.also {
            if (diagnostics.hasSerial()) {
                it.summary = diagnostics.serial
            } else {
                it.summary = getString(R.string.unknown)
                it.isVisible = launchLevel > Build.VERSION_CODES.VANILLA_ICE_CREAM
            }
        }

        findPreference<Preference>("part_status")!!.also {
            if (diagnostics.hasPartStatus()) {
                it.summary = diagnostics.partStatus
            } else {
                it.summary = getString(R.string.unknown)
                it.isVisible = launchLevel > Build.VERSION_CODES.VANILLA_ICE_CREAM
            }
        }
    }
}

class BatteryHealthPreference(context: Context, attrs: AttributeSet) :
    UsageProgressBarPreference(context, attrs) {

    var diagnostics: BatteryInfo = BatteryInfo.getDefaultInstance()

    override fun getSummary(): CharSequence? {
        if (!diagnostics.hasStateOfHealth()) {
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
            val launchLevel = readLaunchLevel()
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, BatteryInfoFragment(diagnostics, launchLevel))
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

    private fun readLaunchLevel(): Int {
        if (intent.extras != null) {
            return intent.extras!!.getInt(INTENT_LAUNCH_LEVEL)
        }
        return ApplicationInterface.app.getLaunchLevel()
    }

    companion object Static {
        const val INTENT_BATTERY_INFO = "battery_info"
        const val INTENT_LAUNCH_LEVEL = "vsr_level"

        fun createIntent(from: Context, diagnostics: BatteryInfo, launchLevel: Int): Intent {
            return Intent(from, BatteryActivity::class.java)
                .putExtra(INTENT_BATTERY_INFO, diagnostics.toByteArray())
                .putExtra(INTENT_LAUNCH_LEVEL, launchLevel)
        }
    }
}
