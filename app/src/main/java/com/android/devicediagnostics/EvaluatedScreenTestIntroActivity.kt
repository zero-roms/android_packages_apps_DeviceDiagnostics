package com.android.devicediagnostics

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class EvaluatedScreenTestIntroFragment(challenge: ByteArray) : PreferenceFragmentCompat() {
  private val mChallenge = challenge
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_evaluated_screen_test, rootKey)
    findPreference<Preference>("continue")?.setOnPreferenceClickListener{
      Intent(activity, EvaluatedScreenTestActivity::class.java).also {
        it.putExtra(QRCODE, mChallenge)
        startActivity(it)
      }
      true
    }
  }
}

class EvaluatedScreenTestIntroActivity : FragmentActivity(R.layout.activity_one_fragment) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(R.id.fragment_container_view,
            EvaluatedScreenTestIntroFragment(intent.extras?.getByteArray(QRCODE)?:
              throw AssertionError("Missing challenge")))
      }
    }
  }
}