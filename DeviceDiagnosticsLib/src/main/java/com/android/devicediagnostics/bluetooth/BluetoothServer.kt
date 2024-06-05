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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.android.devicediagnostics.Protos.BluetoothPacket
import com.android.devicediagnostics.Protos.PacketCommand
import com.android.devicediagnostics.Protos.TrustedDeviceInfo
import com.android.devicediagnostics.runInBackground
import java.util.UUID

val SERVICE_UUID: UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb")
private const val TAG = "BluetoothServer"
const val CHUNK = 4096

class BluetoothServer {
    private val adapter
        get() = BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null
    private var starter: ServerStarter? = null

    interface StartListener {
        fun onServerAvailable(connectionData: BluetoothConnectionData)

        fun onServerStarted()

        fun onServerError()
    }

    interface ReadListener {
        fun onBluetoothReadPacket(packet: BluetoothPacket)

        fun onBluetoothReadError(e: Exception)
    }

    fun start(trustedDeviceInfo: TrustedDeviceInfo, listener: StartListener) {
        if (starter != null) {
            starter!!.stop()
        }

        val server = this
        val callbacks =
            object : ServerStarter.Listener {
                override fun onServerAdvertiseStarted(connectionData: BluetoothConnectionData) {
                    listener.onServerAvailable(connectionData)
                }

                override fun onServerConnected(socket: BluetoothSocket) {
                    server.socket = socket
                    listener.onServerStarted()
                    starter = null
                }

                override fun onServerAdvertiseError() {
                    listener.onServerError()
                }
            }
        starter = ServerStarter(adapter, trustedDeviceInfo, callbacks)
        starter!!.start()
    }

    fun startRead(listener: ReadListener) {
        val handler = Handler(Looper.myLooper()!!)
        val localSocket = socket
        runInBackground {
            try {
                if (localSocket == null) {
                    throw Exception("Socket is closed")
                }
                val packet = readBluetoothPacket(localSocket)
                handler.post() { listener.onBluetoothReadPacket(packet) }
            } catch (e: Exception) {
                handler.post() { listener.onBluetoothReadError(e) }
                return@runInBackground
            }

            try {
                val ack = BluetoothPacket.newBuilder().setCommand(PacketCommand.COMMAND_ACK)
                writeBluetoothPacket(socket!!, ack)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending report acknowledgement: $e")
                return@runInBackground
            }

            // Wait for the client to hang up before resetting.
            try {
                readBluetoothPacket(localSocket)
            } finally {
                reset()
            }
        }
    }

    fun reset() {
        starter?.stop()
        starter = null
        socket?.close()
        socket = null
    }

    val bluetoothEnabled
        get() = adapter.isEnabled
}

class ServerStarter(
    private val adapter: BluetoothAdapter,
    private val trustedDeviceInfo: TrustedDeviceInfo,
    private val listener: ServerStarter.Listener
) {
    private val handler = Handler(Looper.myLooper()!!)
    private val advertiseSettings =
        AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTimeout(0)
            .build()
    private val advertiseData =
        AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(true)
            .build()
    private val advertiser = adapter.bluetoothLeAdvertiser
    private var advertiseCallback: AdvertiseCallback? = null
    private var active: Boolean = false

    // Access to this should be protected by synchronized(this).
    private var serverSocket: BluetoothServerSocket? = null

    interface Listener {
        fun onServerAdvertiseStarted(connectionData: BluetoothConnectionData)

        fun onServerConnected(socket: BluetoothSocket)

        fun onServerAdvertiseError()
    }

    fun start() {
        active = true

        advertiseCallback =
            object : AdvertiseCallback() {
                override fun onStartFailure(errorCode: Int) {
                    postServerError(Exception("Advertisement failed, error code $errorCode"))
                }

                override fun onStartSuccess(settings: AdvertiseSettings?) {
                    Log.d(TAG, "Advertisement started")
                }
            }
        advertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback!!)
        Log.d(TAG, "startAdvertisement: with advertiser $advertiser")

        serverSocket = adapter.listenUsingInsecureL2capChannel()
        Log.d(TAG, "Server started with psm ${serverSocket!!.psm}")

        val connectionData =
            BluetoothConnectionData(serverSocket!!.psm, trustedDeviceInfo.challenge.toByteArray())
        listener.onServerAdvertiseStarted(connectionData)

        val packet = BluetoothPacket.newBuilder().setTrustedDeviceInfo(trustedDeviceInfo)

        // Thread since this waits for connections and otherwise will block Ux
        runInBackground() {
            synchronized(this) {
                if (serverSocket == null) {
                    return@runInBackground
                }

                try {
                    val socket = serverSocket!!.accept()
                    stopAdvertising()

                    writeBluetoothPacket(socket, packet)
                    Log.d(TAG, "wrote start message")

                    postServerStarted(socket)
                } catch (e: Exception) {
                    postServerError(e)
                }
            }
        }
    }

    private fun postServerStarted(socket: BluetoothSocket) {
        handler.post() {
            if (active) {
                listener.onServerConnected(socket)
                active = false
            } else {
                socket.close()
            }
        }
    }

    private fun postServerError(e: Exception) {
        handler.post() {
            if (active) {
                listener.onServerAdvertiseError()
                stopAdvertising()
                active = false
            }
        }
    }

    private fun stopAdvertising() {
        synchronized(this) {
            if (advertiseCallback != null) {
                advertiser.stopAdvertising(advertiseCallback!!)
                advertiseCallback = null
            }
            if (serverSocket != null) {
                serverSocket?.close()
                serverSocket = null
            }
        }
    }

    fun stop() {
        stopAdvertising()
        active = false
    }
}
