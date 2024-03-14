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

plugins {
  id("com.android.library")
}

android {
  namespace = "com.android.devicediagnostics"
  compileSdk = 34

  defaultConfig {
    minSdk = 34

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}

dependencies {

  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("com.google.android.material:material:1.11.0")
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation(files("../../../../out/soong/.intermediates/external/android-key-attestation/android-key-attestation/android_common/javac/android-key-attestation.jar"))
  implementation(files("../../../../out/soong/.intermediates/frameworks/base/packages/SettingsLib/SettingsLib/android_common/e18b8e8d84cb9f664aa09a397b08c165/javac/SettingsLib.jar"))
  implementation(files("../../../../out/soong/.intermediates/frameworks/base/packages/SettingsLib/LayoutPreference/SettingsLibLayoutPreference/android_common/e18b8e8d84cb9f664aa09a397b08c165/javac/SettingsLibLayoutPreference.jar"))
  implementation(files("../../../../out/soong/.intermediates/frameworks/base/packages/SettingsLib/CollapsingToolbarBaseActivity/SettingsLibCollapsingToolbarBaseActivity/android_common/e18b8e8d84cb9f664aa09a397b08c165/javac/SettingsLibCollapsingToolbarBaseActivity.jar"))
  implementation(files("../../../../out/soong/.intermediates/frameworks/base/packages/SettingsLib/UsageProgressBarPreference/SettingsLibUsageProgressBarPreference/android_common/e18b8e8d84cb9f664aa09a397b08c165/javac/SettingsLibUsageProgressBarPreference.jar"))
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
  implementation(kotlin("reflect"))
}