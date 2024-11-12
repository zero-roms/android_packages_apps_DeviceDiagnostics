#!/usr/bin/env python
#
# Copyright (C) 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import json
import os
import time
import unittest

from vts.testcases.kernel.utils import adb
from vts.testcases.vndk import utils

class TradeInModeTest(unittest.TestCase):

    def setUp(self):
        serial_number = os.environ.get("ANDROID_SERIAL")
        self.assertTrue(serial_number, "$ANDROID_SERIAL is empty.")
        self.dut = utils.AndroidDevice(serial_number)
        self.adb = adb.ADB(serial_number)
        self.buildType = self.dut.Execute("getprop ro.build.type")[0].strip()

    def userBuild(self):
        if (self.buildType == "user"):
          return True
        self.assertTrue(self.buildType in ["userdebug", "eng"])
        return False

    def reboot(self):
        self.adb.Execute(["reboot"])
        try:
          self.adb.Execute(["wait-for-device"], timeout=900)
        except self.adb.AdbError as e:
          self.fail("Exception thrown waiting for device:" + e.msg())
        for i in range(300):
          out, err, return_code = self.dut.Execute("getprop sys.boot_completed")
          if "1" in out:
            return
          time.sleep(1)
        self.fail("Did not boot completely")

    def testEnterTradeInMode(self):
        if (self.userBuild()):
          return

        out, err, return_code = self.dut.Execute("su root setprop persist.adb.tradeinmode 1")
        self.assertEqual(return_code, 0, "Failed to set tradeinmode property")
        out, err, return_code = self.dut.Execute("su root setprop ctl.restart adbd")
        self.assertEqual(return_code, 255, "Failed to restart adbd")

        for i in range(30):
          out, err, return_code = self.dut.Execute("tradeinmode getstatus")
          if return_code == 0:
            break
          time.sleep(1)

        self.assertEquals(return_code, 0, "Failed to getstatus")
        j = json.loads(out[out.find('{'):])
        self.assertTrue("serial" in j)

        out, err, return_code = self.dut.Execute("touch /data/local/tmp/tim")
        self.assertEqual(return_code, 1, "Used shell in TIM foyer")

        out, err, return_code = self.dut.Execute("tradeinmode evaluate")
        self.assertEqual(return_code, 255, "Failed to enter evaluation mode")

        for i in range(30):
          out, err, return_code = self.dut.Execute("touch /data/local/tmp/tim")
          if return_code == 0:
            break
          time.sleep(1)
        self.assertEqual(return_code, 0, "Failed to use shell")
        out, err, return_code = self.dut.Execute("ls /data/local/tmp")
        self.assertEqual(return_code, 0, "Failed to ls tmp dir")
        self.assertTrue("tim" in out, "Failed to create tim file")

        self.reboot()

        out, err, return_code = self.dut.Execute("su root am start -a com.android.setupwizard.EXIT")
        self.assertEqual(return_code, 0, "Failed to skip setup wizard")
        out, err, return_code = self.dut.Execute("ls /data/local/tmp")
        self.assertEqual(return_code, 0, "Failed to ls tmp dir")
        self.assertFalse("tim" in out, "Failed to wipe device")

if __name__ == "__main__":
    # Setting verbosity is required to generate output that the TradeFed test
    # runner can parse.
    unittest.main(verbosity=3)
