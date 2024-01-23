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

package com.android.devicediagnostics

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import java.util.zip.CRC32
import org.json.JSONException
import org.json.JSONObject

// Intent extras and JSON fields
private const val ATTESTATION = "ATTESTATION"
private const val CHALLENGE = "CHALLENGE"
private const val SCREEN_TEST = "SCREEN_TEST"
private const val TOUCH_TEST = "TOUCH_TEST"

// More JSON fields
private const val VERSION = "VERSION"
private const val CHALLENGE_CRC32 = "CHALLENGE_CRC32"
private const val ATTESTATION_CRC32 = "ATTESTATION_CRC32"
private const val CHUNK = "CHUNK"
private const val CHUNKS = "CHUNKS"

private const val VERSION_NUMBER = 1
private const val CHUNK_SIZE = 1024

private const val TAG = "TestStatus"

open class TestStatus (
  var challenge: ByteArray = byteArrayOf(),
  var screenTest: Boolean = false,
  var touchTest: Boolean = false,
  var attestation: ByteArray = byteArrayOf(),
  ) {
  constructor(bundle: Bundle) : this(
    // Support string challenge so intents can be launched from am
    challenge = bundle.getByteArray(CHALLENGE) ?: bundle.getString(CHALLENGE)?.toByteArray()
      ?: throw AssertionError("Missing challenge"),
    screenTest = bundle.getBoolean(SCREEN_TEST),
    touchTest = bundle.getBoolean(TOUCH_TEST),
    attestation = bundle.getByteArray(ATTESTATION) ?: byteArrayOf()
  )

  fun write(intent: Intent) {
    intent.putExtra(CHALLENGE, challenge)
    intent.putExtra(SCREEN_TEST, screenTest)
    intent.putExtra(TOUCH_TEST, touchTest)
    intent.putExtra(ATTESTATION, attestation)
  }

  class TSException(message: String) : Exception(message) {
    init {
      Log.d(TAG, message)
    }
  }

  constructor(string: String?, getAttestation: Boolean = true) : this() {
    if (string == null) throw TSException("Empty QR code")
    try {
      val json = JSONObject(string)
      if (json.getInt(VERSION) != VERSION_NUMBER) throw TSException("Bad version")
      challenge = Base64.decode(json.getString(CHALLENGE), 0)
      val crc = CRC32()
      crc.update(challenge)
      if (json.getLong(CHALLENGE_CRC32) != crc.value) throw TSException("Bad challenge CRC")

      screenTest = json.getBoolean(SCREEN_TEST)
      touchTest = json.getBoolean(TOUCH_TEST)
      if (getAttestation)
        attestation = Base64.decode(json.getString(ATTESTATION), 0)
    } catch (ex: JSONException) {
      throw TSException("JSON missing required fields")
    }
  }

  fun toString(putAttestation: Boolean = true, chunk: Int = 0): String {
    val json = JSONObject()
    json.put(VERSION, VERSION_NUMBER)
    json.put(CHALLENGE, Base64.encodeToString(challenge, 0))
    val crc = CRC32()
    crc.update(challenge)
    json.put(CHALLENGE_CRC32, crc.value)

    json.put(SCREEN_TEST, screenTest)
    json.put(TOUCH_TEST, touchTest)
    if (putAttestation) {
      var topOfSlice = chunk * chunkSize - 1
      if (topOfSlice >= attestation.size) topOfSlice = attestation.size - 1
      val slice = attestation.slice((chunk - 1) * chunkSize..topOfSlice).toByteArray()
      val code = Base64.encodeToString(slice, 0)
      crc.reset()
      crc.update(slice)

      json.put(ATTESTATION, code)
      json.put(ATTESTATION_CRC32, crc.value)
      json.put(CHUNK, chunk)
      json.put(CHUNKS, chunks)
    }

    return json.toString()
  }

  open val chunks: Int
    get() = (attestation.size + CHUNK_SIZE - 1) / CHUNK_SIZE

  private val chunkSize: Int
    get() = (attestation.size + chunks - 1) / chunks
}

class ChunkedTestStatus(bundle: Bundle) : TestStatus(bundle) {

  // Read a chunk.
  // Return false if valid but not last chunk
  // Return true if valid and last chunk
  // Otherwise throw TSException
  fun readChunk(string: String?): Boolean {
    if (string == null) throw TSException("Empty QR code")
    try {
      val json = JSONObject(string)
      if (json.getInt(CHUNK) != chunk) throw TSException("Wrong chunk")
      val contents = json.getString(ATTESTATION)
      val bytes = Base64.decode(contents, 0)
      val crc = CRC32()
      crc.update(bytes)
      if (json.getLong(ATTESTATION_CRC32) != crc.value) throw TSException("CRC doesn't match ")

      val testStatus = TestStatus(string, getAttestation = false)
      if (!testStatus.challenge.contentEquals(challenge)) throw TSException("Wrong challenge")
      if (chunk == 1) {
        screenTest = testStatus.screenTest
        touchTest = testStatus.touchTest
        chunks = json.getInt(CHUNKS)
      } else {
        if (screenTest != testStatus.screenTest) throw TSException("Inconsistent screen condition")
        if (touchTest != testStatus.touchTest) throw TSException("Inconsistent touch condition")
        if (chunks != json.getInt(CHUNKS)) throw TSException("Inconsistent chunk count")
      }

      attestation += bytes
      chunk++
      return chunk > chunks
    } catch (ex: JSONException) {
      throw TSException("JSON missing required fields")
    }
  }

  var chunk = 1
    private set

  override var chunks = 0
    private set
}