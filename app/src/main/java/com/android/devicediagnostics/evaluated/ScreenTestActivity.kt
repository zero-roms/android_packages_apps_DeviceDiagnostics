package com.android.devicediagnostics.evaluated

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.android.devicediagnostics.trusted.QRCODE
import com.android.devicediagnostics.R

class ScreenTestActivity : Activity() {
  private enum class State {
    RED, GREEN, BLUE
  }

  private var state = State.RED
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val windowInsetsController =
      WindowCompat.getInsetsController(window, window.decorView)
    // Configure the behavior of the hidden system bars.
    windowInsetsController.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

    setContentView(R.layout.layout_evaluated_screen_test)
    val v = findViewById<View>(R.id.mainLayout) ?: throw AssertionError("Can't find view")
    v.setBackgroundColor(getColor(R.color.red))
    v.setOnClickListener {
      when (state) {
        State.RED -> {
          v.setBackgroundColor(getColor(R.color.green))
          state = State.GREEN
        }

        State.GREEN -> {
          v.setBackgroundColor(getColor(R.color.blue))
          state = State.BLUE
        }

        State.BLUE -> Intent(this, ScreenTestFinalizeActivity::class.java).also {
          it.putExtra(QRCODE, intent.extras?.getByteArray(QRCODE))
          startActivity(it)
        }
      }
    }
  }
}