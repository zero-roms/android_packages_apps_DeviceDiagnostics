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

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.R
import com.android.devicediagnostics.TestStatus
import com.android.devicediagnostics.bluetooth.BluetoothClient
import com.android.settingslib.widget.LayoutPreference
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private const val BITMAP_SIZE = 2048

class QrDisplayFragment(private var testStatus: TestStatus) : PreferenceFragmentCompat() {
  private var mChunk = 1

  private lateinit var mNextPreference: Preference
  private lateinit var mPreviousPreference: Preference
  private lateinit var mProgressPreference: Preference

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_evaluated_qr_display, rootKey)
    findPreference<Preference>("Done")?.setOnPreferenceClickListener {
      requireActivity().finishAffinity()
      true
    }
    mNextPreference =
      findPreference<Preference>("Next") ?: throw AssertionError("No Next preference")
    mPreviousPreference =
      findPreference<Preference>("Previous") ?: throw AssertionError("No Previous preference")
    mProgressPreference =
      findPreference<Preference>("Progress") ?: throw AssertionError("No Previous preference")

    mNextPreference.setOnPreferenceClickListener {
      if (mChunk < testStatus.chunks) mChunk += 1
      if (mChunk == testStatus.chunks) mNextPreference.isEnabled = false
      mPreviousPreference.isEnabled = true
      drawQRCode()
      true
    }
    mPreviousPreference.setOnPreferenceClickListener {
      if (mChunk > 1) mChunk -= 1
      if (mChunk == 1) mPreviousPreference.isEnabled = false
      mNextPreference.isEnabled = true
      drawQRCode()
      true
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    Thread {
      testStatus.attestation = getAttestation(testStatus.challenge)
      requireActivity().runOnUiThread() {
        if (testStatus.chunks > 1) {
          mNextPreference.isVisible = true
          mPreviousPreference.isVisible = true
          mPreviousPreference.isEnabled = false
        }
        drawQRCode()
      }
      BluetoothClient.write(testStatus.toString(putAttestation = true))
    }.start()
  }

  private fun drawQRCode() {
    Thread {
      val writer = QRCodeWriter()
      val bitMatrix =
        writer.encode(testStatus.toString(putAttestation = true, mChunk),
                      BarcodeFormat.QR_CODE, BITMAP_SIZE, BITMAP_SIZE)
      val bmp = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.RGB_565)
      for (x in 0 until BITMAP_SIZE)
        for (y in 0 until BITMAP_SIZE)
          bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)

      requireActivity().runOnUiThread() {
        val layoutPreference = findPreference("qr_code") as LayoutPreference?
        val image = layoutPreference?.findViewById<ImageView>(R.id.qrCode)
        image?.setImageBitmap(bmp)
        mProgressPreference.title = "$mChunk/${testStatus.chunks}"
      }
    }.start()
  }
}

class QrDisplayActivity : FragmentActivity(R.layout.activity_one_fragment) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(
          R.id.fragment_container_view,
          QrDisplayFragment(TestStatus(intent!!.extras!!))
        )
      }
    }
  }
}
