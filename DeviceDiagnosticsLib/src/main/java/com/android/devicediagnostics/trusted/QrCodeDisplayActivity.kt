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
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.ApplicationInterface
import com.android.devicediagnostics.PermissionsHelper
import com.android.devicediagnostics.Protos.DeviceReport
import com.android.devicediagnostics.Protos.TrustedDeviceInfo
import com.android.devicediagnostics.R
import com.android.devicediagnostics.bluetooth.BluetoothConnectionData
import com.android.devicediagnostics.bluetooth.BluetoothServer
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.LayoutPreference
import com.google.protobuf.ByteString
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private const val BITMAP_SIZE = 512

private fun displayQrCode(connectionData: BluetoothConnectionData): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix =
        writer.encode(connectionData.toString(), BarcodeFormat.QR_CODE, BITMAP_SIZE, BITMAP_SIZE)
    val bmp = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.RGB_565)
    for (x in 0 until BITMAP_SIZE) for (y in 0 until BITMAP_SIZE) bmp.setPixel(
        x,
        y,
        if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
    )
    return bmp
}

class QrCodeDisplayFragment() : PreferenceFragmentCompat(), BluetoothServer.StartListener {
    private var connectionData: BluetoothConnectionData? = null
    private var state: TrustedState? = null
    private var viewCreated: Boolean = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_trusted_qrcode, rootKey)

        val app = ApplicationInterface.app
        val challenge = app.getRandomBytes(32)
        state = TrustedState(challenge, DeviceReport.getDefaultInstance())

        val infoBuilder = TrustedDeviceInfo.newBuilder()
        infoBuilder.setChallenge(ByteString.copyFrom(challenge))
        val info = infoBuilder.build()

        app.getBluetoothServer().start(info, this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewCreated = true

        if (connectionData != null) {
            onServerAvailable(connectionData!!)
        }
    }

    private fun drawQrCode() {
        val bmp = displayQrCode(connectionData!!)

        val layoutPreference = findPreference("qr_code") as LayoutPreference?
        val image = layoutPreference?.findViewById<ImageView>(R.id.qrCode)
        image?.setImageBitmap(bmp)
    }

    override fun onServerAvailable(connectionData: BluetoothConnectionData) {
        if (isDetached) {
            return
        }

        this.connectionData = connectionData

        if (viewCreated) {
            drawQrCode()
        }
    }

    override fun onServerStarted() {
        if (isDetached) {
            return
        }
        launchTrustedActivity(activity!!, WaitForResultActivity::class.java.name, state)
        activity!!.finish()
    }

    override fun onServerError() {
        if (isDetached) {
            return
        }
        findPreference<Preference>("Continue")!!.also {
            it.setTitle(R.string.bluetooth_error_title)
            it.setSummary(R.string.bluetooth_error_summary)
        }
    }
}

class QrCodeDisplayActivity : CollapsingToolbarBaseActivity() {
    init {}

    private val permissions =
        PermissionsHelper(
            this,
            arrayOf(
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

    private fun onPermissionsGranted() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            add(R.id.fragment_container_view, QrCodeDisplayFragment())
        }
    }

    override fun onResume() {
        super.onResume()
        permissions.requestPermissions()
    }
}
