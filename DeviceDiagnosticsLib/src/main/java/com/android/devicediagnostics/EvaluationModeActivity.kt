package com.android.devicediagnostics

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.bluetooth.BluetoothServer
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity


class EvaluationModeFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.evaluation_mode_landing, rootKey)
    }

    override fun onResume() {
        super.onResume()

        val enabled = BluetoothServer.bluetoothEnabled
        findPreference<Preference>("trusted_device")!!.isVisible = enabled
        findPreference<Preference>("evaluation_mode")!!.isVisible = enabled
        findPreference<Preference>("bluetooth_disabled")!!.also {
            it.isVisible = !enabled
            it.setOnPreferenceClickListener {
                startActivity(Intent().setAction(Settings.ACTION_BLUETOOTH_SETTINGS))
                true
            }
        }
    }
}

class EvaluationModeActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_fragment)
        setTitle(R.string.evaluation_mode_title)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, EvaluationModeFragment())
            }
        }
    }
}