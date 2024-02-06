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
import com.google.android.attestation.AuthorizationList
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
private var output = ""
private fun println(stuff: String) {
  output += "$stuff\n"
}

fun getAttestation(attestation: ByteArray): ParsedAttestationRecord {
  val inflater = Inflater()
  inflater.setInput(attestation, 0, attestation.size)
  var inflated = ByteArray(0)
  while(true) {
    val inflatedChunk = ByteArray(256)
    val inflatedBytes = inflater.inflate(inflatedChunk)
    if (inflatedBytes == 0) break
    inflated += inflatedChunk.take(inflatedBytes).toByteArray()
  }
  inflater.end()

  val certs = loadCertificates(inflated)
  verifyCertificateChain(certs)
  return ParsedAttestationRecord.createParsedAttestationRecord(certs)
}

fun toString(parsedAttestationRecord: ParsedAttestationRecord): String {
  val indent = "\t"
  output = ""
  println("Attestation version: " + parsedAttestationRecord.attestationVersion)
  println(
    "Attestation Security Level: " + parsedAttestationRecord.attestationSecurityLevel.name
  )
  println("Keymaster Version: " + parsedAttestationRecord.keymasterVersion)
  println(
    "Keymaster Security Level: " + parsedAttestationRecord.keymasterSecurityLevel.name
  )
  println(
    "Attestation Challenge: "
      + parsedAttestationRecord.attestationChallenge.toString(Charsets.UTF_8)
  )
  println("Unique ID: " + Arrays.toString(parsedAttestationRecord.uniqueId))
  println("Software Enforced Authorization List:")
  val softwareEnforced = parsedAttestationRecord.softwareEnforced
  printAuthorizationList(softwareEnforced, indent)
  println("TEE Enforced Authorization List:")
  val teeEnforced = parsedAttestationRecord.teeEnforced
  printAuthorizationList(teeEnforced, indent)
  return output
}

private fun printAuthorizationList(
  authorizationList: AuthorizationList,
  @Suppress("SameParameterValue") indent: String,
) {
  // Detailed explanation of the keys and their values can be found here:
  // https://source.android.com/security/keystore/tags
  printOptional(authorizationList.purpose, indent + "Purpose(s)")
  printOptional(authorizationList.algorithm, indent + "Algorithm")
  printOptional(authorizationList.keySize, indent + "Key Size")
  printOptional(authorizationList.digest, indent + "Digest")
  printOptional(authorizationList.padding, indent + "Padding")
  printOptional(authorizationList.ecCurve, indent + "EC Curve")
  printOptional(authorizationList.rsaPublicExponent, indent + "RSA Public Exponent")
  println(indent + "Rollback Resistance: " + authorizationList.rollbackResistance)
  printOptional(authorizationList.activeDateTime, indent + "Active DateTime")
  printOptional(
    authorizationList.originationExpireDateTime, indent + "Origination Expire DateTime"
  )
  printOptional(authorizationList.usageExpireDateTime, indent + "Usage Expire DateTime")
  println(indent + "No Auth Required: " + authorizationList.noAuthRequired)
  printOptional(authorizationList.userAuthType, indent + "User Auth Type")
  printOptional(authorizationList.authTimeout, indent + "Auth Timeout")
  println(indent + "Allow While On Body: " + authorizationList.allowWhileOnBody)
  println(
    indent
      + "Trusted User Presence Required: "
      + authorizationList.trustedUserPresenceRequired
  )
  println(
    indent + "Trusted Confirmation Required: " + authorizationList.trustedConfirmationRequired
  )
  println(
    indent + "Unlocked Device Required: " + authorizationList.unlockedDeviceRequired
  )
  println(indent + "All Applications: " + authorizationList.allApplications)
  printOptional(authorizationList.applicationId, indent + "Application ID")
  printOptional(authorizationList.creationDateTime, indent + "Creation DateTime")
  printOptional(authorizationList.origin, indent + "Origin")
  println(indent + "Rollback Resistant: " + authorizationList.rollbackResistant)
  if (authorizationList.rootOfTrust.isPresent) {
    println(indent + "Root Of Trust:")
    printRootOfTrust(authorizationList.rootOfTrust, indent + "\t")
  }
  printOptional(authorizationList.osVersion, indent + "OS Version")
  printOptional(authorizationList.osPatchLevel, indent + "OS Patch Level")
  if (authorizationList.attestationApplicationId.isPresent) {
    println(indent + "Attestation Application ID:")
    printAttestationApplicationId(authorizationList.attestationApplicationId, indent + "\t")
  }
  printOptional(
    authorizationList.attestationApplicationIdBytes,
    indent + "Attestation Application ID Bytes"
  )
  printOptional(authorizationList.attestationIdBrand, indent + "Attestation ID Brand")
  printOptional(authorizationList.attestationIdDevice, indent + "Attestation ID Device")
  printOptional(authorizationList.attestationIdProduct, indent + "Attestation ID Product")
  printOptional(authorizationList.attestationIdSerial, indent + "Attestation ID Serial",
                base64encode = false)

  printOptional(authorizationList.attestationIdImei,
                indent + "Attestation ID IMEI", base64encode = false)
  printOptional(authorizationList.attestationIdSecondImei,
                indent + "Attestation ID SECOND IMEI", base64encode = false)
  printOptional(authorizationList.attestationIdMeid, indent + "Attestation ID MEID")
  printOptional(
    authorizationList.attestationIdManufacturer, indent + "Attestation ID Manufacturer"
  )
  printOptional(authorizationList.attestationIdModel, indent + "Attestation ID Model")
  printOptional(authorizationList.vendorPatchLevel, indent + "Vendor Patch Level")
  printOptional(authorizationList.bootPatchLevel, indent + "Boot Patch Level")
  println(
    indent + "Identity Credential Key: " + authorizationList.identityCredentialKey
  )
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

private fun printAttestationApplicationId(
  attestationApplicationId: Optional<AttestationApplicationId>, indent: String
) {
  if (attestationApplicationId.isPresent) {
    println(indent + "Package Info (<package name>, <version>): ")
    for (info in attestationApplicationId.get().packageInfos) {
      println(indent + "\t" + info.packageName + ", " + info.version)
    }
    println(indent + "Signature Digests:")
    for (digest in attestationApplicationId.get().signatureDigests) {
      println(indent + "\t" + Base64.toBase64String(digest))
    }
  }
}

private fun <T> printOptional(optional: Optional<T>, caption: String, base64encode: Boolean = true) {
  if (optional.isPresent) {
    if (optional.get() is ByteArray) {
      if (base64encode) {
        println(caption + ": " + Base64.toBase64String(optional.get() as ByteArray))
      } else {
        println(caption + ": " + (optional.get() as ByteArray).toString(Charsets.UTF_8))
      }
    } else {
      println(caption + ": " + optional.get())
    }
  }
}

private fun verifyCertificateChain(certs: List<X509Certificate>) {
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
  if (Arrays.equals(
      googleRootCaPubKey,
      certs[certs.size - 1].publicKey.encoded
    )
  ) {
    println(
      "The root certificate is correct, so this attestation is trustworthy, as long as none of"
        + " the certificates in the chain have been revoked."
    )
  } else {
    println(
      "The root certificate is NOT correct. The attestation was probably generated by"
        + " software, not in secure hardware. This means that there is no guarantee that the"
        + " claims within the attestation are correct. If you're using a production-level"
        + " system, you should disregard any claims made within this attestation certificate"
        + " as there is no authority backing them up."
    )
  }
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
