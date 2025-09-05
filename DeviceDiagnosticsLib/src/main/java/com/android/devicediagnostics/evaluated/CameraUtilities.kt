/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.devicediagnostics.evaluated

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.android.devicediagnostics.Protos.Camera

fun getCameraInfo(context: Context): ArrayList<Camera> {
    val cameras = ArrayList<Camera>()

    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
    if (cm == null || cm.cameraIdList.isEmpty()) {
        return cameras
    }

    for (id in cm.cameraIdList) {
        val attrs = cm.getCameraCharacteristics(id)
        val camera = Camera.newBuilder()

        val lensFacing = attrs.get(CameraCharacteristics.LENS_FACING)
        if (lensFacing != null) {
            camera.setLensFacing(lensFacing)
        }

        val caps = attrs.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        if (
            caps != null &&
                caps.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
                )
        ) {
            camera.setNumPhysical(attrs.physicalCameraIds.size)
        } else {
            camera.setNumPhysical(1)
        }
        cameras.add(camera.build())
    }
    return cameras
}
