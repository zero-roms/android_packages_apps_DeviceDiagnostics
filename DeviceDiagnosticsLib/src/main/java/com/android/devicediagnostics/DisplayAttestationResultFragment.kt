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

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.attestation.AuthorizationList
import com.google.android.attestation.ParsedAttestationRecord
import java.util.Optional

class DisplayAttestationResultFragment(
    private val record: ParsedAttestationRecord,
    private val result: AttestationResult
) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.attestation_details, rootKey)

        // By default, show a failure code for IMEI/Serial. It will get overridden if it's actually
        // present.
        if (
            result == AttestationResult.VERIFIED || result == AttestationResult.SKIPPED_VERIFICATION
        ) {
            if (result == AttestationResult.VERIFIED) {
                show("attestation_verified", activity!!.resources.getString(R.string.yes))
            } else {
                hide("attestation_verified")
            }
            show("attestation_imei", activity!!.resources.getString(R.string.unknown))
            show("attestation_serial", activity!!.resources.getString(R.string.unknown))
        } else {
            show("attestation_verified", activity!!.resources.getString(R.string.no))
            show("attestation_imei", activity!!.resources.getString(R.string.attestation_failed))
            show("attestation_serial", activity!!.resources.getString(R.string.attestation_failed))
        }

        if (isDeviceLocked()) {
            show("attestation_device_locked", activity!!.resources.getString(R.string.yes))
        } else {
            show("attestation_device_locked", activity!!.resources.getString(R.string.no))
        }

        show("attestation_verified_boot_state", getVerifiedBootState())
        show("attestation_security_level", record.attestationSecurityLevel.name)
        show("attestation_keymaster_version", record.keymasterVersion.toString())
        show("attestation_keymaster_security_level", record.keymasterSecurityLevel.name)
        processAuthorizationList(record.softwareEnforced)
        processAuthorizationList(record.teeEnforced)
    }

    private fun processAuthorizationList(list: AuthorizationList) {
        showInt("attestation_os_version", list.osVersion)
        show("attestation_brand", list.attestationIdBrand)
        show("attestation_device", list.attestationIdDevice)
        show("attestation_product", list.attestationIdProduct)
        show("attestation_serial", list.attestationIdSerial)
        show("attestation_imei", list.attestationIdImei)
        show("attestation_second_imei", list.attestationIdSecondImei)
        show("attestation_meid", list.attestationIdMeid)
        show("attestation_manufacturer", list.attestationIdManufacturer)
        show("attestation_model", list.attestationIdModel)
        showInt("attestation_vendor_patch_level", list.vendorPatchLevel)
        showInt("attestation_boot_patch_level", list.bootPatchLevel)
    }

    private fun isDeviceLocked(): Boolean {
        val root = record.teeEnforced.rootOfTrust
        return root.isPresent && root.get().deviceLocked
    }

    private fun getVerifiedBootState(): String {
        val root = record.teeEnforced.rootOfTrust
        if (!root.isPresent) return activity!!.resources.getString(R.string.unknown)
        return root.get().verifiedBootState.name
    }

    private fun showInt(key: String, value: Optional<Int>?) {
        if (value != null && value.isPresent) return show(key, value.get().toString())
    }

    private fun show(key: String, value: Optional<ByteArray>) {
        if (value.isPresent) return show(key, value.get().toString(Charsets.UTF_8))
    }

    private fun show(key: String, text: String) {
        val pref = findPreference<Preference>(key)!!
        pref.summary = text
        pref.isVisible = true
    }

    private fun hide(key: String) {
        findPreference<Preference>(key)!!.isVisible = false
    }
}
