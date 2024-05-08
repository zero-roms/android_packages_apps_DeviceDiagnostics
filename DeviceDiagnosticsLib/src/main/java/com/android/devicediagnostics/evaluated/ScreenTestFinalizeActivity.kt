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

import android.os.Bundle
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class ScreenTestFinalizeFragment() : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_evaluated_screen_test_finalize, rootKey)
        findPreference<Preference>("Good")?.setOnPreferenceClickListener {
            nextActivity(true)
            true
        }
        findPreference<Preference>("Bad")?.setOnPreferenceClickListener {
            nextActivity(false)
            true
        }
    }

    private fun nextActivity(screenTestResult: Boolean) {
        nextTest(requireActivity()) { it.setScreenTest(screenTestResult) }
    }
}

class ScreenTestFinalizeActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_fragment)
        setTitle(R.string.screen_test_title)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, ScreenTestFinalizeFragment())
            }
        }
    }
}
