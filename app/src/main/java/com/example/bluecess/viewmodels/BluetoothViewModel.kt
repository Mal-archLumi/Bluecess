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
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluecess.domain.models.BluetoothDevice as DomainBluetoothDevice
import com.example.bluecess.domain.models.DeviceType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothViewModel : ViewModel() {
    companion object {
        private const val TAG = "BluetoothViewModel"
        private const val SCAN_TIMEOUT_MS = 30000L // 30 seconds
        private const val DISCOVERY_COOLDOWN_MS = 1000L // 1 second between discoveries
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val discoveryInProgress = AtomicBoolean(false)
    private var retryCount = 0

    // Scan session tracking
    private var currentScanSessionId: String? = null
    private var lastDiscoveryStartTime = 0L

    // UI States
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

    var warningMessage by mutableStateOf<String?>(null)
        private set

    var scanProgress by mutableStateOf(0f)
        private set

    var retryAvailable by mutableStateOf(false)
        private set

    var lastScanTimestamp by mutableStateOf(0L)
        private set

    // Device cache to avoid duplicates
    private val deviceCache = mutableMapOf<String, DomainBluetoothDevice>()

    init {
        Log.d(TAG, "BluetoothViewModel initialized")
    }

    fun initializeBluetooth(context: Context) {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter

            if (bluetoothAdapter == null) {
                errorMessage = "Bluetooth is not supported on this device"
                isBluetoothEnabled = false
                Log.w(TAG, "Bluetooth not supported on this device")
            } else {
                updateBluetoothState()
                Log.d(TAG, "Bluetooth initialized. Enabled: $isBluetoothEnabled")
            }
        } catch (e: SecurityException) {
            errorMessage = "Bluetooth permission denied"
            Log.e(TAG, "SecurityException during initialization", e)
        } catch (e: Exception) {
            errorMessage = "Failed to initialize Bluetooth"
            Log.e(TAG, "Error initializing Bluetooth", e)
        }
    }

    fun updateBluetoothState() {
        bluetoothAdapter?.let {
            val wasEnabled = isBluetoothEnabled
            isBluetoothEnabled = it.isEnabled

            if (wasEnabled && !isBluetoothEnabled) {
                // Bluetooth was turned off during scan
                cleanupScan()
                errorMessage = "Bluetooth was turned off"
            }

            Log.d(TAG, "Bluetooth state updated: $isBluetoothEnabled")
        }
    }

    fun checkPermissions(context: Context): Boolean {
        val permissionsToCheck = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            permissionsToCheck.addAll(listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ))
        } else {
            // Android 6-11
            permissionsToCheck.addAll(listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            ))
        }

        val allGranted = permissionsToCheck.all { permission ->
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        hasRequiredPermissions = allGranted
        return allGranted
    }

    fun updatePermissionsState(context: Context) {
        hasRequiredPermissions = checkPermissions(context)
    }

    @SuppressLint("MissingPermission")
    fun startScanning(context: Context) {
        Log.d(TAG, "startScanning called. isScanning: $isScanning, discoveryInProgress: ${discoveryInProgress.get()}")

        // Validate preconditions
        if (!checkPreconditions(context)) {
            return
        }

        if (isScanning) {
            Log.i(TAG, "Scan already in progress, stopping current scan")
            stopScanning(context)
            return
        }

        // Check if discovery is already in progress (system-wide)
        if (bluetoothAdapter?.isDiscovering == true) {
            Log.w(TAG, "System discovery already in progress, waiting...")
            warningMessage = "Finishing existing Bluetooth discovery..."

            viewModelScope.launch {
                delay(2000) // Wait 2 seconds
                if (bluetoothAdapter?.isDiscovering == true) {
                    errorMessage = "Bluetooth discovery is already running. Please wait or try again."
                    warningMessage = null
                    return@launch
                }
                warningMessage = null
                startNewDiscoverySession(context)
            }
            return
        }

        startNewDiscoverySession(context)
    }

    private fun checkPreconditions(context: Context): Boolean {
        // Check Bluetooth adapter
        if (bluetoothAdapter == null) {
            errorMessage = "Bluetooth is not available on this device"
            return false
        }

        // Check Bluetooth enabled
        if (!isBluetoothEnabled) {
            errorMessage = "Please enable Bluetooth first"
            return false
        }

        // Check permissions
        if (!checkPermissions(context)) {
            errorMessage = "Please grant all required permissions"
            return false
        }

        // Check if we recently started a discovery
        val timeSinceLastDiscovery = System.currentTimeMillis() - lastDiscoveryStartTime
        if (timeSinceLastDiscovery < DISCOVERY_COOLDOWN_MS) {
            warningMessage = "Please wait a moment before scanning again"
            return false
        }

        return true
    }

    @SuppressLint("MissingPermission")
    private fun startNewDiscoverySession(context: Context) {
        viewModelScope.launch {
            try {
                // Clear previous state
                resetScanState()

                // Generate new scan session ID
                currentScanSessionId = System.currentTimeMillis().toString()
                lastDiscoveryStartTime = System.currentTimeMillis()

                // Stop any ongoing discovery first
                safeCancelDiscovery()

                // Wait for cooldown
                delay(DISCOVERY_COOLDOWN_MS)

                // Clear device cache for this session
                deviceCache.clear()

                // Register receiver
                registerBluetoothReceiver(context)

                // Start discovery
                Log.d(TAG, "Starting Bluetooth discovery...")
                val discoveryStarted = bluetoothAdapter?.startDiscovery() ?: false

                if (discoveryStarted) {
                    isScanning = true
                    discoveryInProgress.set(true)
                    retryCount = 0
                    errorMessage = null
                    warningMessage = "Scanning for devices..."
                    lastScanTimestamp = System.currentTimeMillis()

                    Log.i(TAG, "Bluetooth discovery started successfully")

                    // Start progress animation
                    startScanProgress()

                    // Set timeout
                    mainHandler.postDelayed({
                        if (isScanning) {
                            Log.w(TAG, "Discovery timeout reached")
                            handleDiscoveryTimeout(context)
                        }
                    }, SCAN_TIMEOUT_MS)

                } else {
                    Log.e(TAG, "Failed to start discovery")
                    handleDiscoveryError("Failed to start discovery. Please try again.", context)
                }

            } catch (e: SecurityException) {
                Log.e(TAG, "Permission error starting discovery", e)
                handleDiscoveryError("Bluetooth permission denied", context)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Illegal state starting discovery", e)
                handleDiscoveryError("Bluetooth is not available", context)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error starting discovery", e)
                handleDiscoveryError("Failed to start scanning: ${e.message}", context)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning(context: Context) {
        Log.d(TAG, "stopScanning called")

        if (!isScanning && !discoveryInProgress.get()) {
            Log.d(TAG, "No scan in progress, nothing to stop")
            return
        }

        try {
            cleanupScan()
            safeCancelDiscovery()

            // Show bonded devices if no discovered devices
            if (discoveredDevices.isEmpty()) {
                discoveredDevices = getBondedDevices()
            }

            Log.i(TAG, "Scan stopped successfully. Found ${discoveredDevices.size} devices")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
            errorMessage = "Error stopping scan: ${e.message}"
        }
    }

    private fun cleanupScan() {
        mainHandler.removeCallbacksAndMessages(null)
        isScanning = false
        discoveryInProgress.set(false)
        scanProgress = 0f
        retryAvailable = false
        warningMessage = null
        currentScanSessionId = null
    }

    @SuppressLint("MissingPermission")
    private fun safeCancelDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                Log.d(TAG, "Cancelling ongoing discovery")
                val cancelled = bluetoothAdapter?.cancelDiscovery() ?: false
                Log.d(TAG, "Discovery cancellation result: $cancelled")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission error cancelling discovery", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling discovery", e)
        }

        // Always wait a bit after cancellation
        Thread.sleep(500)
    }

    private fun startScanProgress() {
        scanProgress = 0f
        val startTime = System.currentTimeMillis()

        val progressRunnable = object : Runnable {
            override fun run() {
                if (!isScanning) return

                val elapsed = System.currentTimeMillis() - startTime
                scanProgress = (elapsed.toFloat() / SCAN_TIMEOUT_MS).coerceAtMost(0.99f)

                if (isScanning) {
                    mainHandler.postDelayed(this, 100)
                }
            }
        }

        mainHandler.post(progressRunnable)
    }

    private fun handleDiscoveryError(error: String, context: Context) {
        Log.w(TAG, "Discovery error: $error")
        errorMessage = error

        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++
            retryAvailable = true
            warningMessage = "Retry available ($retryCount/$MAX_RETRY_ATTEMPTS)"

            Log.i(TAG, "Retry available. Count: $retryCount")
        } else {
            cleanupScan()
            discoveredDevices = getBondedDevices()
        }
    }

    private fun handleDiscoveryTimeout(context: Context) {
        Log.w(TAG, "Discovery timeout")
        warningMessage = "Scan timeout. Showing available devices..."

        viewModelScope.launch {
            delay(1000)
            stopScanning(context)

            if (discoveredDevices.isEmpty()) {
                errorMessage = "No devices found. Make sure your device is in pairing mode."
            }
        }
    }

    fun retryScan(context: Context) {
        if (retryAvailable && retryCount <= MAX_RETRY_ATTEMPTS) {
            Log.i(TAG, "Retrying scan. Attempt $retryCount")
            retryAvailable = false
            warningMessage = null
            startScanning(context)
        }
    }

    private fun resetScanState() {
        discoveredDevices = emptyList()
        errorMessage = null
        warningMessage = null
        scanProgress = 0f
    }

    private fun registerBluetoothReceiver(context: Context) {
        unregisterBluetoothReceiver(context)

        bluetoothReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)

                        device?.let { bluetoothDevice ->
                            // Filter out devices with no name or weird addresses
                            if (shouldIncludeDevice(bluetoothDevice)) {
                                viewModelScope.launch {
                                    addDiscoveredDevice(bluetoothDevice, rssi.toInt())
                                }
                            }
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        Log.d(TAG, "Discovery started")
                        viewModelScope.launch {
                            isScanning = true
                            discoveryInProgress.set(true)
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "Discovery finished")
                        viewModelScope.launch {
                            handleDiscoveryFinished(context)
                        }
                    }

                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        Log.d(TAG, "Bluetooth state changed: $state")

                        when (state) {
                            BluetoothAdapter.STATE_OFF -> {
                                viewModelScope.launch {
                                    isBluetoothEnabled = false
                                    cleanupScan()
                                    errorMessage = "Bluetooth was turned off"
                                }
                            }
                            BluetoothAdapter.STATE_ON -> {
                                viewModelScope.launch {
                                    isBluetoothEnabled = true
                                }
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
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }

            context.registerReceiver(bluetoothReceiver, filter)
            isReceiverRegistered = true
            Log.d(TAG, "Bluetooth receiver registered")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Bluetooth receiver", e)
            errorMessage = "Failed to initialize scanner"
        }
    }

    private fun unregisterBluetoothReceiver(context: Context) {
        try {
            bluetoothReceiver?.let { receiver ->
                if (isReceiverRegistered) {
                    context.unregisterReceiver(receiver)
                    isReceiverRegistered = false
                    Log.d(TAG, "Bluetooth receiver unregistered")
                }
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered", e)
        }
    }

    private fun shouldIncludeDevice(device: BluetoothDevice): Boolean {
        // Skip devices with null names or empty names
        if (device.name.isNullOrEmpty()) {
            return false
        }

        // Skip devices with invalid addresses
        if (device.address.isNullOrEmpty() || device.address == "00:00:00:00:00:00") {
            return false
        }

        // Filter out BLE devices if we only want classic audio devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (device.type == BluetoothDevice.DEVICE_TYPE_LE) {
                return false // Skip BLE-only devices for audio routing
            }
        }

        return true
    }

    private fun addDiscoveredDevice(device: BluetoothDevice, rssi: Int = 0) {
        val deviceKey = device.address
        val currentSessionId = currentScanSessionId

        // Only add devices from current scan session
        if (currentSessionId == null) return

        // Check if device already in cache (from this session)
        if (!deviceCache.containsKey(deviceKey)) {
            val domainDevice = convertToDomainBluetoothDevice(device, rssi)
            deviceCache[deviceKey] = domainDevice

            // Update UI with sorted list (by RSSI strength)
            discoveredDevices = deviceCache.values.sortedByDescending { it.rssi ?: -100 }

            Log.d(TAG, "New device discovered: ${device.name} (RSSI: $rssi)")
        }
    }

    private fun handleDiscoveryFinished(context: Context) {
        Log.d(TAG, "Handling discovery finished")

        isScanning = false
        discoveryInProgress.set(false)
        scanProgress = 1.0f

        // Add bonded devices if we haven't found many
        if (discoveredDevices.size < 3) {
            val bondedDevices = getBondedDevices()
            bondedDevices.forEach { bondedDevice ->
                if (!deviceCache.containsKey(bondedDevice.address)) {
                    deviceCache[bondedDevice.address] = bondedDevice
                }
            }

            discoveredDevices = deviceCache.values.sortedByDescending { it.rssi ?: -100 }
        }

        Log.i(TAG, "Discovery finished. Total devices found: ${discoveredDevices.size}")

        // Auto-cleanup after delay
        mainHandler.postDelayed({
            cleanupScan()
        }, 2000)
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<DomainBluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.mapNotNull { device ->
                if (shouldIncludeDevice(device)) {
                    convertToDomainBluetoothDevice(device)
                } else {
                    null
                }
            }?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bonded devices", e)
            emptyList()
        }
    }

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

    private fun detectDeviceType(device: BluetoothDevice): DeviceType {
        val deviceName = device.name?.lowercase() ?: ""

        return when {
            deviceName.contains("headphone") || deviceName.contains("airpod") ||
                    deviceName.contains("earbud") || deviceName.contains("bud") -> DeviceType.HEADPHONES

            deviceName.contains("speaker") || deviceName.contains("sound") ||
                    deviceName.contains("boombox") || deviceName.contains("dock") -> DeviceType.SPEAKER

            deviceName.contains("car") || deviceName.contains("auto") ||
                    deviceName.contains("vehicle") -> DeviceType.CAR

            deviceName.contains("phone") || deviceName.contains("mobile") -> DeviceType.PHONE

            else -> DeviceType.UNKNOWN
        }
    }

    fun connectToDevice(device: DomainBluetoothDevice) {
        viewModelScope.launch {
            try {
                // Implementation depends on your audio routing system
                // For now, just update UI state
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

    fun clearWarning() {
        warningMessage = null
    }

    override fun onCleared() {
        super.onCleared()
        mainHandler.removeCallbacksAndMessages(null)
        Log.d(TAG, "ViewModel cleared")
    }
}