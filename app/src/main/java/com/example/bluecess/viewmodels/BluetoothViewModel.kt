package com.example.bluecess.viewmodels

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluecess.domain.models.BluetoothDevice as DomainBluetoothDevice
import com.example.bluecess.domain.models.DeviceType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BluetoothViewModel : ViewModel() {
    companion object {
        private const val TAG = "BluetoothViewModel"
        private const val SCAN_TIMEOUT_MS = 12000L // 12 seconds
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false
    private var applicationContext: Context? = null

    var isBluetoothEnabled by mutableStateOf(false)
        private set

    var isScanning by mutableStateOf(false)
        private set

    var discoveredDevices by mutableStateOf<List<DomainBluetoothDevice>>(emptyList())
        private set

    var connectedDevices by mutableStateOf<List<DomainBluetoothDevice>>(emptyList())
        private set

    var selectedTab by mutableStateOf(0)

    var hasRequiredPermissions by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val deviceCache = mutableMapOf<String, DomainBluetoothDevice>()

    init {
        Log.d(TAG, "BluetoothViewModel initialized")
    }

    fun initializeBluetooth(context: Context) {
        try {
            applicationContext = context.applicationContext
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter

            if (bluetoothAdapter == null) {
                errorMessage = "Bluetooth is not supported on this device"
                isBluetoothEnabled = false
                Log.w(TAG, "Bluetooth not supported")
            } else {
                updateBluetoothState()
                registerBluetoothStateReceiver(context)
                Log.d(TAG, "Bluetooth initialized. Enabled: $isBluetoothEnabled")
            }
        } catch (e: Exception) {
            errorMessage = "Failed to initialize Bluetooth: ${e.message}"
            Log.e(TAG, "Error initializing Bluetooth", e)
        }
    }

    private fun registerBluetoothStateReceiver(context: Context) {
        try {
            val stateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothAdapter.ACTION_STATE_CHANGED -> {
                            val state = intent.getIntExtra(
                                BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.ERROR
                            )
                            when (state) {
                                BluetoothAdapter.STATE_OFF -> {
                                    isBluetoothEnabled = false
                                    if (isScanning) {
                                        cleanupScan(context)
                                    }
                                }
                                BluetoothAdapter.STATE_ON -> {
                                    isBluetoothEnabled = true
                                }
                            }
                        }
                    }
                }
            }
            
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.registerReceiver(stateReceiver, filter)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register state receiver", e)
        }
    }

    fun updateBluetoothState() {
        bluetoothAdapter?.let {
            isBluetoothEnabled = it.isEnabled
            Log.d(TAG, "Bluetooth state: $isBluetoothEnabled")
        }
    }

    fun checkPermissions(context: Context): Boolean {
        val permissionsToCheck = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToCheck.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            permissionsToCheck.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }

        val allGranted = permissionsToCheck.all { permission ->
            ActivityCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }

        hasRequiredPermissions = allGranted
        return allGranted
    }

    fun updatePermissionsState(context: Context) {
        hasRequiredPermissions = checkPermissions(context)
    }

    @SuppressLint("MissingPermission")
    fun startScanning(context: Context) {
        Log.d(TAG, "startScanning called. Current state - isScanning: $isScanning")

        if (isScanning) {
            Log.i(TAG, "Already scanning, stopping first")
            stopScanning(context)
            return
        }

        if (bluetoothAdapter == null) {
            errorMessage = "Bluetooth adapter not available"
            Log.e(TAG, "Bluetooth adapter is null")
            return
        }

        if (!isBluetoothEnabled) {
            errorMessage = "Please enable Bluetooth first"
            Log.w(TAG, "Bluetooth is disabled")
            return
        }

        if (!checkPermissions(context)) {
            errorMessage = "Please grant all required permissions"
            Log.w(TAG, "Missing required permissions")
            return
        }

        viewModelScope.launch {
            try {
                // Cancel any ongoing discovery
                if (bluetoothAdapter?.isDiscovering == true) {
                    Log.d(TAG, "Canceling existing discovery")
                    bluetoothAdapter?.cancelDiscovery()
                    delay(500)
                }

                // Clear previous results
                deviceCache.clear()
                discoveredDevices = emptyList()
                errorMessage = null

                // Register receiver for discovery
                registerDiscoveryReceiver(context)

                // Start discovery
                Log.d(TAG, "Starting Bluetooth discovery")
                val started = bluetoothAdapter?.startDiscovery() ?: false

                if (started) {
                    isScanning = true
                    Log.i(TAG, "Bluetooth discovery started successfully")

                    // Set timeout
                    launch {
                        delay(SCAN_TIMEOUT_MS)
                        if (isScanning) {
                            Log.d(TAG, "Scan timeout reached")
                            stopScanning(context)
                        }
                    }
                } else {
                    errorMessage = "Failed to start discovery. Please try again."
                    Log.e(TAG, "startDiscovery returned false")
                    unregisterDiscoveryReceiver(context)
                }

            } catch (e: SecurityException) {
                errorMessage = "Bluetooth permission denied"
                Log.e(TAG, "SecurityException during discovery", e)
                unregisterDiscoveryReceiver(context)
            } catch (e: Exception) {
                errorMessage = "Failed to start scanning: ${e.message}"
                Log.e(TAG, "Exception during discovery", e)
                unregisterDiscoveryReceiver(context)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning(context: Context) {
        Log.d(TAG, "stopScanning called")

        if (!isScanning) {
            Log.d(TAG, "Not scanning, nothing to stop")
            return
        }

        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                Log.d(TAG, "Canceling discovery")
                bluetoothAdapter?.cancelDiscovery()
            }

            cleanupScan(context)

            if (discoveredDevices.isEmpty()) {
                discoveredDevices = getBondedDevices()
                Log.d(TAG, "No devices found, showing bonded devices: ${discoveredDevices.size}")
            }

            Log.i(TAG, "Scan stopped. Total devices: ${discoveredDevices.size}")

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException stopping scan", e)
            errorMessage = "Permission error stopping scan"
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
            errorMessage = "Error stopping scan"
        }
    }

    private fun cleanupScan(context: Context) {
        isScanning = false
        unregisterDiscoveryReceiver(context)
    }

    private fun registerDiscoveryReceiver(context: Context) {
        unregisterDiscoveryReceiver(context)

        bluetoothReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = 
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val rssi = intent.getShortExtra(
                            BluetoothDevice.EXTRA_RSSI,
                            Short.MIN_VALUE
                        ).toInt()

                        device?.let { btDevice ->
                            if (shouldIncludeDevice(btDevice)) {
                                addDiscoveredDevice(btDevice, rssi)
                            }
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        Log.d(TAG, "Discovery started broadcast received")
                        isScanning = true
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "Discovery finished broadcast received")
                        viewModelScope.launch {
                            delay(500)
                            if (isScanning) {
                                stopScanning(context)
                            }
                        }
                    }
                }
            }
        }

        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }

            context.registerReceiver(bluetoothReceiver, filter)
            isReceiverRegistered = true
            Log.d(TAG, "Discovery receiver registered")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to register discovery receiver", e)
            errorMessage = "Failed to setup scanner"
        }
    }

    private fun unregisterDiscoveryReceiver(context: Context) {
        try {
            if (isReceiverRegistered && bluetoothReceiver != null) {
                context.unregisterReceiver(bluetoothReceiver)
                isReceiverRegistered = false
                bluetoothReceiver = null
                Log.d(TAG, "Discovery receiver unregistered")
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun shouldIncludeDevice(device: BluetoothDevice): Boolean {
        if (device.name.isNullOrEmpty()) return false
        if (device.address.isNullOrEmpty() || device.address == "00:00:00:00:00:00") return false

        // Include all classic and dual-mode devices, skip BLE-only
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (device.type == BluetoothDevice.DEVICE_TYPE_LE) {
                return false
            }
        }

        return true
    }

    @SuppressLint("MissingPermission")
    private fun addDiscoveredDevice(device: BluetoothDevice, rssi: Int) {
        val deviceKey = device.address

        if (!deviceCache.containsKey(deviceKey)) {
            val domainDevice = convertToDomainBluetoothDevice(device, rssi)
            deviceCache[deviceKey] = domainDevice

            discoveredDevices = deviceCache.values
                .sortedByDescending { it.rssi ?: -100 }

            Log.d(TAG, "Device discovered: ${device.name} (${device.address}) RSSI: $rssi")
        }
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<DomainBluetoothDevice> {
        return try {
            if (!hasRequiredPermissions) {
                Log.w(TAG, "Missing permissions for bonded devices")
                return emptyList()
            }

            bluetoothAdapter?.bondedDevices?.mapNotNull { device ->
                if (shouldIncludeDevice(device)) {
                    convertToDomainBluetoothDevice(device)
                } else {
                    null
                }
            }?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting bonded devices", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bonded devices", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    private fun convertToDomainBluetoothDevice(
        device: BluetoothDevice,
        rssi: Int = 0
    ): DomainBluetoothDevice {
        return DomainBluetoothDevice(
            name = device.name ?: "Unknown Device",
            address = device.address,
            isConnected = false,
            isPaired = device.bondState == BluetoothDevice.BOND_BONDED,
            deviceType = detectDeviceType(device),
            rssi = rssi,
            lastSeen = System.currentTimeMillis()
        )
    }

    @SuppressLint("MissingPermission")
    private fun detectDeviceType(device: BluetoothDevice): DeviceType {
        val deviceName = device.name?.lowercase() ?: ""

        return when {
            deviceName.contains("headphone") || deviceName.contains("airpod") ||
                    deviceName.contains("earbud") || deviceName.contains("bud") -> 
                DeviceType.HEADPHONES

            deviceName.contains("speaker") || deviceName.contains("sound") ||
                    deviceName.contains("boombox") || deviceName.contains("dock") -> 
                DeviceType.SPEAKER

            deviceName.contains("car") || deviceName.contains("auto") ||
                    deviceName.contains("vehicle") -> 
                DeviceType.CAR

            deviceName.contains("phone") || deviceName.contains("mobile") -> 
                DeviceType.PHONE

            else -> DeviceType.UNKNOWN
        }
    }

    fun connectToDevice(device: DomainBluetoothDevice) {
        viewModelScope.launch {
            try {
                val updatedDevice = device.copy(isConnected = true)
                connectedDevices = connectedDevices + updatedDevice
                discoveredDevices = discoveredDevices.map {
                    if (it.address == device.address) updatedDevice else it
                }
                Log.i(TAG, "Connected to ${device.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to device", e)
                errorMessage = "Failed to connect: ${e.message}"
            }
        }
    }

    fun disconnectDevice(device: DomainBluetoothDevice) {
        viewModelScope.launch {
            val updatedDevice = device.copy(isConnected = false)
            connectedDevices = connectedDevices.filterNot { it.address == device.address }
            discoveredDevices = discoveredDevices.map {
                if (it.address == device.address) updatedDevice else it
            }
            Log.i(TAG, "Disconnected from ${device.name}")
        }
    }

    fun clearError() {
        errorMessage = null
    }

    override fun onCleared() {
        super.onCleared()
        applicationContext?.let { context ->
            if (isScanning) {
                stopScanning(context)
            }
        }
        Log.d(TAG, "ViewModel cleared")
    }
}