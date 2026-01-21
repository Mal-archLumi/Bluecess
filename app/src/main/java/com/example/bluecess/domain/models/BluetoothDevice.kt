package com.example.bluecess.domain.models

data class BluetoothDevice(
    val name: String,
    val address: String,
    val isConnected: Boolean = false,
    val isPaired: Boolean = false,
    val deviceType: DeviceType = DeviceType.UNKNOWN
)

enum class DeviceType {
    HEADPHONES,
    SPEAKER,
    PHONE,
    CAR,
    UNKNOWN
}