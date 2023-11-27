package com.android.devicediagnostics

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.io.IOException
import java.util.zip.Inflater

class TrustedDisplayResultFragment : PreferenceFragmentCompat() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    val attestation = requireActivity().intent.extras?.getByteArray(QRCODE) ?: throw AssertionError ("No attestation")
    val challenge = requireActivity().intent.extras?.getByteArray(CHALLENGE) ?: throw AssertionError ("No challenge")
    val screenCondition = requireActivity().intent.extras?.getBoolean(SCREEN_CONDITION) ?: throw AssertionError ("No screen condition")

    setPreferencesFromResource(R.xml.preferences_trusted_display_result, rootKey)

    findPreference<Preference>("screen")?.summary = if(screenCondition) "Good" else "Bad"

    findPreference<Preference>("done")?.setOnPreferenceClickListener{
      Intent(activity, TrustedActivity::class.java).also {
        requireActivity().finishAffinity()
      }
      true
    }

    Thread {
      try {
        val inflater = Inflater()
        inflater.setInput(attestation, 0, attestation.size)
        var inflated = ByteArray(0)
        while(true) {
          val inflatedChunk = ByteArray(256)
          val inflatedBytes = inflater.inflate(inflatedChunk)
          if (inflatedBytes == 0) break
          inflated += inflatedChunk.take(inflatedBytes).toByteArray()
        }
        val attestationRecord = getAttestation(inflated)
        if (attestationRecord.attestationChallenge contentEquals challenge) {
          requireActivity().runOnUiThread {
            findPreference<Preference>("locked")?.also {
              it.summary = if (attestationRecord.teeEnforced.rootOfTrust.get().deviceLocked) "Locked" else "Not locked"
              it.isVisible = true
            }
            findPreference<Preference>("trustworthy")?.also {
              it.summary = attestationRecord.teeEnforced.rootOfTrust.get().verifiedBootState.name
              it.isVisible = true
            }
            findPreference<Preference>("attestation_details")?.also {
              it.setOnPreferenceClickListener{
                startActivity(Intent(requireActivity(), TrustedDisplayAttestationDetailsActivity::class.java)
                                .putExtra(QRCODE, toString(attestationRecord)))
                true
              }
              it.isVisible = true
            }
          }
        } else {
          findPreference<Preference>("failure")?.also {
            it.summary = "Attestation challenge does not match!" +
              "\n" + attestationRecord.attestationChallenge.toString(Charsets.UTF_8) +
              "\n" + challenge.toString(Charsets.UTF_8)
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

class TrustedDisplayResultActivity : FragmentActivity(R.layout.activity_one_fragment) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (savedInstanceState == null) {
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(R.id.fragment_container_view, TrustedDisplayResultFragment())
      }
    }
  }
}