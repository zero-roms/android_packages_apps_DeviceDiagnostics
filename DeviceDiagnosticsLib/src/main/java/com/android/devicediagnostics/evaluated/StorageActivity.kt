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
import com.android.devicediagnostics.Protos.StorageInfo
import com.android.devicediagnostics.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.UsageProgressBarPreference

public fun getStorageInfo(context: Context): StorageInfo {
    val builder = StorageInfo.newBuilder()

    val ssm =
        IStorageStatsManager.Stub.asInterface(ServiceManager.getService(Context.STORAGE_STATS_SERVICE));
    val totalBytes =
        ssm.getTotalBytes(StorageManager.UUID_PRIVATE_INTERNAL, context.opPackageName)
    setStorageInfoCapacity(builder, (totalBytes / 1000) / 1000)

    if (android.os.Flags.storageLifetimeApi()) {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        builder.setUsefulLifetimeRemaining(sm.internalStorageRemainingLifetime)
    }
    return builder.build()
}

private fun setStorageInfoCapacity(builder: StorageInfo.Builder, value: Long) {
    var totalCapacity = value
    var totalCapacityUnits = "MB"

    if (totalCapacity >= 1000) {
        totalCapacity /= 1000
        totalCapacityUnits = "GB"
    }

    if (totalCapacity >= 1000) {
        totalCapacity /= 1000
        totalCapacityUnits = "TB"
    }

    builder.setCapacity(totalCapacity)
    builder.setCapacityUnits(totalCapacityUnits)
}

class StorageFragment(private var diagnostics: StorageInfo) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.storage_preferences, rootKey)

        findPreference<StorageHealthPreference>("health")!!.also {
            if (diagnostics.usefulLifetimeRemaining != -1) {
                it.setPercent(diagnostics.usefulLifetimeRemaining.toLong(), 100)
                it.setBottomSummary(getString(R.string.storage_health_summary,
                    diagnostics.usefulLifetimeRemaining))
            } else {
                it.isVisible = false
            }
        }

        findPreference<Preference>("total_capacity")!!.also {
            it.summary =
                getString(R.string.storage_total_capacity_summary,
                    diagnostics.capacity,
                    diagnostics.capacityUnits)
        }
    }
}

class StorageActivity : CollapsingToolbarBaseActivity() {
    lateinit var diagnostics: StorageInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_fragment)
        setTitle(R.string.storage_title)

        diagnostics = getStorageInfo(this)

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

    val diagnostics: StorageInfo
        get() = (context as StorageActivity).diagnostics

    override fun getSummary(): CharSequence? {
        if (diagnostics.usefulLifetimeRemaining == -1) {
            return context.getString(R.string.unavailable)
        }
        return diagnostics.usefulLifetimeRemaining.toString() + "%"
    }
}
