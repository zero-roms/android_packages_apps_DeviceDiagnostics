package com.android.devicediagnostics.evaluated

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.R
import com.android.devicediagnostics.TestStatus

class TouchTestFinalizeFragment(private val testStatus: TestStatus) : PreferenceFragmentCompat() {

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_evaluated_touch_test_finalize, rootKey)
    findPreference<Preference>("Continue")?.setOnPreferenceClickListener {
      Intent(activity, QrDisplayActivity::class.java).also {
        testStatus.write(it)
        startActivity(it)
      }
      true
    }

    findPreference<Preference>(if (testStatus.touchTest) "Good" else "Bad")?.isVisible = true
  }
}

class TouchTestFinalizeActivity : FragmentActivity(R.layout.activity_one_fragment) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(
          R.id.fragment_container_view,
          TouchTestFinalizeFragment(TestStatus(intent!!.extras!!))
        )
      }
    }
  }
}