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
import com.android.tradefed.log.LogUtil.CLog
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.android.tradefed.util.CommandResult
import com.android.tradefed.util.CommandStatus
import com.android.tradefed.util.RunUtil
import com.google.common.io.Files
import java.io.File
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.time.toDuration
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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

    @After
    public fun tearDown() {
        restoreDevice()
    }

    @Test
    public fun checkGetStatusFlow() {
        if (!shouldTestTradeInMode()) {
            return
        }
        if (!hasSetupWizard()) {
            CLog.e("Skipping test, device does not have SUW enabled")
            return
        }

        val hasTelephony = FeatureUtil.hasTelephony(mDevice)

        enterTradeInMode()

        // Normal shell commands should not work.
        var result = mDevice.executeShellV2Command("ls -l /")
        assertNotEquals(result.getExitCode(), 0)

        result = mDevice.executeShellV2Command("tradeinmode wait-until-ready getstatus")
        assertEquals(result.getExitCode(), 0)

        val root = JSONObject(result.getStdout())
        checkGetStatus(root, hasTelephony)
    }

    @Test
    public fun checkEvaluateFlow() {
        if (!shouldTestTradeInMode()) {
            return
        }
        if (!hasSetupWizard()) {
            CLog.e("Skipping test, device does not have SUW enabled")
            return
        }

        enterTradeInMode()

        // Normal shell commands should not work.
        var result = mDevice.executeShellV2Command("stat /")
        assertNotEquals(result.getExitCode(), 0)

        result = mDevice.executeShellV2Command("tradeinmode wait-until-ready evaluate")
        RunUtil.getDefault().sleep(PAUSE_MS)
        mDevice.waitForDeviceOnline()

        // Normal shell commands should work now.
        result = mDevice.executeShellV2Command("stat /")
        assertEquals(result.getExitCode(), 0)

        // Wait for the "settings" service to be online.
        waitForCommandService("settings")

        // Setup should be complete.
        assertEquals(getSettingInt("global", "device_provisioned", 0), 1)
        assertEquals(getSettingInt("secure", "user_setup_complete", 0), 1)

        // Create a temporary file and validate that it exists.
        val tempData = "test string"
        val tempFile = File.createTempFile("tradeinmode", ".placeholder")
        tempFile.deleteOnExit()
        Files.write(tempData.toByteArray(Charsets.UTF_8), tempFile)
        val remotePath = "/data/local/tmp/" + tempFile.getName()
        mDevice.pushFile(tempFile, remotePath)
        assertEquals(mDevice.executeShellCommand("cat " + remotePath), tempData)

        // Reboot. A wipe should have been performed and the file should be gone.
        mDevice.rebootUntilOnline()
        result = mDevice.executeShellV2Command("cat " + remotePath)
        assertNotEquals(result.getExitCode(), 0)

        // We should be back in SUW.
        assertEquals(getSettingInt("global", "device_provisioned", 0), 0)
        assertEquals(getSettingInt("secure", "user_setup_complete", 0), 0)
    }

    // This checks that tradeinmode evaluate works in userdebug, without
    // specifically enabling TIM. We don't need an explicit userdebug
    // check since our tests are never run on non-root devices, so the
    // same test will work on user builds with ro.debuggable=1.
    @Test
    public fun checkUserdebugEvaluateFlow() {
        if (!shouldTestTradeInMode()) {
            return
        }
        if (!hasSetupWizard()) {
            CLog.e("Skipping test, device does not have SUW enabled")
            return
        }

        performWipe()

        // Normal shell commands should work.
        var result = mDevice.executeShellV2Command("stat /")
        assertEquals(result.getExitCode(), 0)

        waitForCommandService("settings")

        // SUW should be active.
        assertEquals(getSettingInt("global", "device_provisioned", 0), 0)
        assertEquals(getSettingInt("secure", "user_setup_complete", 0), 0)

        result = mDevice.executeShellV2Command("tradeinmode wait-until-ready evaluate")
        RunUtil.getDefault().sleep(PAUSE_MS)
        mDevice.waitForDeviceOnline()

        // Wait for the "settings" service to be online.
        waitForCommandService("settings")

        // Setup should be complete.
        assertEquals(getSettingInt("global", "device_provisioned", 0), 1)
        assertEquals(getSettingInt("secure", "user_setup_complete", 0), 1)

        // Create a temporary file and validate that it exists.
        val tempData = "test string"
        val tempFile = File.createTempFile("tradeinmode", ".placeholder")
        tempFile.deleteOnExit()
        Files.write(tempData.toByteArray(Charsets.UTF_8), tempFile)
        val remotePath = "/data/local/tmp/" + tempFile.getName()
        mDevice.pushFile(tempFile, remotePath)
        assertEquals(mDevice.executeShellCommand("cat " + remotePath), tempData)

        // Reboot. A wipe should have been performed and the file should be gone.
        mDevice.rebootUntilOnline()
        result = mDevice.executeShellV2Command("cat " + remotePath)
        assertNotEquals(result.getExitCode(), 0)

        // We should be back in SUW.
        assertEquals(getSettingInt("global", "device_provisioned", 0), 0)
        assertEquals(getSettingInt("secure", "user_setup_complete", 0), 0)
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
        assertTrue(product.optString("brand") != "")
        assertTrue(product.optString("manufacturer") != "")
        assertTrue(product.optString("model") != "")

        assertTrue(root.has("storage"))
        val storage = root.getJSONObject("storage")
        assertTrue(storage.has("capacity_bytes"))
        assertTrue(storage.optLong("capacity_bytes") > 0)
        if (storage.has("useful_lifetime_remaining")) {
            assertTrue(storage.optInt("useful_lifetime_remaining") >= 0)
        }

        assertTrue(root.has("battery"))
        val battery = root.getJSONObject("battery")
        assertTrue(battery.optInt("cycle_count") >= 0)
        assertTrue(battery.optInt("health") >= 0)
        assertTrue(battery.has("state"))
        if (battery.has("manufacturing_date")) {
            assertTrue(battery.optInt("manufacturing_date") > 0)
        }
        if (battery.has("first_usage_date")) {
            assertTrue(battery.optInt("first_usage_date") > 0)
        }
        assertTrue(battery.optInt("state") > 0)
        if (battery.has("part_status")) {
            assertTrue(battery.optString("part_status") != "")
        }
        if (battery.has("serial")) {
            assertTrue(battery.optString("serial") != "")
        }

        if (root.has("sensors")) {
            val sensors = root.getJSONObject("sensors")
            if (sensors.has("moisture_intrusion")) {
                assertTrue(sensors.optBoolean("moisture_intrusion"))
            }
        }

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
        performWipe()

        // Second reboot to put adbd into trade-in mode. We set a property to
        // detect whether the reboot already happened or not. This is purely
        // for the test harness and has nothing to do with the actual trade-in
        // mode functionality.
        mDevice.setProperty("debug.tradeinmode", "1")
        assertTrue(mDevice.startTradeInModeTesting(DEVICE_BOOT_TIMEOUT_MS.toInt()))
        waitForDevice({ !inRecovery() && device.getProperty("debug.tradeinmode") != "1" })
    }

    protected fun performWipe() {
        // Initial reboot to wipe the device.
        val result = mDevice.executeShellV2Command("tradeinmode wait-until-ready testing wipe")
        if (!checkFlakyCommandResult(result)) {
            throw AssertionError("failed to wipe device for trade-in mode")
        }

        RunUtil.getDefault().sleep(PAUSE_MS)
        waitForDevice({ !inRecovery() })
    }

    protected fun hasSetupWizard(): Boolean {
        val suwMode = device.getProperty("ro.setupwizard.mode")
        if (suwMode == "EMULATOR" || suwMode == "DISABLED") {
            return false
        }
        return true
    }

    protected fun waitForDevice(cond: () -> Boolean) {
        val start = TimeSource.Monotonic.markNow()
        val end = start + DEVICE_BOOT_TIMEOUT_MS.toDuration(DurationUnit.MILLISECONDS)

        // Wait at least once before checking |cond| since virtual devices can
        // power off very quickly.
        mDevice.waitForDeviceOnline(DEVICE_BOOT_TIMEOUT_MS)

        while (true) {
            if (cond()) {
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

    protected fun restoreDevice() {
        val result = mDevice.executeShellCommand("tradeinmode wait-until-ready testing status")

        if (result.trim().equals("testing")) {
            mDevice.stopTradeInModeTesting()
        }
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

    private fun waitForCommandService(cmd: String) {
        val services = mDevice.executeShellCommand("cmd -l")
        for (i in 1..10) {
            if (services.contains(" $cmd")) {
                break
            }
            RunUtil.getDefault().sleep(PAUSE_MS)
        }
    }

    private fun getSettingInt(namespace: String, key: String, defaultValue: Int): Int {
        try {
            val result = mDevice.getSetting(namespace, key)
            return result.toInt()
        } catch (e: NumberFormatException) {
            return defaultValue
        }
    }
}
