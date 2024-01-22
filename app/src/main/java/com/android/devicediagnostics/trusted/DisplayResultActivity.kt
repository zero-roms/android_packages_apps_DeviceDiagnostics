package com.android.devicediagnostics.trusted

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.R
import com.android.devicediagnostics.TestStatus
import com.android.devicediagnostics.getAttestation
import java.io.IOException

const val ATTESTATION = "ATTESTATION"

class DisplayResultFragment(private var testStatus: TestStatus) : PreferenceFragmentCompat() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_trusted_display_result, rootKey)

    findPreference<Preference>("screen")?.summary = if(testStatus.screenTest) "Good" else "Bad"
    findPreference<Preference>("touch")?.summary = if(testStatus.touchTest) "Good" else "Bad"

    findPreference<Preference>("done")?.setOnPreferenceClickListener{
      Intent(activity, QrCodeDisplayActivity::class.java).also {
        requireActivity().finishAffinity()
      }
      true
    }

    Thread {
      try {
        val attestationRecord = getAttestation(testStatus.attestation)
        if (attestationRecord.attestationChallenge contentEquals testStatus.challenge) {
          requireActivity().runOnUiThread {
            findPreference<Preference>("locked")?.also {
              it.summary = if (attestationRecord.teeEnforced.rootOfTrust.get().deviceLocked)
                resources.getString(R.string.locked)
              else
                resources.getString(R.string.not_locked)
              it.isVisible = true
            }
            findPreference<Preference>("trustworthy")?.also {
              it.summary = attestationRecord.teeEnforced.rootOfTrust.get().verifiedBootState.name
              it.isVisible = true
            }
            findPreference<Preference>("attestation_details")?.also {
              it.setOnPreferenceClickListener {
                startActivity(Intent(requireActivity(), DisplayAttestationDetailsActivity::class.java)
                  .putExtra (ATTESTATION, com.android.devicediagnostics.toString(attestationRecord)))
                true
              }
              it.isVisible = true
            }
          }
        } else {
          findPreference<Preference>("failure")?.also {
            it.summary = resources.getString(R.string.attestation_challenge_does_not_match) +
              "\n" + attestationRecord.attestationChallenge.toString(Charsets.UTF_8) +
              "\n" + testStatus.challenge.toString(Charsets.UTF_8)
            it.isVisible = true
          }
        }
      } catch (e : IOException) {
        findPreference<Preference>("failure")?.also {
          it.summary = e.toString()
          it.isVisible = true
        }
      }
    }.start()
  }
}

class DisplayResultActivity : FragmentActivity(R.layout.activity_one_fragment) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (savedInstanceState == null) {
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(R.id.fragment_container_view,
            DisplayResultFragment(TestStatus(intent!!.extras!!)))
      }
    }
  }
}