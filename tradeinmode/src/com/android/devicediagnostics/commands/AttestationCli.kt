/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.devicediagnostics.commands

import com.android.devicediagnostics.AttestationResult
import com.android.devicediagnostics.checkAttestation
import com.android.devicediagnostics.getVerifiedBootState
import com.android.devicediagnostics.isDeviceLocked
import com.google.android.attestation.AuthorizationList
import com.google.android.attestation.ParsedAttestationRecord
import com.google.common.io.CharStreams
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Optional
import kotlin.system.exitProcess
import org.bouncycastle.util.encoders.Base64
import org.json.JSONArray
import org.json.JSONObject

private class Tokenizer(private val args: Array<String>) {
    private var cursor = 0

    fun next(): String? {
        if (cursor >= args.size) return null
        return args[cursor++]
    }

    fun more(): Boolean {
        return cursor < args.size
    }
}

class AttestationCli {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                doMain(Tokenizer(args))
            } catch (e: Exception) {
                System.err.println("Error: $e")
                exitProcess(1)
            }
        }
    }
}

private fun doMain(args: Tokenizer) {
    var challenge: ByteArray? = null

    var path = args.next()
    if (path == "--challenge") {
        val challengeString = args.next()
        if (challengeString == null) {
            throw IllegalArgumentException("Expected challenge string")
        }

        challenge = challengeString.toByteArray(StandardCharsets.UTF_8)
        path = args.next()
    }

    var stream: InputStream?
    if (path != null) {
        stream = Files.newInputStream(Paths.get(path))
    } else {
        stream = System.`in`
    }

    val text = CharStreams.toString(InputStreamReader(stream, Charsets.UTF_8))
    val root = JSONObject(text)
    if (!root.has("attestation")) {
        throw IllegalArgumentException("No attestation record was found")
    }
    val encodedData = root.getString("attestation")
    if (encodedData.isEmpty()) {
        throw IllegalArgumentException("No attestation record was found")
    }

    val decodedData = java.util.Base64.getMimeDecoder().decode(encodedData)
    val result = checkAttestation(decodedData, null)
    val output = JSONObject()
    output.put("certificate", result.second.toString().lowercase())
    output.put("record", recordToJson(result.first))
    output.put("trustworthy", getTrustworthiness(result.first, result.second, challenge))
    println(output.toString(2))
}

private fun recordToJson(record: ParsedAttestationRecord?): JSONObject? {
    if (record == null) {
        return null
    }

    val root = JSONObject()
    root.put("bootloader_locked", isDeviceLocked(record))
    root.put("verified_boot", getVerifiedBootState(record))
    root.put("security_level", record.attestationSecurityLevel.name)
    root.put("keymaster_version", record.keymasterVersion.toString())
    root.put("keymaster_security_level", record.keymasterSecurityLevel.name)

    var attrs = authorizationListToJson(record.teeEnforced)
    if (attrs != null) {
        attrs.put("source", "hardware")
    } else {
        attrs = authorizationListToJson(record.softwareEnforced)
        if (attrs != null) {
            attrs.put("source", "software")
        }
    }
    root.put("attributes", attrs)

    return root
}

private fun authorizationListToJson(list: AuthorizationList?): JSONObject? {
    if (list == null) {
        return null
    }

    val root = JSONObject()
    putOptionalInt(root, "os_version", list.osVersion)
    putOptionalString(root, "brand", list.attestationIdBrand)
    putOptionalString(root, "device", list.attestationIdDevice)
    putOptionalString(root, "product", list.attestationIdProduct)
    putOptionalString(root, "serial", list.attestationIdSerial)
    putOptionalString(root, "meid", list.attestationIdMeid)
    putOptionalString(root, "manufacturer", list.attestationIdManufacturer)
    putOptionalString(root, "model", list.attestationIdModel)
    putOptionalInt(root, "vendor_patch_level", list.vendorPatchLevel)
    putOptionalInt(root, "boot_patch_level", list.bootPatchLevel)

    val imeis = JSONArray()
    putOptionalString(imeis, list.attestationIdImei)
    putOptionalString(imeis, list.attestationIdSecondImei)
    if (imeis.length() > 0) {
        root.put("imeis", imeis)
    }

    if (root.length() == 0) {
        return null
    }
    return root
}

private fun getTrustworthiness(
    record: ParsedAttestationRecord?,
    result: AttestationResult,
    challenge: ByteArray?,
): String {
    if (record == null || result != AttestationResult.VERIFIED) {
        return "unverified certificate"
    }
    if (challenge != null && !record.attestationChallenge.contentEquals(challenge)) {
        return "bad challenge"
    }
    if (!getVerifiedBootState(record)) {
        return "verified boot disabled"
    }
    if (!isDeviceLocked(record)) {
        return "bootloader unlocked"
    }
    if (challenge == null) {
        return "no challenge"
    }
    return "yes"
}

private fun putOptionalString(list: JSONArray, value: Optional<ByteArray>?) {
    if (value == null || !value.isPresent) {
        return
    }
    list.put(value.get().toString(Charsets.UTF_8))
}

private fun putOptionalString(root: JSONObject, key: String, value: Optional<ByteArray>?) {
    if (value == null || !value.isPresent) {
        return
    }
    root.put(key, value.get().toString(Charsets.UTF_8))
}

private fun putOptionalInt(root: JSONObject, key: String, value: Optional<Int>?) {
    if (value == null || !value.isPresent) {
        return
    }
    root.put(key, value.get())
}
