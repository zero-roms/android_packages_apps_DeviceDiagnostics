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
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.R
import com.android.devicediagnostics.TestStatus
import com.android.devicediagnostics.bluetooth.SendMessage
import com.android.settingslib.widget.LayoutPreference
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.random.Random

private const val BITMAP_SIZE = 512
private const val TAG = "trusted/QrCodeDisplay"
private fun displayQrCode(testStatus: TestStatus) : Bitmap {

    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(testStatus.toString(putAttestation = false),
                                  BarcodeFormat.QR_CODE, BITMAP_SIZE, BITMAP_SIZE)
    val bmp = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.RGB_565)
    for (x in 0 until BITMAP_SIZE)
        for (y in 0 until BITMAP_SIZE)
            bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
    return bmp
}

class QrCodeDisplayFragment : PreferenceFragmentCompat() {
    private var testStatus = TestStatus(challenge = Random.nextBytes(32))
    private val sendMessage = object : SendMessage() {
        override fun foundDevice(device: BluetoothDevice) {
            requireActivity().runOnUiThread() {
                Log.d(TAG, "Found device ${device.name}")

                if (deviceCategory.findPreference<Preference>(device.address) == null) {
                    val preference = Preference(requireActivity())
                    preference.title = device.name
                    preference.key = device.address
                    preference.setOnPreferenceClickListener {
                        setDevice(device)
                        true
                    }
                    deviceCategory.addPreference(preference)
                }
            }
        }

        override fun sent() {
            super.sent()
            Intent(activity, QrCodeScanActivity::class.java).also {
                testStatus.write(it)
                startActivity(it)
            }
        }
    }

    private lateinit var deviceCategory: PreferenceCategory

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        mRequestBlePermission.launch(arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))

        setPreferencesFromResource(R.xml.preferences_trusted_qrcode, rootKey)
        findPreference<Preference>("Continue")?.setOnPreferenceClickListener{
            Intent(activity, QrCodeScanActivity::class.java).also {
                testStatus.write(it)
                startActivity(it)
            }
            true
        }

        deviceCategory = findPreference<PreferenceCategory>("Devices")!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bmp = displayQrCode(testStatus)

        val layoutPreference = findPreference( "qr_code" ) as LayoutPreference?
        val image = layoutPreference?.findViewById<ImageView>(R.id.qrCode)
        image?.setImageBitmap(bmp)
    }

    override fun onStop() {
        super.onStop()
        sendMessage.stop()
    }

    private val mRequestBlePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it.entries.all { entry -> entry.value }) {
                sendMessage.send(testStatus.toString(putAttestation = false),
                                 this, requireActivity().application)
            }
        }
}

class QrCodeDisplayActivity : FragmentActivity(R.layout.activity_one_fragment) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, QrCodeDisplayFragment())
            }
        }
    }
}
