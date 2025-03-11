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

import android.security.KeyStoreException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.android.devicediagnostics.Protos.AttestationInfo
import com.google.protobuf.ByteString
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

private const val TAG = "Attestation"
private const val EC_CURVE = "secp256r1"
private const val KEYSTORE_ALIAS = "attestation_collector_key"

private fun getAttestation(challenge: ByteArray): ByteArray {
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
    builder =
        builder::class
            .members
            .firstOrNull { it.name == "setAttestationIds" }
            ?.call(
                builder,
                intArrayOf(1, 2), // AttestationUtils.ID_TYPE_SERIAL, AttestationUtils.ID_TYPE_IMEI
            ) as KeyGenParameterSpec.Builder

    val spec = builder.build()

    val keyPairGenerator =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
    keyPairGenerator.initialize(spec)
    keyPairGenerator.generateKeyPair()

    var report = ByteArray(0)
    for (cert in keyStore.getCertificateChain(KEYSTORE_ALIAS)) {
        report +=
            ByteBuffer.allocate(Int.SIZE_BYTES).putInt(cert.encoded.size).array() + cert.encoded
    }

    return report
}

fun attestationCodeToString(code: Int): String {
    return when (code) {
        KeyStoreException.ERROR_ATTESTATION_CHALLENGE_TOO_LARGE -> "Challenge too large"
        KeyStoreException.ERROR_ATTESTATION_KEYS_UNAVAILABLE -> "Attestation keys unavailable"
        KeyStoreException.ERROR_ID_ATTESTATION_FAILURE -> "Device identifier error"
        KeyStoreException.ERROR_INCORRECT_USAGE -> "Incorrect usage"
        KeyStoreException.ERROR_INTERNAL_SYSTEM_ERROR -> "Internal system error"
        KeyStoreException.ERROR_KEYMINT_FAILURE -> "KeyMint error"
        KeyStoreException.ERROR_KEYSTORE_FAILURE -> "KeyStore error"
        KeyStoreException.ERROR_KEYSTORE_UNINITIALIZED -> "Keystore is uninitialized"
        KeyStoreException.ERROR_KEY_CORRUPTED -> "Key is corrupted"
        KeyStoreException.ERROR_KEY_DOES_NOT_EXIST -> "Key does not exist"
        KeyStoreException.ERROR_KEY_NOT_TEMPORALLY_VALID -> "Key expired or not yet usable"
        KeyStoreException.ERROR_KEY_OPERATION_EXPIRED -> "Key operation expired"
        KeyStoreException.ERROR_PERMISSION_DENIED -> "Permission denied"
        else -> "Unexpected KeyStore error"
    }
}

fun createAttestationRecord(challenge: ByteArray): AttestationInfo {
    val builder = AttestationInfo.newBuilder()

    var certs: ByteArray? = null
    var exception: Throwable? = null
    try {
        certs = getAttestation(challenge)
    } catch (e: Exception) {
        exception = e
    }

    var error: String? = null

    if (exception != null) {
        Log.e(TAG, "Attestation failed", exception)

        val providerException = exception as java.security.ProviderException?
        if (providerException != null && providerException.cause != null) {
            exception = providerException.cause
            Log.e(TAG, "Provider threw exception", exception)
        }

        val kse = exception as android.security.KeyStoreException?
        if (kse != null) {
            if (
                kse.isTransientFailure() &&
                    kse.getRetryPolicy() == KeyStoreException.RETRY_WHEN_CONNECTIVITY_AVAILABLE
            ) {
                error = "Network connection needed for attestation"
            } else {
                // toString() dumps too much info, so we reduce it.
                error = attestationCodeToString(kse.numericErrorCode)
            }
        }
    }

    if (certs != null) {
        builder.setCertificates(ByteString.copyFrom(certs))
    } else if (error != null) {
        builder.setError(error)
    } else {
        builder.setError(exception!!.message)
    }
    return builder.build()
}
