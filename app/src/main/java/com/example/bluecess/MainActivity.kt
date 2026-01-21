package com.example.bluecess

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bluecess.ui.components.AppRow
import com.example.bluecess.ui.components.BluetoothScanButton
import com.example.bluecess.ui.components.ModeSelector
import com.example.bluecess.ui.theme.BluecessGreen
import com.example.bluecess.ui.theme.BluecessRed
import com.example.bluecess.ui.theme.CardGray
import com.example.bluecess.ui.theme.TextPrimary
import com.example.bluecess.ui.theme.TextSecondary
import com.example.bluecess.ui.theme.Typography
import com.example.bluecess.ui.theme.BluecessTheme
import com.example.bluecess.viewmodels.BluetoothViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Permissions granted
            // You might want to trigger a scan automatically here
        } else {
            // Permissions denied
            // Show message to user
        }
    }

    // Bluetooth enable request launcher
    private val requestBluetoothEnable = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Handle Bluetooth enable result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Bluetooth adapter
        val bluetoothManager = getSystemService(android.bluetooth.BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        setContent {
            BluecessTheme {
                BluetoothApp(
                    requestPermissionLauncher = requestPermissionLauncher,
                    requestBluetoothEnable = requestBluetoothEnable,
                    bluetoothAdapter = bluetoothAdapter
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothApp(
    requestPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null,
    requestBluetoothEnable: androidx.activity.result.ActivityResultLauncher<android.content.Intent>? = null,
    bluetoothAdapter: BluetoothAdapter? = null
) {
    val viewModel: BluetoothViewModel = viewModel()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Get required permissions for this Android version
    val requiredPermissions = remember {
        getRequiredPermissions()
    }

    // Check permissions and Bluetooth state when app starts/resumes
    LaunchedEffect(Unit) {
        viewModel.initializeBluetooth(context)
        viewModel.updatePermissionsState(context)

        // Listen for Bluetooth state changes
        snapshotFlow { viewModel.isBluetoothEnabled }.collectLatest { isEnabled ->
            if (!isEnabled && viewModel.isScanning) {
                viewModel.stopScanning(context)
            }
        }
    }

    // Handle lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.updateBluetoothState()
                    viewModel.updatePermissionsState(context)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Stop scanning when app goes to background
                    if (viewModel.isScanning) {
                        viewModel.stopScanning(context)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Clean up
            if (viewModel.isScanning) {
                viewModel.stopScanning(context)
            }
        }
    }

    // Show error messages
    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            // Clear error after showing
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "BLUECESS",
                        style = Typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* Share action */ }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Navigate to settings
                        val intent = Intent(context, SettingsActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Mode Selector
            ModeSelector(
                selectedIndex = viewModel.selectedTab,
                onTabSelected = { viewModel.selectedTab = it }
            )

            when (viewModel.selectedTab) {
                0 -> DeviceView(
                    viewModel = viewModel,
                    context = context,
                    requestPermissionLauncher = requestPermissionLauncher,
                    requestBluetoothEnable = requestBluetoothEnable,
                    requiredPermissions = requiredPermissions
                )
                1 -> AppView()
            }
        }
    }
}

@Composable
fun DeviceView(
    viewModel: BluetoothViewModel,
    context: android.content.Context,
    requestPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>?,
    requestBluetoothEnable: androidx.activity.result.ActivityResultLauncher<android.content.Intent>?,
    requiredPermissions: Array<String>
) {
    val devices = remember(viewModel.discoveredDevices, viewModel.getBondedDevices()) {
        // Combine discovered and bonded devices
        val allDevices = mutableListOf<com.example.bluecess.domain.models.BluetoothDevice>()

        // Add bonded devices first
        viewModel.getBondedDevices().forEach { device ->
            if (!allDevices.any { it.address == device.address }) {
                allDevices.add(device)
            }
        }

        // Add discovered devices
        viewModel.discoveredDevices.forEach { device ->
            val existingIndex = allDevices.indexOfFirst { it.address == device.address }
            if (existingIndex >= 0) {
                // Update existing device with discovered info
                allDevices[existingIndex] = device.copy(isPaired = allDevices[existingIndex].isPaired)
            } else {
                allDevices.add(device)
            }
        }

        allDevices
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Permission warning
        if (!viewModel.hasBluetoothPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Yellow.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Permissions Required",
                        style = Typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Bluetooth and Location permissions are needed to scan for devices",
                        style = Typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Button(
                        onClick = {
                            requestPermissionLauncher?.launch(requiredPermissions)
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Grant Permissions")
                    }
                }
            }
        }

        // Bluetooth disabled warning
        if (!viewModel.isBluetoothEnabled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Red.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Bluetooth Disabled",
                        style = Typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Please enable Bluetooth to scan for devices",
                        style = Typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Button(
                        onClick = {
                            val enableBtIntent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            requestBluetoothEnable?.launch(enableBtIntent)
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Enable Bluetooth")
                    }
                }
            }
        }

        // Bluetooth Scan Button
        BluetoothScanButton(
            isScanning = viewModel.isScanning,
            onClick = {
                if (!viewModel.isBluetoothEnabled) {
                    val enableBtIntent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    requestBluetoothEnable?.launch(enableBtIntent)
                } else if (!viewModel.hasBluetoothPermission) {
                    requestPermissionLauncher?.launch(requiredPermissions)
                } else {
                    viewModel.startScanning(context)
                }
            }
        )

        // Device List
        if (devices.isEmpty() && !viewModel.isScanning) {
            // Show empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No devices found",
                    style = Typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
                Text(
                    text = "Tap 'Scan Devices' to search for Bluetooth devices",
                    style = Typography.labelMedium,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn {
                items(devices) { device ->
                    DeviceCard(
                        device = device,
                        onClick = {
                            if (device.isConnected) {
                                viewModel.disconnectDevice(device)
                            } else {
                                viewModel.connectToDevice(device)
                            }
                        }
                    )
                }

                // Add some spacing at the bottom
                item {
                    Box(
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCard(
    device: com.example.bluecess.domain.models.BluetoothDevice,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardGray
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = device.name,
                    style = Typography.bodyMedium,
                    color = TextPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (device.isConnected) "Connected" else "Not connected",
                        style = Typography.labelMedium,
                        color = TextSecondary
                    )
                    if (device.isPaired) {
                        Text(
                            text = "• Paired",
                            style = Typography.labelSmall,
                            color = BluecessGreen
                        )
                    }
                }
            }

            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = if (device.isConnected) BluecessGreen else BluecessRed,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun AppView() {
    // Mock data for apps
    val apps = remember {
        mutableStateListOf(
            AppInfo("Instagram", true),
            AppInfo("Spotify", false),
            AppInfo("TikTok", false),
            AppInfo("Music Player", false),
            AppInfo("YouTube", false),
            AppInfo("Netflix", false)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(items = apps) { app ->
            AppRow(
                appName = app.name,
                appIcon = android.R.drawable.sym_def_app_icon, // Placeholder
                isEnabled = app.isEnabled,
                onToggle = { enabled ->
                    val index = apps.indexOf(app)
                    apps[index] = app.copy(isEnabled = enabled)

                    // Only allow one app to be enabled at a time
                    if (enabled) {
                        apps.forEachIndexed { i, otherApp ->
                            if (i != index) {
                                apps[i] = otherApp.copy(isEnabled = false)
                            }
                        }
                    }
                }
            )
        }

        // Add some spacing at the bottom
        item {
            Box(
                modifier = Modifier.height(32.dp)
            )
        }
    }
}

@Composable
fun AppRow(
    appName: String,
    appIcon: Int,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardGray
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon and name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Placeholder for app icon
                Surface(
                    modifier = Modifier
                        .size(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.LightGray
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = appName.first().toString(),
                            style = Typography.titleLarge,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = appName,
                    style = Typography.bodyMedium,
                    color = TextPrimary
                )
            }

            // Toggle switch
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BluecessGreen,
                    checkedTrackColor = BluecessGreen.copy(alpha = 0.5f)
                )
            )
        }
    }
}

// Helper function to get required permissions based on Android version
// Removed @Composable annotation since it doesn't need to be composable
fun getRequiredPermissions(): Array<String> {
    val permissions = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        // Android 12+ also needs notification permission for foreground service
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        // Android 6-11
        permissions.add(Manifest.permission.BLUETOOTH)
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    return permissions.toTypedArray()
}

// Data class for AppInfo
data class AppInfo(
    val name: String,
    val isEnabled: Boolean
)

@Preview(showBackground = true)
@Composable
fun BluetoothAppPreview() {
    BluecessTheme {
        BluetoothApp()
    }
}