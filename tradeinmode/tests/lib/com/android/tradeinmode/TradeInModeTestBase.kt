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

package com.android.tradeinmode

import com.android.compatibility.common.util.FeatureUtil
import com.android.ddmlib.TimeoutException
import com.android.tradefed.device.ITestDevice
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.android.tradefed.util.CommandResult
import com.android.tradefed.util.CommandStatus
import com.android.tradefed.util.RunUtil
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.time.toDuration
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.runner.RunWith

private val ONE_MINUTE_MS: Long = 1000 * 60
public val DEVICE_BOOT_TIMEOUT_MS: Long = 5 * ONE_MINUTE_MS
public val PAUSE_MS: Long = 6000

/** Tests trade-in mode functionality. */
@RunWith(DeviceJUnit4ClassRunner::class)
abstract class TradeInModeTestBase : BaseHostJUnit4Test() {
    protected lateinit var mDevice: ITestDevice

    @Before
    public fun setUp() {
        mDevice = getDevice()
    }

    public fun checkGetStatus(root: JSONObject, hasTelephony: Boolean) {
        assertTrue(root.has("serial"))
        assertNotEquals(root.getString("serial"), "")
        assertTrue(root.has("launch_level"))
        assertTrue(root.getInt("launch_level") > 0)

        assertTrue(root.has("product"))
        val product = root.getJSONObject("product")
        assertTrue(product.has("brand"))
        assertTrue(product.has("manufacturer"))
        assertTrue(product.has("model"))

        assertTrue(root.has("storage"))
        val storage = root.getJSONObject("storage")
        assertTrue(storage.has("capacity_bytes"))
        assertTrue(storage.getLong("capacity_bytes") > 0)

        assertTrue(root.has("battery"))
        val battery = root.getJSONObject("battery")
        assertTrue(battery.has("cycle_count"))
        assertTrue(battery.has("health"))
        assertTrue(battery.has("state"))
        assertTrue(battery.has("manufacturing_date"))
        assertTrue(battery.has("first_usage_date"))

        assertTrue(root.has("locks"))
        val locks = root.getJSONObject("locks")
        assertFalse(locks.getBoolean("factory_reset_protection"))

        if (hasTelephony) {
            assertTrue(root.has("imeis"))
            val imeis = root.getJSONArray("imeis")
            assertNotEquals(imeis.length(), 0)
            assertFalse(imeis.getString(0).isEmpty())
        }
    }

    protected fun enterTradeInMode() {
        // Initial reboot to wipe the device.
        val result = mDevice.executeShellV2Command("tradeinmode wait-until-ready testing wipe")
        if (!checkFlakyCommandResult(result)) {
            throw AssertionError("failed to wipe device for trade-in mode")
        }

        RunUtil.getDefault().sleep(PAUSE_MS)
        waitForDeviceNotInRecovery()

        // Second reboot to put adbd into trade-in mode.
        assertTrue(mDevice.startTradeInModeTesting(DEVICE_BOOT_TIMEOUT_MS.toInt()))
        RunUtil.getDefault().sleep(PAUSE_MS)
    }

    protected fun waitForDeviceNotInRecovery() {
        val start = TimeSource.Monotonic.markNow()
        val end = start + DEVICE_BOOT_TIMEOUT_MS.toDuration(DurationUnit.MILLISECONDS)
        while (true) {
            if (!inRecovery()) {
                return
            }
            val now = TimeSource.Monotonic.markNow()
            if (now > end) {
                throw TimeoutException("Timed out waiting to leave recovery")
            }
            RunUtil.getDefault().sleep(PAUSE_MS)
            mDevice.waitForDeviceOnline((end - now).inWholeMilliseconds)
        }
    }

    protected fun inRecovery(): Boolean {
        if (device.getProperty("init.svc.running") == "running") {
            return true
        }
        if (device.getProperty("ro.boot.mode") == "recovery") {
            return true
        }
        return false
    }

    abstract fun shouldTestTradeInMode(): Boolean

    protected fun isTradeInModeSupported(): Boolean {
        return !FeatureUtil.isTV(mDevice) &&
            !FeatureUtil.isWatch(mDevice) &&
            !FeatureUtil.isAutomotive(mDevice)
    }

    protected fun checkFlakyCommandResult(result: CommandResult): Boolean {
        if (CommandStatus.SUCCESS.equals(result.getStatus())) {
            return true
        }
        // If the device disconnects adb too fast, the exit code can be 255.
        if (CommandStatus.FAILED.equals(result.getStatus())) {
            if (result.getExitCode() == 255) {
                return true
            }
        }
        return false
    }

    protected fun restoreDevice() {
        val result = mDevice.executeShellCommand("tradeinmode wait-until-ready testing status")

        if (result.trim().equals("testing")) {
            mDevice.stopTradeInModeTesting()
        }
    }
}
