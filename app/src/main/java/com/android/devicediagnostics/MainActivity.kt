package com.android.devicediagnostics

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class MainFragment : PreferenceFragmentCompat() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_main, rootKey)
    findPreference<Preference>("Trusted")?.setOnPreferenceClickListener{
      Intent(activity, TrustedActivity::class.java).also {
        startActivity(it)
      }
      true
    }
    findPreference<Preference>("Evaluated")?.setOnPreferenceClickListener{
      Intent(activity, EvaluatedQrCodeScanActivity::class.java).also {
        startActivity(it)
      }
      true
    }
  }
}

class MainActivity : FragmentActivity(R.layout.activity_one_fragment) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(R.id.fragment_container_view, MainFragment())
      }
    }
  }
}
