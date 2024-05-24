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
import android.os.Bundle
import android.text.format.Formatter
import android.util.AttributeSet
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.Protos.StorageInfo
import com.android.devicediagnostics.evaluated.getStorageInfo
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.UsageProgressBarPreference
import java.text.NumberFormat

class StorageInfoFragment(private var diagnostics: StorageInfo) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.storage_preferences, rootKey)

        findPreference<StorageHealthPreference>("health")!!.also {
            it.diagnostics = diagnostics
            if (diagnostics.usefulLifetimeRemaining != -1) {
                val locale = getDefaultLocale(activity!!)
                val percent = diagnostics.usefulLifetimeRemaining / 100.0f
                val percentString = NumberFormat.getPercentInstance(locale).format(percent)
                it.setPercent(diagnostics.usefulLifetimeRemaining.toLong(), 100)
                it.setBottomSummary(getString(R.string.storage_health_summary_fmt, percentString))
            } else {
                it.isVisible = false
            }
        }

        findPreference<Preference>("total_capacity")!!.also {
            val desc =
                Formatter.formatBytes(
                    activity!!.resources,
                    diagnostics.capacityBytes,
                    Formatter.FLAG_SHORTER
                )
            it.summary = getString(R.string.storage_total_capacity_summary, desc.value, desc.units)
        }
    }
}

class StorageHealthPreference(context: Context, attrs: AttributeSet) :
    UsageProgressBarPreference(context, attrs) {

    fun constructor() {}

    var diagnostics: StorageInfo = StorageInfo.getDefaultInstance()

    override fun getSummary(): CharSequence? {
        if (diagnostics.usefulLifetimeRemaining == -1) {
            return context.getString(R.string.unavailable)
        }
        return diagnostics.usefulLifetimeRemaining.toString() + "%"
    }
}

class StorageActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_fragment)
        setTitle(R.string.storage_title)

        if (savedInstanceState == null) {
            val diagnostics = readStorageInfo()
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, StorageInfoFragment(diagnostics))
            }
        }
    }

    // Get either current battery info or battery info from the intent.
    private fun readStorageInfo(): StorageInfo {
        if (intent.extras != null) {
            val array = intent.extras!!.getByteArray(INTENT_STORAGE_INFO)
            return StorageInfo.parseFrom(array)
        }
        return getStorageInfo(this)
    }

    companion object Static {
        const val INTENT_STORAGE_INFO = "storage_info"

        fun createIntent(from: Activity, diagnostics: StorageInfo): Intent {
            return Intent(from, StorageActivity::class.java)
                .putExtra(INTENT_STORAGE_INFO, diagnostics.toByteArray())
        }
    }
}
