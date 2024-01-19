package com.android.devicediagnostics.evaluated

import android.content.Intent
import android.os.Bundle

private const val CHALLENGE = "CHALLENGE"
private const val ATTESTATION = "ATTESTATION"
private const val SCREEN_TEST = "SCREEN_TEST"
private const val TOUCH_TEST = "TOUCH_TEST"

class TestStatus (
  var challenge: ByteArray = byteArrayOf(),
  var screenTest: Boolean = false,
  var touchTest: Boolean = false,
  var attestation: ByteArray = byteArrayOf(),
  )
{
  constructor(bundle: Bundle) : this() {
    // Support string challenge so this intent can be launched from am
    challenge = bundle.getByteArray(CHALLENGE)?:
                bundle.getString(CHALLENGE)?.toByteArray()?:
                throw AssertionError("Missing challenge")
    screenTest = bundle.getBoolean(SCREEN_TEST)
    touchTest = bundle.getBoolean(TOUCH_TEST)
    attestation = bundle.getByteArray(ATTESTATION)?:
                  byteArrayOf()
  }

  fun write(intent: Intent) {
    // Support string challenge so this intent can be launched from am
    intent.putExtra(CHALLENGE, challenge)
    intent.putExtra(SCREEN_TEST, screenTest)
    intent.putExtra(TOUCH_TEST, touchTest)
    intent.putExtra(ATTESTATION, attestation)
  }
}