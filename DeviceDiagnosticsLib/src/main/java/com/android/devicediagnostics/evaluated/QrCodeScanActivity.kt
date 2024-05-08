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

import android.Manifest
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.ApplicationInterface
import com.android.devicediagnostics.PermissionsHelper
import com.android.devicediagnostics.Protos
import com.android.devicediagnostics.R
import com.android.devicediagnostics.bluetooth.BluetoothClient
import com.android.devicediagnostics.bluetooth.BluetoothConnectionData
import com.android.devicediagnostics.bluetooth.IBluetoothDevice
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.qrcode.QrCamera
import com.android.settingslib.widget.LayoutPreference

private const val TAG = "evaluated/QrCodeScan"

class QrCodeScanFragment() :
    TextureView.SurfaceTextureListener,
    BluetoothClient.ScanListener,
    QrCamera.ScannerCallback,
    PreferenceFragmentCompat() {
    private var textureView: TextureView? = null
    private var bluetoothClient: BluetoothClient? = null
    private var mCamera: QrCamera? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_evaluated_scan, rootKey)
        mCamera =
            (activity!!.applicationContext as ApplicationInterface).getQrCamera(activity!!, this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val layoutPreference = findPreference("qr_code") as LayoutPreference?
        textureView = layoutPreference!!.findViewById<TextureView>(R.id.preview_view)
        textureView!!.surfaceTextureListener = this
        textureView!!.outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 12f)
                }
            }
        textureView!!.clipToOutline = true
    }

    fun startBluetooth(connectionData: BluetoothConnectionData) {
        bluetoothClient =
            (activity!!.applicationContext as ApplicationInterface).getBluetoothClient()
        bluetoothClient!!.connectionData = connectionData
        bluetoothClient!!.start(this)

        requireActivity().runOnUiThread { setConnectionProgressVisibility(true) }
    }

    override fun onBluetoothConnected(device: IBluetoothDevice) {
        try {
            Log.d(TAG, "Found device ${device.name}")
            val bytes = bluetoothClient!!.read(device)
            val trustedDeviceInfo = Protos.TrustedDeviceInfo.parseFrom(bytes)
            requireActivity().runOnUiThread { onDeviceConnected(trustedDeviceInfo) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not connect to trusted device: $e")
        }
    }

    override fun onBluetoothConnectionFailed() {
        onBluetoothFailure()
    }

    private fun setConnectionProgressVisibility(visible: Boolean) {
        val pref = findPreference<Preference>("connection_progress")!!
        pref.isVisible = visible
    }

    private fun setConnectionFailedVisibility(visible: Boolean) {
        val pref = findPreference<Preference>("connection_failed")!!
        pref.isVisible = visible
    }

    private fun onDeviceConnected(trustedDeviceInfo: Protos.TrustedDeviceInfo) {
        setConnectionProgressVisibility(false)
        startFullTestFlow(requireActivity(), trustedDeviceInfo)
    }

    private fun onBluetoothFailure() {
        setConnectionProgressVisibility(false)
        setConnectionFailedVisibility(true)

        val bt = (requireActivity().applicationContext as ApplicationInterface).getBluetoothClient()
        bt.reset()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        mCamera?.start(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        mCamera?.stop()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    override fun handleCameraFailure() {
        mCamera?.stop()
    }

    override fun getViewSize(): Size {
        return Size(textureView!!.width, textureView!!.height)
    }

    override fun getFramePosition(previewSize: Size, cameraOrientation: Int): Rect {
        return Rect(0, 0, previewSize.height, previewSize.height)
    }

    override fun setTransform(transform: Matrix?) {
        textureView!!.setTransform(transform)
    }

    override fun isValid(qrCode: String?): Boolean {
        try {
            BluetoothConnectionData(qrCode!!)
        } catch (e: Exception) {
            return false
        }
        return true
    }

    override fun handleSuccessfulResult(qrCode: String?) {
        getSystemService<Vibrator>(activity!!, Vibrator::class.java)!!.vibrate(
            VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
        )
        startBluetooth(BluetoothConnectionData(qrCode!!))
    }
}

class QrCodeScanActivity : CollapsingToolbarBaseActivity() {
    private var mFragment = QrCodeScanFragment()

    private val permissions =
        PermissionsHelper(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            onPermissionsGranted()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_one_fragment)
        setTitle(R.string.evaluation_mode_title)
    }

    override fun onResume() {
        super.onResume()
        permissions.requestPermissions()
    }

    private fun onPermissionsGranted() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            add(R.id.fragment_container_view, mFragment)
        }
    }
}
