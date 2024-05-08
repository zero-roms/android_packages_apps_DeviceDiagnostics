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
import android.os.Bundle
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.android.devicediagnostics.R

class ScreenTestActivity : Activity() {
    private enum class State {
        RED,
        GREEN,
        BLUE
    }

    private var state = State.RED
        set(value) {
            when (value) {
                State.RED -> rectangle.setBackgroundColor(getColor(R.color.red))
                State.GREEN -> rectangle.setBackgroundColor(getColor(R.color.green))
                State.BLUE -> rectangle.setBackgroundColor(getColor(R.color.blue))
            }
            field = value
        }

    private lateinit var rectangle: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Attempt to hide system bars
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContentView(R.layout.layout_evaluated_screen_test)
        rectangle = findViewById<View>(R.id.mainLayout) ?: throw AssertionError("Can't find view")
        rectangle.setOnClickListener {
            when (state) {
                State.RED -> state = State.GREEN
                State.GREEN -> state = State.BLUE
                State.BLUE -> {
                    nextTestActivity(this, ScreenTestFinalizeActivity::class.java.name)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        state = State.RED
    }
}
