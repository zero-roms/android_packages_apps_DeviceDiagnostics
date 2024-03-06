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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.util.zip.Deflater

private const val EC_CURVE = "secp256r1"
private const val KEYSTORE_ALIAS = "attestation_collector_key"

private fun getAttestation(challenge: ByteArray, withImei: Boolean): ByteArray {
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
    if (withImei)
        builder =
            builder::class.members.firstOrNull { it.name == "setAttestationIds" }?.call(builder,
                intArrayOf(1, 2) // AttestationUtils.ID_TYPE_SERIAL, AttestationUtils.ID_TYPE_IMEI
            ) as KeyGenParameterSpec.Builder

    val spec = builder.build()

    val keyPairGenerator =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
    keyPairGenerator.initialize(spec)
    keyPairGenerator.generateKeyPair()

    var report = ByteArray(0)
    for (cert in keyStore.getCertificateChain(KEYSTORE_ALIAS)) {
        report += ByteBuffer.allocate(Int.SIZE_BYTES)
            .putInt(cert.encoded.size)
            .array() + cert.encoded
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
    deflater.end()

    return deflated
}

fun getAttestation(challenge: ByteArray): ByteArray {
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
