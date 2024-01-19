package com.android.devicediagnostics.evaluated

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import android.graphics.Bitmap
import android.graphics.Color
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.View
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.devicediagnostics.trusted.CHUNK
import com.android.devicediagnostics.trusted.CHUNKS
import com.android.devicediagnostics.trusted.CONTENTS
import com.android.devicediagnostics.trusted.CRC32
import com.android.devicediagnostics.LayoutPreference
import com.android.devicediagnostics.R
import com.android.devicediagnostics.trusted.SCREEN_CONDITION
import com.android.devicediagnostics.trusted.TOUCH_CONDITION
import com.android.devicediagnostics.trusted.VERSION
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.json.JSONObject

private const val BITMAP_SIZE = 2048
private const val CHUNK_SIZE = 1024

private const val EC_CURVE = "secp256r1"
private const val KEYSTORE_ALIAS = "attestation_collector_key"

private fun getAttestation(challenge: ByteArray, withImei: Boolean) : ByteArray {
  val keyStore = KeyStore.getInstance("AndroidKeyStore")
  keyStore.load(null)
  keyStore.deleteEntry(KEYSTORE_ALIAS)
  val keyPurpose = KeyProperties::PURPOSE_SIGN.get() or KeyProperties::PURPOSE_VERIFY.get()
  var builder =
    KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, keyPurpose)
      .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
      .setDigests(KeyProperties.DIGEST_SHA256)
      .setAttestationChallenge(challenge)

  // Use reflection to call this system API so we can still build in Android Studio
  if(withImei)
    builder = builder::class.members.firstOrNull { it.name == "setAttestationIds" }?.call(builder,
      intArrayOf(1, 2) // AttestationUtils.ID_TYPE_SERIAL, AttestationUtils.ID_TYPE_IMEI
    ) as KeyGenParameterSpec.Builder

  val spec = builder.build()

  val keyPairGenerator =
    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
  keyPairGenerator.initialize(spec)
  keyPairGenerator.generateKeyPair()

  var report = ByteArray(0)
  for (cert in keyStore.getCertificateChain(KEYSTORE_ALIAS)) {
    report += ByteBuffer.allocate(Int.SIZE_BYTES).putInt(cert.encoded.size).array() + cert.encoded
  }

  val deflater = Deflater()
  deflater.setInput(report)
  deflater.finish()
  var deflated = ByteArray(0)
  while (true) {
    val deflatedChunk = ByteArray(256)
    val deflatedBytes = deflater.deflate(deflatedChunk)
    if (deflatedBytes == 0) break
    deflated += deflatedChunk.take(deflatedBytes).toByteArray()
  }

  return deflated
}

private fun attest(challenge: ByteArray): ByteArray {
  // On devices without device IDs provisioned, the above code will throw ProviderException
  // Continue without this data to support testing other functionality
  return try {
    getAttestation(challenge, withImei = true)
  } catch (e: java.security.ProviderException) {
    getAttestation(challenge, withImei = false)
  } catch (e: SecurityException) {
    getAttestation(challenge, withImei = false)
  }
}

class QrDisplayFragment(private var testStatus: TestStatus) : PreferenceFragmentCompat() {
  private var mChunk = 1
  private var mChunks = 1
  private var mChunkSize = 0
  private var mAttestation = ByteArray(0)
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
      if (mChunk < mChunks) mChunk += 1
      if (mChunk == mChunks) mNextPreference.isEnabled = false
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
      mAttestation = attest(testStatus.challenge)
      requireActivity().runOnUiThread() {
        mChunks = (mAttestation.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        mChunkSize = (mAttestation.size + mChunks - 1) / mChunks
        if (mChunks > 1) {
          mNextPreference.isVisible = true
          mPreviousPreference.isVisible = true
          mPreviousPreference.isEnabled = false
        }
        drawQRCode()
      }
    }.start()
  }

  private fun drawQRCode() {
    Thread {
      var topOfSlice = mChunk * mChunkSize - 1
      if (topOfSlice >= mAttestation.size) topOfSlice = mAttestation.size - 1
      val slice = mAttestation.slice((mChunk - 1) * mChunkSize..topOfSlice).toByteArray()
      val code = Base64.encodeToString(slice, 0)
      val crc = CRC32()
      crc.update(slice)

      val json = JSONObject()
      json.put(VERSION, 1)
      json.put(CONTENTS, code)
      json.put(CRC32, crc.value)
      json.put(CHUNK, mChunk)
      json.put(CHUNKS, mChunks)
      json.put(SCREEN_CONDITION, testStatus.screenTest)
      json.put(TOUCH_CONDITION, testStatus.touchTest)

      val writer = QRCodeWriter()
      val bitMatrix =
        writer.encode(json.toString(), BarcodeFormat.QR_CODE, BITMAP_SIZE, BITMAP_SIZE)
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

      requireActivity().runOnUiThread() {
        val layoutPreference = findPreference("qr_code") as LayoutPreference?
        val image = layoutPreference?.findViewById<ImageView>(R.id.qrCode)
        image?.setImageBitmap(bmp)
        mProgressPreference.title = "$mChunk/$mChunks"
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