package com.android.devicediagnostics.evaluated

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.trusted.QRCODE
import com.android.devicediagnostics.R
import com.android.devicediagnostics.trusted.SCREEN_CONDITION

class ScreenTestFinalizeFragment(challenge: ByteArray) : PreferenceFragmentCompat() {
  private val mChallenge = challenge
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_evaluated_screen_test_finalize, rootKey)
    findPreference<Preference>("Good")?.setOnPreferenceClickListener{
      Intent(activity, QrDisplayActivity::class.java).also {
        it.putExtra(QRCODE, mChallenge)
        it.putExtra(SCREEN_CONDITION, true)
        startActivity(it)
      }
      true
    }
    findPreference<Preference>("Bad")?.setOnPreferenceClickListener{
      Intent(activity, QrDisplayActivity::class.java).also {
        it.putExtra(QRCODE, mChallenge)
        it.putExtra(SCREEN_CONDITION, false)
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
          ScreenTestFinalizeFragment(intent.extras?.getByteArray(QRCODE)?:
            throw AssertionError("Missing challenge"))
        )
      }
    }
  }
}