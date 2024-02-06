package com.android.devicediagnostics

import android.os.Bundle
import androidx.fragment.app.commit
import androidx.preference.PreferenceFragmentCompat
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class EvaluationModeFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.evaluation_mode_landing, rootKey)
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