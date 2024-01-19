package com.android.devicediagnostics.evaluated

import android.Manifest
import android.content.Intent
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.trusted.CHUNK
import com.android.devicediagnostics.trusted.CHUNKS
import com.android.devicediagnostics.trusted.CONTENTS
import com.android.devicediagnostics.trusted.CRC32
import com.android.devicediagnostics.LayoutPreference
import com.android.devicediagnostics.trusted.QRCODE
import com.android.devicediagnostics.R
import com.android.devicediagnostics.trusted.VERSION
import com.android.settingslib.qrcode.QrCamera
import java.util.zip.CRC32
import org.json.JSONException
import org.json.JSONObject

private const val TAG = "DeviceDiagnostics: EvaluatedQrCodeScanActivity"

class QrCodeScanFragment(listener: TextureView.SurfaceTextureListener) :
                                  PreferenceFragmentCompat() {
  private var mListener = listener
  private var mTextureView: TextureView? = null
  val textureView : TextureView
    get() { return mTextureView ?: throw AssertionError("texture view is null") }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_evaluated_scan, rootKey)
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
  private var mChunk = 1
  private var mChunks = 1
  private var mResult = ByteArray(0)
  private val mRequestPermission =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
          supportFragmentManager.commit {
            setReorderingAllowed(true)
            add(R.id.fragment_container_view, mFragment)
          }
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      mRequestPermission.launch(Manifest.permission.CAMERA)
    }
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
      if (qrCode == null) {
        Log.d(TAG, "No qr content")
        return false
      }
      val json = JSONObject(qrCode)
      if (json.getInt(VERSION) != 1) {
        Log.d(TAG, "Bad version ")
        return false
      }
      val contents = json.getString(CONTENTS)
      val bytes = Base64.decode(contents, 0)
      val crc = CRC32()
      crc.update(bytes)
      if (json.getLong(CRC32) != crc.value) {
        Log.d(TAG, "CRC doesn't match ")
        return false
      }
      if (json.getInt(CHUNK) != mChunk) {
        Log.d(TAG, "Wrong chunk")
        return false
      }
      if (json.getInt(CHUNKS) != mChunks && mChunk != 1) {
        Log.d(TAG, "Wrong total chunks")
        return false
      }

      // The chunk is valid. Buzz the user
      runOnUiThread {
        getSystemService<Vibrator>(this, Vibrator::class.java)!!.vibrate(
          VibrationEffect.createOneShot(
            500,
            VibrationEffect.DEFAULT_AMPLITUDE
          )
        )
      }
      mChunks = json.getInt(CHUNKS)
      mResult += bytes
      if (mChunk == mChunks) {
        return true
      }

      // This is not the last chunk, so we say it's invalid and we'll keep getting new ones
      mChunk += 1
      return false
    } catch (ex: JSONException) {
      Log.d(TAG, "JSON missing fields")
      return false
    }
  }

  override fun handleSuccessfulResult(qrCode: String?) {
    Intent(this, ScreenTestIntroActivity::class.java).also {
      TestStatus(challenge = mResult).write(it)
      startActivity(it)
    }
  }
}