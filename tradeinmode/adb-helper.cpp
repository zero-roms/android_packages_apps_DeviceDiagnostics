//
// Copyright (C) 2024 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include <paths.h>

#include <iostream>
#include <regex>

#include <android-base/logging.h>
#include <gflags/gflags.h>

DEFINE_string(challenge, "", "Challenge string for getstatus");

static bool Exec(const std::string& cmd) {
    // No need to fork, this binary is just a wrapper around these commands.
    execl(_PATH_BSHELL, _PATH_BSHELL, "-c", cmd.c_str(), nullptr);
    PLOG(ERROR) << "execl failed";
    return false;
}

static bool DoGetStatus(int argc, char**) {
    if (argc > 2) {
        std::cerr << "Unexpected argument." << std::endl;
        return false;
    }

    std::string cmd =
            "content query --uri "
            "content://com.android.devicediagnostics/.GetStatusContentProvider";
    if (!FLAGS_challenge.empty()) {
        std::regex allowed_pattern("[A-Za-z0-9]+");
        if (!std::regex_match(FLAGS_challenge, allowed_pattern)) {
            std::cerr << "Invalid challenge." << std::endl;
            return false;
        }
        cmd += " --where " + FLAGS_challenge;
    }
    return Exec(cmd);
}

static bool DoEnterEvalMode(int argc, char**) {
    if (argc > 2) {
        std::cerr << "Unexpected argument." << std::endl;
        return false;
    }

    std::string cmd =
            "/system/bin/am start -n "
            "com.android.devicediagnostics/.EnterTradeInMode";
    return Exec(cmd);
}

int main(int argc, char** argv) {
    gflags::ParseCommandLineFlags(&argc, &argv, true);

    if (argc < 2) {
        std::cerr << "Expected command." << std::endl;
        return 1;
    }

    bool ok = false;
    if (strcmp(argv[1], "getstatus") == 0) {
        ok = DoGetStatus(argc, argv);
    } else if (strcmp(argv[1], "enter") == 0) {
        ok = DoEnterEvalMode(argc, argv);
    } else {
        std::cerr << "Unknown command." << std::endl;
    }
    return ok ? 0 : 1;
}
