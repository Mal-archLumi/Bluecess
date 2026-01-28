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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bluecess.ui.components.BluetoothScanButton
import com.example.bluecess.ui.components.ModeSelector
import com.example.bluecess.ui.theme.BluecessBlue
import com.example.bluecess.ui.theme.BluecessGreen
import com.example.bluecess.ui.theme.BluecessRed
import com.example.bluecess.ui.theme.BluecessTheme
import com.example.bluecess.ui.theme.CardGray
import com.example.bluecess.ui.theme.TextPrimary
import com.example.bluecess.ui.theme.TextSecondary
import com.example.bluecess.ui.theme.Typography
import com.example.bluecess.viewmodels.BluetoothViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        // Trigger update in ViewModel
        // This will be handled by the ViewModel's permission check
    }

    private val requestBluetoothEnable = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Bluetooth state will be automatically updated via broadcast receiver
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
    val scope = rememberCoroutineScope()

    val requiredPermissions = remember { getRequiredPermissions() }

    LaunchedEffect(Unit) {
        viewModel.initializeBluetooth(context)
        viewModel.updatePermissionsState(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.updateBluetoothState()
                    viewModel.updatePermissionsState(context)
                }
                Lifecycle.Event.ON_PAUSE -> {
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
            if (viewModel.isScanning) {
                viewModel.stopScanning(context)
            }
        }
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("BLUECESS", style = Typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(context, SettingsActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
        val allDevices = mutableListOf<com.example.bluecess.domain.models.BluetoothDevice>()
        
        viewModel.getBondedDevices().forEach { device ->
            if (!allDevices.any { it.address == device.address }) {
                allDevices.add(device)
            }
        }
        
        viewModel.discoveredDevices.forEach { device ->
            val existingIndex = allDevices.indexOfFirst { it.address == device.address }
            if (existingIndex >= 0) {
                allDevices[existingIndex] = device.copy(isPaired = allDevices[existingIndex].isPaired)
            } else {
                allDevices.add(device)
            }
        }
        
        allDevices
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!viewModel.hasRequiredPermissions) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Yellow.copy(alpha = 0.1f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                        onClick = { requestPermissionLauncher?.launch(requiredPermissions) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Grant Permissions")
                    }
                }
            }
        }

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
                Column(modifier = Modifier.padding(16.dp)) {
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
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            requestBluetoothEnable?.launch(enableBtIntent)
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Enable Bluetooth")
                    }
                }
            }
        }

        BluetoothScanButton(
            isScanning = viewModel.isScanning,
            onClick = {
                if (!viewModel.isBluetoothEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    requestBluetoothEnable?.launch(enableBtIntent)
                } else if (!viewModel.hasRequiredPermissions) {
                    requestPermissionLauncher?.launch(requiredPermissions)
                } else {
                    viewModel.startScanning(context)
                }
            }
        )

        if (devices.isEmpty() && !viewModel.isScanning) {
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
                item { Box(modifier = Modifier.height(32.dp)) }
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
        colors = CardDefaults.cardColors(containerColor = CardGray),
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

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = apps) { app ->
            AppRow(
                appName = app.name,
                isEnabled = app.isEnabled,
                onToggle = { enabled ->
                    val index = apps.indexOf(app)
                    apps[index] = app.copy(isEnabled = enabled)
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
        item { Box(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun AppRow(
    appName: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardGray),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.LightGray
                ) {
                    Box(contentAlignment = Alignment.Center) {
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

fun getRequiredPermissions(): Array<String> {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        // Location is still needed for discovering nearby devices even on Android 12+
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
    } else {
        // Android 11 and below
        permissions.add(Manifest.permission.BLUETOOTH)
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    return permissions.toTypedArray()
}

data class AppInfo(val name: String, val isEnabled: Boolean)