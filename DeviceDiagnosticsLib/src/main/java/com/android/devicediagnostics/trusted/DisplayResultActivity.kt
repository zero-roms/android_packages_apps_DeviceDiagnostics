/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.devicediagnostics.trusted

import android.os.Bundle
import androidx.fragment.app.commit
import com.android.devicediagnostics.AttestationController
import com.android.devicediagnostics.DisplayResultFragment
import com.android.devicediagnostics.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

const val ATTESTATION = "ATTESTATION"
private const val TAG = "DisplayResultActivity"

class DisplayResultActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_fragment)
        setTitle(R.string.evaluation_results)

        if (savedInstanceState == null) {
            val state = TrustedState.fromActivity(this)!!
            val controller = AttestationController(state.challenge)
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, DisplayResultFragment(state.report, controller))
            }
        }
    }
}