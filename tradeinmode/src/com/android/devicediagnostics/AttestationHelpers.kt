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
package com.android.devicediagnostics

import com.google.android.attestation.CertificateRevocationStatus
import com.google.android.attestation.Constants
import com.google.android.attestation.ParsedAttestationRecord
import com.google.android.attestation.RootOfTrust
import com.google.common.collect.ImmutableList
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import org.bouncycastle.util.encoders.Base64

enum class AttestationResult {
    // There was a non-network error performing attestation.
    GENERIC_ERROR,
    // There was a network error perform attestation.
    NETWORK_ERROR,
    // The certificate could not be verified.
    UNVERIFIED,
    // No attempt was made to verify the certificate.
    SKIPPED_VERIFICATION,
    // Certificate was verified but the challenge was incorrect.
    BAD_CHALLENGE,
    // The certificate was verified.
    VERIFIED,
}

// Verify a device's attestation record. If a challenge is provided, verify the
// challenge as well.
public fun checkAttestation(
    attestation: ByteArray,
    challenge: ByteArray?,
): Pair<ParsedAttestationRecord?, AttestationResult> {
    var certs: ImmutableList<X509Certificate>?
    var record: ParsedAttestationRecord? = null

    try {
        certs = loadCertificates(attestation)
        record = ParsedAttestationRecord.createParsedAttestationRecord(certs)
        if (!verifyCertificateChain(certs)) {
            return Pair(record, AttestationResult.UNVERIFIED)
        }
        if (challenge != null && !record.attestationChallenge.contentEquals(challenge)) {
            return Pair(record, AttestationResult.BAD_CHALLENGE)
        }
        return Pair(record, AttestationResult.VERIFIED)
    } catch (e: Exception) {
        // Note: set record so we can distinguish between parsing errors and network errors.
        if (record != null && e is IOException) {
            return Pair(record, AttestationResult.NETWORK_ERROR)
        }
        return Pair(record, AttestationResult.GENERIC_ERROR)
    }
}

public fun isDeviceLocked(record: ParsedAttestationRecord): Boolean {
    val root = record.teeEnforced.rootOfTrust
    return root.isPresent && root.get().deviceLocked
}

public fun getVerifiedBootState(record: ParsedAttestationRecord): Boolean {
    val root = record.teeEnforced.rootOfTrust
    if (root.isPresent && root.get().verifiedBootState == RootOfTrust.VerifiedBootState.VERIFIED) {
        return true
    }
    return false
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
