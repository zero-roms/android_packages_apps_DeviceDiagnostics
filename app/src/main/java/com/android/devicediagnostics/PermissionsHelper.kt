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
package com.android.devicediagnostics

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog

class PermissionsHelper(private val activity: ComponentActivity,
    private val permissions: Array<String>,
    private val onPermissionsGranted: () -> Unit) {
    private var permissionsDeniedOnce = false
    private val requestPermissions =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            handlePermissions(permissions)
        }
    private var active = false

    private fun handlePermissions(permissions: Map<String, Boolean>) {
        if (!permissions.entries.all { it.value }) {
            requestPermissionsAgain()
            return
        }
        onPermissionsGranted()
    }

    public fun requestPermissions() {
        if (active) {
            return
        }
        active = true
        requestPermissions.launch(permissions)
    }

    private fun requestPermissionsAgain() {
        val builder = AlertDialog.Builder(activity)
        builder.setMessage(R.string.grant_eval_permissions_dialog)
        builder.setNegativeButton(R.string.cancel_button, DialogInterface.OnClickListener { _, _ ->
            active = false
            activity.finish()
        })
        builder.setPositiveButton(R.string.try_again_button,
            DialogInterface.OnClickListener { _, _ ->
                // Android doesn't let apps spam users with repeated permissions requests, so after one failure
                // we have to direct them to the Settings app to correct things.
                if (permissionsDeniedOnce) {
                    activity.startActivity(Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data =
                            Uri.fromParts("package",
                                activity.getString(R.string.package_name),
                                null)
                    })
                    active = false
                } else {
                    requestPermissions.launch(permissions)
                    permissionsDeniedOnce = true
                }
            })
        builder.show()
    }
}