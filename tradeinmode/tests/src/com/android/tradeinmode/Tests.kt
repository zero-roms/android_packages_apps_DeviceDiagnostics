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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class UserdebugTradeInModeTests : TradeInModeTestBase() {
    @Test
    public fun testDeveloperGetStatus() {
        if (!shouldTestTradeInMode()) {
            return
        }

        val result = mDevice.executeShellV2Command("tradeinmode wait-until-ready getstatus")
        assertEquals(result.getExitCode(), 0)

        val root = JSONObject(result.getStdout())
        checkGetStatus(root, FeatureUtil.hasTelephony(mDevice))
    }

    override fun shouldTestTradeInMode(): Boolean {
        return isTradeInModeSupported()
    }
}
