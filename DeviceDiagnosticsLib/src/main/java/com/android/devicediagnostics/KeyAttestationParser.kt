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

import com.google.android.attestation.AttestationApplicationId
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
import java.util.Arrays
import java.util.Optional
import java.util.zip.Inflater
import org.bouncycastle.util.encoders.Base64

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
 *
 * 1. Loading the certificates from PEM-encoded strings.
 *
 * 2. Verifying the certificate chain, up to the root. Note that this example does NOT require
 * the root certificate to appear within Google's list of root certificates. However, if you're
 * verifying the properties of hardware-backed keys on a device that ships with hardware-level key
 * attestation, Android 7.0 (API level 24) or higher, and Google Play services, your production code
 * should enforce this requirement.
 *
 * 3. Checking if any certificate in the chain has been revoked or suspended.
 *
 * 4. Extracting the attestation extension data from the attestation certificate.
 *
 * 5. Verifying (and printing) several important data elements from the attestation extension.
 */

class AttestationResult(val record: ParsedAttestationRecord, val verified: Boolean) {
}

fun getAttestation(attestation: ByteArray): AttestationResult {
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

    val certs = loadCertificates(inflated)
    val verified = verifyCertificateChain(certs)
    val record = ParsedAttestationRecord.createParsedAttestationRecord(certs)
    return AttestationResult(record, verified)
}

private fun printRootOfTrust(rootOfTrust: Optional<RootOfTrust>, indent: String) {
    if (rootOfTrust.isPresent) {
        println(
            indent
                + "Verified Boot Key: "
                + Base64.toBase64String(rootOfTrust.get().verifiedBootKey)
        )
        println(indent + "Device Locked: " + rootOfTrust.get().deviceLocked)
        println(
            indent + "Verified Boot State: " + rootOfTrust.get().verifiedBootState.name
        )
        println(
            indent
                + "Verified Boot Hash: "
                + Base64.toBase64String(rootOfTrust.get().verifiedBootHash)
        )
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
        try {
            val certStatus = CertificateRevocationStatus
                .fetchStatus(cert.serialNumber)
            if (certStatus != null) {
                throw CertificateException(
                    "Certificate revocation status is " + certStatus.status.name
                )
            }
        } catch (e: IOException) {
            throw IOException("Unable to fetch certificate status. Check connectivity.")
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
