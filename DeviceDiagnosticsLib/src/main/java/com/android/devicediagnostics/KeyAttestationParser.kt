/* Copyright 2016, The Android Open Source Project, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.devicediagnostics

import android.app.Activity
import android.util.Log
import com.android.devicediagnostics.Protos.DeviceReport
import com.google.android.attestation.CertificateRevocationStatus
import com.google.android.attestation.Constants
import com.google.android.attestation.ParsedAttestationRecord
import com.google.common.collect.ImmutableList
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.Inflater
import org.bouncycastle.util.encoders.Base64

private const val TAG = "Attestation"

/**
 * This is an illustration of how you can use the Bouncy Castle ASN.1 parser to extract information
 * from an Android attestation data structure. On a secure server that you trust, create similar
 * logic to verify that a key pair has been generated in an Android device. The app on the device
 * must retrieve the key's certificate chain using KeyStore.getCertificateChain(), then send the
 * contents to the trusted server.
 *
 * In this example, the certificate chain includes hard-coded excerpts of each certificate.
 *
 * This example demonstrates the following tasks:
 * 1. Loading the certificates from PEM-encoded strings.
 * 2. Verifying the certificate chain, up to the root. Note that this example does NOT require the
 *    root certificate to appear within Google's list of root certificates. However, if you're
 *    verifying the properties of hardware-backed keys on a device that ships with hardware-level
 *    key attestation, Android 7.0 (API level 24) or higher, and Google Play services, your
 *    production code should enforce this requirement.
 * 3. Checking if any certificate in the chain has been revoked or suspended.
 * 4. Extracting the attestation extension data from the attestation certificate.
 * 5. Verifying (and printing) several important data elements from the attestation extension.
 */
enum class AttestationResult {
    // There was a non-network error performing attestation.
    GENERIC_ERROR,
    // There was a network error perform attestation.
    NETWORK_ERROR,
    // The certificate could not be verified.
    UNVERIFIED,
    // No attempt was made to verify the certificate.
    SKIPPED_VERIFICATION,
    // The certificate was verified.
    VERIFIED
}

class AttestationController(val challenge: ByteArray, val selfCheck: Boolean) {
    interface Callbacks {
        fun onAttestationReceived(result: Pair<ParsedAttestationRecord, AttestationResult>)
        fun onAttestationRetry()
        fun onAttestationError()
    }

    // If "selfCheck" is true, we allow network verification to soft fail.
    fun verifyAttestation(activity: Activity, report: DeviceReport, callbacks: Callbacks) {
        // Attestation check does a network lookup, so must be on separate thread
        val app = activity.applicationContext as ApplicationInterface
        runInBackground {
            var attestation = app.verifyAttestation(report.attestation.toByteArray(), challenge)
            if (attestation.second == AttestationResult.NETWORK_ERROR && selfCheck)
                attestation = Pair(attestation.first, AttestationResult.SKIPPED_VERIFICATION)

            activity.runOnUiThread {
                if (attestation.second == AttestationResult.GENERIC_ERROR) {
                    callbacks.onAttestationError()
                } else if (attestation.second == AttestationResult.NETWORK_ERROR) {
                    callbacks.onAttestationRetry()
                } else {
                    callbacks.onAttestationReceived(Pair(attestation.first!!, attestation.second))
                }
            }
        }
    }
}

fun getAttestation(
    attestation: ByteArray,
    challenge: ByteArray
): Pair<ParsedAttestationRecord?, AttestationResult> {
    val inflater = Inflater()
    inflater.setInput(attestation, 0, attestation.size)
    var inflated = ByteArray(0)
    while (true) {
        val inflatedChunk = ByteArray(256)
        val inflatedBytes = inflater.inflate(inflatedChunk)
        if (inflatedBytes == 0) break
        inflated += inflatedChunk.take(inflatedBytes).toByteArray()
    }
    inflater.end()

    var certs: ImmutableList<X509Certificate>?
    var record: ParsedAttestationRecord? = null

    try {
        certs = loadCertificates(inflated)
        record = ParsedAttestationRecord.createParsedAttestationRecord(certs)
        if (verifyCertificateChain(certs)) {
            if (record.attestationChallenge.contentEquals(challenge)) {
                return Pair(record, AttestationResult.VERIFIED)
            }
            Log.e(TAG, "Attestation failed: challenge does not match")
        }
        return Pair(record, AttestationResult.UNVERIFIED)
    } catch (e: Exception) {
        // Note: set record so we can distinguish between parsing errors and network errors.
        Log.e(TAG, "Attestation failed: $e")
        if (record != null && e is IOException) {
            return Pair(record, AttestationResult.NETWORK_ERROR)
        }
        return Pair(record, AttestationResult.GENERIC_ERROR)
    }
}

private fun verifyCertificateChain(certs: List<X509Certificate>): Boolean {
    var parent = certs[certs.size - 1]
    for (i in certs.indices.reversed()) {
        val cert = certs[i]
        // Verify that the certificate has not expired.
        cert.checkValidity()
        cert.verify(parent.publicKey)
        parent = cert
        val certStatus = CertificateRevocationStatus.fetchStatus(cert.serialNumber)
        if (certStatus != null) {
            throw CertificateException("Certificate revocation status is " + certStatus.status.name)
        }
    }

    // If the attestation is trustworthy and the device ships with hardware-
    // backed key attestation, Android 7.0 (API level 24) or higher, and
    // Google Play services, the root certificate should be signed with the
    // Google attestation root key.
    val googleRootCaPubKey = Base64.decode(Constants.GOOGLE_ROOT_CA_PUB_KEY)
    return googleRootCaPubKey.contentEquals(certs[certs.size - 1].publicKey.encoded)
}

private fun loadCertificates(attestation: ByteArray): ImmutableList<X509Certificate> {
    val certs = ImmutableList.Builder<X509Certificate>()
    val factory = CertificateFactory.getInstance("X.509")
    val buffer = ByteBuffer.wrap(attestation)
    while (buffer.remaining() > 0) {
        val size = buffer.int
        val encodedCert = ByteArray(size)
        buffer[encodedCert]
        val inputStream = ByteArrayInputStream(encodedCert)
        certs.add(factory.generateCertificate(inputStream) as X509Certificate)
    }
    return certs.build()
}
