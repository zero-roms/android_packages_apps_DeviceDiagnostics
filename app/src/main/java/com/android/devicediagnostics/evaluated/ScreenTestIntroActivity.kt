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
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.R
import com.android.devicediagnostics.TestStatus
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

class ScreenTestIntroFragment(private val testStatus: TestStatus) : PreferenceFragmentCompat() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_evaluated_screen_test, rootKey)
    findPreference<Preference>("title")!!.also {
      if (testStatus.oneShot) {
        it.setSummary(R.string.screen_test_summary_one_shot)
      }
    }
    findPreference<Preference>("continue")!!.also {
      it.setOnPreferenceClickListener{
        Intent(activity, ScreenTestActivity::class.java).also {
          testStatus.write(it)
          startActivity(it)
        }
        true
      }
    }
  }
}

class ScreenTestIntroActivity : CollapsingToolbarBaseActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_one_fragment)
    setTitle(R.string.screen_test_title)

    if (savedInstanceState == null) {
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(
          R.id.fragment_container_view,
          ScreenTestIntroFragment(TestStatus(intent))
        )
      }
    }
  }
}
