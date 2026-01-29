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
    private var connectionReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false
    private var isConnectionReceiverRegistered = false
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
                registerConnectionStateReceiver(context)
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

    private fun registerConnectionStateReceiver(context: Context) {
        try {
            connectionReceiver = object : BroadcastReceiver() {
                @SuppressLint("MissingPermission")
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED -> {
                            val device: BluetoothDevice? = 
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            device?.let {
                                Log.i(TAG, "Device connected: ${it.name} (${it.address})")
                                updateDeviceConnectionState(it.address, true)
                            }
                        }
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            val device: BluetoothDevice? = 
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            device?.let {
                                Log.i(TAG, "Device disconnected: ${it.name} (${it.address})")
                                updateDeviceConnectionState(it.address, false)
                            }
                        }
                        BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                            val device: BluetoothDevice? = 
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            val bondState = intent.getIntExtra(
                                BluetoothDevice.EXTRA_BOND_STATE,
                                BluetoothDevice.BOND_NONE
                            )
                            device?.let {
                                when (bondState) {
                                    BluetoothDevice.BOND_BONDED -> {
                                        Log.i(TAG, "Device paired: ${it.name}")
                                        updateDevicePairedState(it.address, true)
                                    }
                                    BluetoothDevice.BOND_NONE -> {
                                        Log.i(TAG, "Device unpaired: ${it.name}")
                                        updateDevicePairedState(it.address, false)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }

            context.registerReceiver(connectionReceiver, filter)
            isConnectionReceiverRegistered = true
            Log.d(TAG, "Connection state receiver registered")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to register connection receiver", e)
        }
    }

    private fun updateDeviceConnectionState(address: String, isConnected: Boolean) {
        discoveredDevices = discoveredDevices.map {
            if (it.address == address) {
                it.copy(isConnected = isConnected)
            } else {
                it
            }
        }

        if (isConnected) {
            val device = discoveredDevices.find { it.address == address }
            device?.let {
                if (!connectedDevices.any { d -> d.address == address }) {
                    connectedDevices = connectedDevices + it.copy(isConnected = true)
                }
            }
        } else {
            connectedDevices = connectedDevices.filterNot { it.address == address }
        }
    }

    private fun updateDevicePairedState(address: String, isPaired: Boolean) {
        discoveredDevices = discoveredDevices.map {
            if (it.address == address) {
                it.copy(isPaired = isPaired)
            } else {
                it
            }
        }
    }

    fun updateBluetoothState() {
        bluetoothAdapter?.let {
            isBluetoothEnabled = it.isEnabled
            Log.d(TAG, "Bluetooth state: $isBluetoothEnabled")
        }
    }

    // Separate permission checking methods
    fun checkLocationPermission(context: Context): Boolean {
        val grantedFineLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val grantedCoarseLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        return grantedFineLocation || grantedCoarseLocation
    }
    
    fun checkBluetoothPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val grantedScan = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            
            val grantedConnect = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            
            return grantedScan && grantedConnect
        } else {
            val grantedBluetooth = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
            
            val grantedBluetoothAdmin = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
            
            return grantedBluetooth && grantedBluetoothAdmin
        }
    }

    fun checkPermissions(context: Context): Boolean {
        val locationGranted = checkLocationPermission(context)
        val bluetoothGranted = checkBluetoothPermission(context)
        
        hasRequiredPermissions = locationGranted && bluetoothGranted
        Log.d(TAG, "Permissions check - Location: $locationGranted, Bluetooth: $bluetoothGranted")
        return hasRequiredPermissions
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
            errorMessage = "Missing required permissions. Please grant all Bluetooth and Location permissions."
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

                // Register receiver for discovery BEFORE starting
                registerDiscoveryReceiver(context)
                
                // Small delay to ensure receiver is registered
                delay(100)

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
                    errorMessage = "Failed to start discovery. Please check permissions and try again."
                    Log.e(TAG, "startDiscovery returned false")
                    unregisterDiscoveryReceiver(context)
                }

            } catch (e: SecurityException) {
                errorMessage = "Bluetooth permission denied. Please grant all permissions."
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

            // Show bonded devices if no devices were discovered
            if (discoveredDevices.isEmpty()) {
                val bondedDevices = getBondedDevices()
                if (bondedDevices.isNotEmpty()) {
                    discoveredDevices = bondedDevices
                    Log.d(TAG, "No devices discovered, showing ${bondedDevices.size} bonded devices")
                } else {
                    Log.d(TAG, "No devices found at all")
                }
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
        // Always unregister first to avoid duplicates
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
                            Log.d(TAG, "Device found: ${btDevice.name ?: "Unknown"} (${btDevice.address})")
                            if (shouldIncludeDevice(btDevice)) {
                                addDiscoveredDevice(btDevice, rssi)
                            } else {
                                Log.d(TAG, "Device filtered out: ${btDevice.name}")
                            }
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        Log.d(TAG, "Discovery started broadcast received")
                        isScanning = true
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "Discovery finished broadcast received. Found ${discoveredDevices.size} devices")
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
        // Must have a name
        if (device.name.isNullOrEmpty()) {
            Log.d(TAG, "Filtering out device with no name: ${device.address}")
            return false
        }
        
        // Must have valid address
        if (device.address.isNullOrEmpty() || device.address == "00:00:00:00:00:00") {
            Log.d(TAG, "Filtering out device with invalid address")
            return false
        }

        // Include all classic and dual-mode devices, skip BLE-only
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (device.type == BluetoothDevice.DEVICE_TYPE_LE) {
                Log.d(TAG, "Filtering out BLE-only device: ${device.name}")
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

            Log.i(TAG, "Device added: ${device.name} (${device.address}) RSSI: $rssi, Total: ${discoveredDevices.size}")
        }
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<DomainBluetoothDevice> {
        return try {
            if (!hasRequiredPermissions) {
                Log.w(TAG, "Missing permissions for bonded devices")
                return emptyList()
            }

            val bonded = bluetoothAdapter?.bondedDevices?.mapNotNull { device ->
                if (shouldIncludeDevice(device)) {
                    convertToDomainBluetoothDevice(device)
                } else {
                    null
                }
            }?.toList() ?: emptyList()
            
            Log.d(TAG, "Retrieved ${bonded.size} bonded devices")
            bonded
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
            rssi = if (rssi != 0) rssi else null,
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

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: DomainBluetoothDevice) {
        viewModelScope.launch {
            try {
                if (!checkPermissions(applicationContext ?: return@launch)) {
                    errorMessage = "Missing permissions to connect"
                    return@launch
                }

                val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                
                if (bluetoothDevice == null) {
                    errorMessage = "Device not found"
                    Log.e(TAG, "Could not get remote device for ${device.address}")
                    return@launch
                }

                // Check if device is already paired
                if (bluetoothDevice.bondState == BluetoothDevice.BOND_NONE) {
                    // Device not paired - initiate pairing
                    Log.i(TAG, "Initiating pairing with ${device.name}")
                    val pairingResult = bluetoothDevice.createBond()
                    
                    if (pairingResult) {
                        Log.i(TAG, "Pairing initiated for ${device.name}")
                        // The connection state will be updated via broadcast receiver
                        // when pairing completes
                    } else {
                        errorMessage = "Failed to initiate pairing with ${device.name}"
                        Log.e(TAG, "createBond() returned false for ${device.name}")
                    }
                } else {
                    // Device is already paired
                    Log.i(TAG, "Device ${device.name} is already paired")
                    
                    // For already paired devices, we just update the UI
                    // The actual audio connection happens automatically for audio devices
                    // or you can trigger specific profile connections here
                    
                    // Update UI to show connected (this will be corrected by broadcast receiver
                    // if the device isn't actually connected)
                    updateDeviceConnectionState(device.address, true)
                }

            } catch (e: SecurityException) {
                errorMessage = "Permission denied for connecting"
                Log.e(TAG, "SecurityException connecting to device", e)
            } catch (e: Exception) {
                errorMessage = "Failed to connect: ${e.message}"
                Log.e(TAG, "Error connecting to device", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice(device: DomainBluetoothDevice) {
        viewModelScope.launch {
            try {
                if (!checkPermissions(applicationContext ?: return@launch)) {
                    errorMessage = "Missing permissions to disconnect"
                    return@launch
                }

                // Note: Android doesn't provide a direct API to disconnect Bluetooth devices
                // Disconnection typically happens through:
                // 1. System Bluetooth settings
                // 2. Device going out of range
                // 3. Device being turned off
                
                // For now, we can only unpair (remove bond)
                val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                
                if (bluetoothDevice != null && device.isPaired) {
                    // Attempt to remove pairing using reflection
                    // (There's no official API for this)
                    try {
                        val method = bluetoothDevice.javaClass.getMethod("removeBond")
                        val result = method.invoke(bluetoothDevice) as? Boolean
                        
                        if (result == true) {
                            Log.i(TAG, "Removed pairing for ${device.name}")
                            updateDeviceConnectionState(device.address, false)
                            updateDevicePairedState(device.address, false)
                        } else {
                            errorMessage = "Could not unpair ${device.name}. Use system Bluetooth settings."
                            Log.w(TAG, "removeBond returned false for ${device.name}")
                        }
                    } catch (e: Exception) {
                        errorMessage = "Cannot disconnect from system. Use Bluetooth settings."
                        Log.e(TAG, "Error calling removeBond", e)
                        // Fallback: just update UI
                        updateDeviceConnectionState(device.address, false)
                    }
                } else {
                    // Just update UI state
                    updateDeviceConnectionState(device.address, false)
                    Log.i(TAG, "Updated UI state for ${device.name}")
                }

            } catch (e: Exception) {
                errorMessage = "Error disconnecting: ${e.message}"
                Log.e(TAG, "Error disconnecting device", e)
            }
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
            
            if (isConnectionReceiverRegistered && connectionReceiver != null) {
                try {
                    context.unregisterReceiver(connectionReceiver)
                    isConnectionReceiverRegistered = false
                    Log.d(TAG, "Connection receiver unregistered")
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering connection receiver", e)
                }
            }
        }
        Log.d(TAG, "ViewModel cleared")
    }
}