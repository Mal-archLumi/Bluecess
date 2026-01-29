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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
import com.example.bluecess.viewmodels.AppViewModel
import com.example.bluecess.viewmodels.BluetoothViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    
    // Separate permission launchers
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // After location is granted, request Bluetooth permissions
            requestBluetoothPermissions()
        }
    }
    
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        // Update ViewModel state
        // This will be handled by the ViewModel's permission check
    }
    
    private val requestBluetoothEnable = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Bluetooth state will be automatically updated via broadcast receiver
    }
    
    private fun requestLocationPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        locationPermissionLauncher.launch(permissions)
    }
    
    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        bluetoothPermissionLauncher.launch(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bluetoothManager = getSystemService(android.bluetooth.BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        setContent {
            BluecessTheme {
                BluetoothApp(
                    requestLocationPermission = { requestLocationPermission() },
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
    requestLocationPermission: () -> Unit,
    requestBluetoothEnable: androidx.activity.result.ActivityResultLauncher<android.content.Intent>? = null,
    bluetoothAdapter: BluetoothAdapter? = null
) {
    val bluetoothViewModel: BluetoothViewModel = viewModel()
    val appViewModel: AppViewModel = viewModel()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        bluetoothViewModel.initializeBluetooth(context)
        bluetoothViewModel.updatePermissionsState(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    bluetoothViewModel.updateBluetoothState()
                    bluetoothViewModel.updatePermissionsState(context)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (bluetoothViewModel.isScanning) {
                        bluetoothViewModel.stopScanning(context)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (bluetoothViewModel.isScanning) {
                bluetoothViewModel.stopScanning(context)
            }
        }
    }

    LaunchedEffect(bluetoothViewModel.errorMessage) {
        bluetoothViewModel.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            bluetoothViewModel.clearError()
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
                selectedIndex = bluetoothViewModel.selectedTab,
                onTabSelected = { bluetoothViewModel.selectedTab = it }
            )

            when (bluetoothViewModel.selectedTab) {
                0 -> DeviceView(
                    viewModel = bluetoothViewModel,
                    context = context,
                    requestLocationPermission = requestLocationPermission,
                    requestBluetoothEnable = requestBluetoothEnable
                )
                1 -> AppView(appViewModel = appViewModel, context = context)
            }
        }
    }
}

@Composable
fun DeviceView(
    viewModel: BluetoothViewModel,
    context: android.content.Context,
    requestLocationPermission: () -> Unit,
    requestBluetoothEnable: androidx.activity.result.ActivityResultLauncher<android.content.Intent>?
) {
    var deviceToDisconnect by remember { mutableStateOf<com.example.bluecess.domain.models.BluetoothDevice?>(null) }
    
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

    // Disconnect confirmation dialog
    if (deviceToDisconnect != null) {
        AlertDialog(
            onDismissRequest = { deviceToDisconnect = null },
            title = { Text("Disconnect Device") },
            text = { 
                Text("Your phone will disconnect from ${deviceToDisconnect?.name}")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deviceToDisconnect?.let { device ->
                            viewModel.disconnectDevice(device)
                        }
                        deviceToDisconnect = null
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToDisconnect = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Check location permission first
        val hasLocationPermission = remember(viewModel.hasRequiredPermissions) {
            viewModel.checkLocationPermission(context)
        }
        
        // Check Bluetooth permission (separate)
        val hasBluetoothPermission = remember(viewModel.hasRequiredPermissions) {
            viewModel.checkBluetoothPermission(context)
        }
        
        if (!hasLocationPermission) {
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
                        text = "Location Permission Required",
                        style = Typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Location permission is needed for Bluetooth device discovery",
                        style = Typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Button(
                        onClick = requestLocationPermission,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Grant Location Permission")
                    }
                }
            }
        } else if (!hasBluetoothPermission) {
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
                        text = "Bluetooth Permission Required",
                        style = Typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Bluetooth permission is needed to scan and connect to devices",
                        style = Typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "Location permission is already granted ✓",
                        style = Typography.labelSmall,
                        color = BluecessGreen,
                        modifier = Modifier.padding(top = 8.dp)
                    )
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
                } else if (!hasLocationPermission) {
                    requestLocationPermission()
                } else if (!hasBluetoothPermission) {
                    // This will be handled by the permission check cards
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
                items(items = devices, key = { device -> device.address }) { device ->
                    DeviceCard(
                        device = device,
                        onSingleClick = {
                            if (device.isConnected) {
                                // Do nothing on single click when connected
                            } else {
                                viewModel.connectToDevice(device)
                            }
                        },
                        onDoubleClick = {
                            if (device.isConnected) {
                                deviceToDisconnect = device
                            }
                        }
                    )
                }
                item { Box(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeviceCard(
    device: com.example.bluecess.domain.models.BluetoothDevice,
    onSingleClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onSingleClick,
                onDoubleClick = onDoubleClick
            ),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppView(
    appViewModel: AppViewModel,
    context: android.content.Context
) {
    // Convert StateFlow to compose state
    val apps by appViewModel.filteredApps.collectAsState()
    
    // These are mutableStateOf properties, access directly (no collectAsState needed)
    val isLoading = appViewModel.isLoading
    val isExclusiveMode = appViewModel.isExclusiveMode
    
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        appViewModel.loadApps(context)
    }
    
    LaunchedEffect(searchQuery) {
        appViewModel.filterApps(searchQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                appViewModel.filterApps(it)
            },
            label = { Text("Search apps...") },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp)
        )
        
        // Exclusive mode toggle
        Card(
            modifier = Modifier
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
                Column {
                    Text(
                        text = "Exclusive Mode",
                        style = Typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Only one app can route audio at a time",
                        style = Typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Switch(
                    checked = isExclusiveMode,
                    onCheckedChange = { appViewModel.toggleExclusiveMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BluecessBlue,
                        checkedTrackColor = BluecessBlue.copy(alpha = 0.5f)
                    )
                )
            }
        }
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading audio apps...",
                    style = Typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else if (apps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) 
                        "No audio apps found" 
                    else 
                        "No audio apps match \"$searchQuery\"",
                    style = Typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            // Stats
            val enabledCount = apps.count { it.isAudioEnabled }
            Text(
                text = "$enabledCount audio app(s) enabled",
                style = Typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
            )
            
            LazyColumn {
                items(items = apps, key = { app -> app.packageName }) { app ->
                    AppRow(
                        appInfo = app,
                        isExclusiveMode = isExclusiveMode,
                        onToggle = { enabled ->
                            appViewModel.toggleAppAudio(context, app.packageName, enabled)
                        }
                    )
                }
                item { Box(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun AppRow(
    appInfo: com.example.bluecess.domain.models.AppInfo,
    isExclusiveMode: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // App icon using AsyncImage (Coil)
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = Color.LightGray
                ) {
                    if (appInfo.icon != null) {
                        // Convert drawable to bitmap for Coil
                        val bitmap = remember(appInfo.packageName) {
                            android.graphics.Bitmap.createBitmap(
                                appInfo.icon.intrinsicWidth,
                                appInfo.icon.intrinsicHeight,
                                android.graphics.Bitmap.Config.ARGB_8888
                            ).apply {
                                val canvas = android.graphics.Canvas(this)
                                appInfo.icon.setBounds(0, 0, canvas.width, canvas.height)
                                appInfo.icon.draw(canvas)
                            }
                        }
                        
                        AsyncImage(
                            model = bitmap,
                            contentDescription = "${appInfo.name} icon",
                            contentScale = ContentScale.FillBounds
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = appInfo.name.first().toString(),
                                style = Typography.titleLarge,
                                color = Color.White
                            )
                        }
                    }
                }
                
                Column {
                    Text(
                        text = appInfo.name,
                        style = Typography.bodyMedium,
                        color = TextPrimary
                    )
                    if (appInfo.isSystemApp) {
                        Text(
                            text = "System",
                            style = Typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
            
            Switch(
                checked = appInfo.isAudioEnabled,
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