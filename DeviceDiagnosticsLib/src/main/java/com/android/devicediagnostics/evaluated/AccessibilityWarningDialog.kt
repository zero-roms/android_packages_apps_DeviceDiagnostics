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
package com.android.devicediagnostics.evaluated

import android.app.Activity
import android.app.AlertDialog
import com.android.devicediagnostics.R

fun showAccessibilityDialog(activity: Activity, titleResId: Int, messageResId: Int) {
    AlertDialog.Builder(activity)
        .setTitle(titleResId)
        .setMessage(messageResId)
        .setPositiveButton(R.string.proceed) { _, _ -> }
        .setNegativeButton(R.string.cancel) { _, _ -> activity.finish() }
        .show()
}
