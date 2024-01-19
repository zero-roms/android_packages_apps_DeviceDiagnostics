package com.android.devicediagnostics.evaluated

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.R

class ScreenTestIntroFragment(private val testStatus: TestStatus) : PreferenceFragmentCompat() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_evaluated_screen_test, rootKey)
    findPreference<Preference>("continue")?.setOnPreferenceClickListener{
      Intent(activity, ScreenTestActivity::class.java).also {
        testStatus.write(it)
        startActivity(it)
      }
      true
    }
  }
}

class ScreenTestIntroActivity : FragmentActivity(R.layout.activity_one_fragment) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(
          R.id.fragment_container_view,
          ScreenTestIntroFragment(TestStatus(intent!!.extras!!))
        )
      }
    }
  }
}