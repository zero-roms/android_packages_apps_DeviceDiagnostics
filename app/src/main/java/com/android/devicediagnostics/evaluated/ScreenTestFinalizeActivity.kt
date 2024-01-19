package com.android.devicediagnostics.evaluated

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.R

class ScreenTestFinalizeFragment(private var testStatus: TestStatus) : PreferenceFragmentCompat() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_evaluated_screen_test_finalize, rootKey)
    findPreference<Preference>("Good")?.setOnPreferenceClickListener{
      Intent(activity, TouchTestActivity::class.java).also {
        testStatus.screenTest = true
        testStatus.write(it)
        startActivity(it)
      }
      true
    }
    findPreference<Preference>("Bad")?.setOnPreferenceClickListener{
      Intent(activity, TouchTestActivity::class.java).also {
        testStatus.screenTest = false
        testStatus.write(it)
        startActivity(it)
      }
      true
    }
  }
}

class ScreenTestFinalizeActivity : FragmentActivity(R.layout.activity_one_fragment) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(
          R.id.fragment_container_view,
          ScreenTestFinalizeFragment(TestStatus(intent!!.extras!!))
        )
      }
    }
  }
}