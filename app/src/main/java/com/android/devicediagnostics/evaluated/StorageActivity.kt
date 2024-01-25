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

import android.app.usage.IStorageStatsManager
import android.content.Context
import android.os.Bundle
import android.os.ServiceManager
import android.os.storage.StorageManager
import android.util.AttributeSet
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.UsageProgressBarPreference

class StorageDiagnostics {
    var lifetime: Int = -1
    var totalCapacity: Long = -1
    var totalCapacityUnits: String? = null

    constructor(context: Context) {
        val ssm =
            IStorageStatsManager.Stub.asInterface(ServiceManager.getService(Context.STORAGE_STATS_SERVICE));

        val totalBytes =
            ssm.getTotalBytes(StorageManager.UUID_PRIVATE_INTERNAL, context.opPackageName)
        setCapacity((totalBytes / 1000) / 1000)

        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        if (android.os.Flags.storageLifetimeApi()) {
            lifetime = sm.internalStorageRemainingLifetime
        }
    }

    private fun setCapacity(value: Long) {
        totalCapacity = value
        if (totalCapacity < 1000) {
            totalCapacityUnits = "MB"
            return
        }

        totalCapacity /= 1000
        if (totalCapacity < 1000) {
            totalCapacityUnits = "GB"
            return
        }

        totalCapacity /= 1000
        totalCapacityUnits = "TB"
    }
}

class StorageFragment(private var diagnostics: StorageDiagnostics) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.storage_preferences, rootKey)

        findPreference<StorageHealthPreference>("health")!!.also {
            if (diagnostics.lifetime != -1) {
                it.setPercent(diagnostics.lifetime.toLong(), 100)
                it.setBottomSummary(getString(R.string.storage_health_summary,
                    diagnostics.lifetime))
            } else {
                it.isVisible = false
            }
        }

        findPreference<Preference>("total_capacity")!!.also {
            it.summary =
                getString(R.string.storage_total_capacity_summary,
                    diagnostics.totalCapacity,
                    diagnostics.totalCapacityUnits)
        }
    }
}

class StorageActivity : CollapsingToolbarBaseActivity() {
    lateinit var diagnostics: StorageDiagnostics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_fragment)
        setTitle(R.string.storage_title)

        diagnostics = StorageDiagnostics(this)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, StorageFragment(diagnostics))
            }
        }
    }
}

class StorageHealthPreference(context: Context, attrs: AttributeSet) :
    UsageProgressBarPreference(context, attrs) {

    fun constructor() {}

    val diagnostics: StorageDiagnostics
        get() = (context as StorageActivity).diagnostics

    override fun getSummary(): CharSequence? {
        if (diagnostics.lifetime == -1) {
            return "Unavailable"
        }
        return diagnostics.lifetime.toString() + "%"
    }
}
