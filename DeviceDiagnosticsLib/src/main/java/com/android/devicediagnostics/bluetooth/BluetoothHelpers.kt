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
package com.android.devicediagnostics.bluetooth

import android.bluetooth.BluetoothSocket
import android.util.Base64
import com.android.devicediagnostics.Protos.BluetoothPacket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import org.json.JSONObject

const val BT_READ_SIZE = 4096
const val BT_MAX_PACKET_SIZE = 1024 * 1024
const val BT_VERSION = 1

private const val QRCODE_VERSION = 4

private fun readFully(socket: BluetoothSocket, n: Int): ByteArray {
    val b = ByteArray(n)
    val stream = socket.inputStream

    var offset = 0
    while (offset < n) {
        val maxRead = min(n - offset, BT_READ_SIZE)
        offset += stream.read(b, offset, maxRead)
    }
    return b
}

fun readBluetoothPacket(socket: BluetoothSocket): BluetoothPacket {
    val payloadSizeBytes = readFully(socket, 4)
    val payloadSize = ByteBuffer.wrap(payloadSizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()
    if (payloadSize == 0) {
        throw Exception("Invalid packet received")
    }
    if (payloadSize > BT_MAX_PACKET_SIZE) {
        throw Exception("Maximum packet size exceeded: $payloadSize")
    }
    val payloadBytes = readFully(socket, payloadSize)
    return BluetoothPacket.parseFrom(payloadBytes)
}

fun writeBluetoothPacket(socket: BluetoothSocket, packetBuilder: BluetoothPacket.Builder) {
    val packet = packetBuilder.setVersion(BT_VERSION).build()
    val bytes = packet.toByteArray()
    val header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.size).array()
    socket.outputStream.write(header)
    socket.outputStream.write(bytes)
}

class BluetoothConnectionData(val psm: Int = 0, val challenge: ByteArray) {
    companion object Helpers {
        private const val JSON_PSM_KEY = "PSM"
        private const val JSON_VERSION_KEY = "version"
        private const val JSON_CHALLENGE_KEY = "challenge"

        fun fromJson(json: String): BluetoothConnectionData {
            val obj = JSONObject(json)
            var version: Int = 0
            if (obj.has(JSON_VERSION_KEY)) {
                version = obj.getInt(JSON_VERSION_KEY)
            }
            if (version < QRCODE_VERSION) {
                throw Exception("Unsupported version")
            }
            val psm = obj.getInt(JSON_PSM_KEY)
            val challengeBase64 = obj.getString(JSON_CHALLENGE_KEY)
            val challenge = Base64.decode(challengeBase64, Base64.URL_SAFE)
            return BluetoothConnectionData(psm, challenge)
        }
    }

    override fun toString(): String {
        val challengeBase64 = Base64.encodeToString(challenge, Base64.URL_SAFE)
        return JSONObject()
            .put(JSON_PSM_KEY, psm)
            .put(JSON_VERSION_KEY, QRCODE_VERSION)
            .put(JSON_CHALLENGE_KEY, challengeBase64)
            .toString()
    }
}
