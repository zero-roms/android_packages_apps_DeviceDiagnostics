package com.android.devicediagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

class TrustedDisplayAttestationDetailsActivity : ComponentActivity()  {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val qrcode = intent.extras?.getString(QRCODE) ?: throw AssertionError("No attestation string")
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(qrcode)
      }
    }
  }
}
