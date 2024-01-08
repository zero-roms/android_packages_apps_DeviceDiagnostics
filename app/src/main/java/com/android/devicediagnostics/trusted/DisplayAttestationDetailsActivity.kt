package com.android.devicediagnostics.trusted

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.android.devicediagnostics.R

class DisplayAttestationDetailsActivity : Activity()  {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.layout_trusted_attestation_details)
    val text = findViewById<TextView>(R.id.attestation_details) ?: throw AssertionError("Can't find TextView")
    text.text = intent.extras?.getString(QRCODE) ?: throw AssertionError("No attestation string")
  }
}
