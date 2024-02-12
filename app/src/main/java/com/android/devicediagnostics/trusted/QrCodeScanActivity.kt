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

import android.Manifest
import android.content.Intent
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Size
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.R
import com.android.devicediagnostics.ChunkedTestStatus
import com.android.devicediagnostics.PermissionsHelper
import com.android.devicediagnostics.TestStatus
import com.android.devicediagnostics.bluetooth.BluetoothServer
import com.android.settingslib.widget.LayoutPreference
import com.android.settingslib.qrcode.QrCamera
import java.lang.Exception

class QrCodeScanFragment(listener: TextureView.SurfaceTextureListener) :
  PreferenceFragmentCompat() {
  private var mListener = listener
  private var mTextureView: TextureView? = null

  val textureView : TextureView
    get() { return mTextureView ?: throw AssertionError("texture view is null") }

  private lateinit var mProgressPreference: Preference
  val progressPreference : Preference
    get() {return mProgressPreference }
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_trusted_scan, rootKey)
    mProgressPreference =
      findPreference<Preference>("Progress") ?: throw AssertionError("No Previous preference")
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val layoutPreference = findPreference( "qr_code" ) as LayoutPreference?
    mTextureView = layoutPreference!!.findViewById<TextureView>(R.id.preview_view)
    textureView.surfaceTextureListener = mListener
    textureView.outlineProvider = object : ViewOutlineProvider() {
      override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, 12f)
      }
    }
    textureView.clipToOutline = true
  }
}

class QrCodeScanActivity : FragmentActivity(R.layout.activity_one_fragment),
                           TextureView.SurfaceTextureListener,
                           QrCamera.ScannerCallback {
  private var mFragment = QrCodeScanFragment(this)
  private var mCamera = QrCamera(this, this)
  private lateinit var testStatus: TestStatus
  private val chunkedTestStatus: ChunkedTestStatus
    get() { return testStatus as ChunkedTestStatus }

  private val permissions = PermissionsHelper(this,
    arrayOf(
      Manifest.permission.CAMERA,
      Manifest.permission.BLUETOOTH_ADVERTISE,
      Manifest.permission.BLUETOOTH_CONNECT,
      Manifest.permission.BLUETOOTH_SCAN)
  ) { onPermissionsGranted() }

  private fun onPermissionsGranted() {
    supportFragmentManager.commit {
      setReorderingAllowed(true)
      add(R.id.fragment_container_view, mFragment)
    }
    Thread {
      val ts = BluetoothServer.read()
      try {
        testStatus = TestStatus(ts, getAttestation = true)
        runOnUiThread { handleSuccessfulResult("") }
      } catch ( _ : Exception ) {
      }
    }.start()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    testStatus = ChunkedTestStatus(intent!!.extras!!)
  }

  override fun onResume() {
    super.onResume()
    permissions.requestPermissions()
  }

  override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
    mCamera.start(surface)
  }

  override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

  override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
    mCamera.stop()
    return true
  }

  override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

  override fun handleCameraFailure() {
    mCamera.stop()
  }

  override fun getViewSize(): Size {
    return Size(mFragment.textureView.width, mFragment.textureView.height)
  }

  override fun getFramePosition(previewSize: Size, cameraOrientation: Int): Rect {
    return Rect(0, 0, previewSize.height, previewSize.height)
  }

  override fun setTransform(transform: Matrix?) {
    mFragment.textureView.setTransform(transform)
  }

  override fun isValid(qrCode: String?): Boolean {
    try {
      val complete = chunkedTestStatus.readChunk(qrCode)

      // The chunk is valid. Buzz the user
      runOnUiThread {
        getSystemService<Vibrator>(Vibrator::class.java)?.vibrate(
          VibrationEffect.createOneShot(
            500,
            VibrationEffect.DEFAULT_AMPLITUDE
          )
        )
      }
      if (complete)  return true

      // This is not the last chunk, so we say it's invalid and we'll keep getting new ones
      runOnUiThread {
        mFragment.progressPreference.title = resources.getString(R.string.scan_qr_code) +
          " ${chunkedTestStatus.chunk}/${testStatus.chunks}"
      }
      return false
    } catch(ex: TestStatus.TSException) {
      return false
    }
  }

  override fun handleSuccessfulResult(qrCode: String?) {
    Intent(this, DisplayResultActivity::class.java).also {
      testStatus.write(it)
      startActivity(it)
    }
  }
}
