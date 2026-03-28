package com.getupandgetlit.dingshihai.domain.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

data class BluetoothRouteState(
    val available: Boolean,
    val a2dpConnected: Boolean,
    val headsetConnected: Boolean,
    val outputDevicePresent: Boolean,
)

class BluetoothChecker(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    fun isBluetoothAudioAvailable(): Boolean {
        return currentRouteState().available
    }

    fun currentRouteState(): BluetoothRouteState {
        val adapter = bluetoothAdapter ?: return BluetoothRouteState(
            available = false,
            a2dpConnected = false,
            headsetConnected = false,
            outputDevicePresent = false,
        )
        if (!adapter.isEnabled) {
            return BluetoothRouteState(
                available = false,
                a2dpConnected = false,
                headsetConnected = false,
                outputDevicePresent = false,
            )
        }
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasBluetoothDevice = devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        val a2dpConnected = adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
        val headsetConnected = adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        return BluetoothRouteState(
            available = hasBluetoothDevice && (a2dpConnected || headsetConnected),
            a2dpConnected = a2dpConnected,
            headsetConnected = headsetConnected,
            outputDevicePresent = hasBluetoothDevice,
        )
    }

    @Suppress("DEPRECATION")
    fun preparePlaybackRoute(): BluetoothRouteState {
        val state = currentRouteState()
        if (!state.available) {
            return state
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
        if (state.a2dpConnected) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.isBluetoothA2dpOn = true
        } else if (state.headsetConnected) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        }
        return state
    }
}
