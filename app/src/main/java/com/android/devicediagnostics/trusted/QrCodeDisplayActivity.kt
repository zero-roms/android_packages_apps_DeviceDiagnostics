package com.android.devicediagnostics.trusted

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.LayoutPreference
import com.android.devicediagnostics.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.zip.CRC32
import kotlin.random.Random
import org.json.JSONObject

private const val BITMAP_SIZE = 512
private fun displayQrCode(data: ByteArray) : Bitmap {
    val code = Base64.encodeToString(data, 0)
    val crc = CRC32()
    crc.update(data)

    val json = JSONObject()
    json.put(VERSION, 1)
    json.put(CONTENTS, code)
    json.put(CRC32, crc.value)
    json.put(CHUNK, 1)
    json.put(CHUNKS, 1)

    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(json.toString(), BarcodeFormat.QR_CODE, BITMAP_SIZE, BITMAP_SIZE)
    val bmp = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.RGB_565)
    for (x in 0 until BITMAP_SIZE) {
        for (y in 0 until BITMAP_SIZE) {
            bmp.setPixel(
                x,
                y,
                if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            )
        }
    }
    return bmp
}

class QrCodeDisplayFragment : PreferenceFragmentCompat() {
    private var mChallenge = Random.Default.nextBytes(32)
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_trusted_qrcode, rootKey)
        findPreference<Preference>("Continue")?.setOnPreferenceClickListener{
            startActivity(Intent(activity,
                                 QrCodeScanActivity::class.java)
                            .putExtra(CHALLENGE, mChallenge))
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bmp = displayQrCode(mChallenge)

        val layoutPreference = findPreference( "qr_code" ) as LayoutPreference?
        val image = layoutPreference?.findViewById<ImageView>(R.id.qrCode)
        image?.setImageBitmap(bmp)
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
