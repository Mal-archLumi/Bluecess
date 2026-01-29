package com.example.bluecess

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.bluecess.ui.theme.BluecessBlue
import com.example.bluecess.ui.theme.BluecessTheme
import com.example.bluecess.ui.theme.CardGray
import com.example.bluecess.ui.theme.TextPrimary
import com.example.bluecess.ui.theme.TextSecondary
import com.example.bluecess.ui.theme.Typography

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BluecessTheme {
                SettingsScreen(onBackPressed = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var bluetoothPermission by remember { mutableStateOf(false) }
    var locationPermission by remember { mutableStateOf(false) }
    var notificationPermission by remember { mutableStateOf(false) }
    
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Update permission states after request
        bluetoothPermission = checkBluetoothPermission(context)
        locationPermission = checkLocationPermission(context)
        notificationPermission = checkNotificationPermission(context)
    }
    
    fun updatePermissions() {
        bluetoothPermission = checkBluetoothPermission(context)
        locationPermission = checkLocationPermission(context)
        notificationPermission = checkNotificationPermission(context)
    }
    
    // Use LaunchedEffect to update permissions on resume
    LaunchedEffect(lifecycleOwner) {
        updatePermissions()
    }
    
    // Add lifecycle observer
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updatePermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", style = Typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Connections and Permissions",
                    style = Typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                Text(
                    text = "Manage app permissions and settings",
                    style = Typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
                )
                
                // Bluetooth Permission
                PermissionCard(
                    title = "Bluetooth Access",
                    description = "Required for scanning and connecting to Bluetooth devices",
                    icon = Icons.Filled.Bluetooth,
                    isGranted = bluetoothPermission,
                    onRequestPermission = {
                        requestBluetoothPermissions(requestPermissionLauncher)
                    }
                )
                
                // Location Permission
                PermissionCard(
                    title = "Location Access",
                    description = "Required for Bluetooth device discovery",
                    icon = Icons.Filled.LocationOn,
                    isGranted = locationPermission,
                    onRequestPermission = {
                        requestLocationPermission(requestPermissionLauncher)
                    }
                )
                
                // Notification Permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionCard(
                        title = "Notifications",
                        description = "Required for showing app notifications",
                        icon = Icons.Filled.Notifications,
                        isGranted = notificationPermission,
                        onRequestPermission = {
                            requestNotificationPermission(requestPermissionLauncher)
                        }
                    )
                }
                
                // App Info Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CardGray)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = "Security",
                            tint = BluecessBlue
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Information",
                                style = Typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Version 1.0 • Package: com.example.bluecess",
                                style = Typography.labelMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                            }
                        ) {
                            Text("App Info")
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, contentDescription = title, tint = BluecessBlue)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = Typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = description,
                        style = Typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Text(
                    text = if (isGranted) "Granted" else "Required",
                    style = Typography.labelMedium,
                    color = if (isGranted) Color.Green else Color.Red
                )
            }
            
            if (!isGranted) {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

// Permission checking functions
fun checkBluetoothPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

fun checkLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
           ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true // Permission not required before Android 13
    }
}

// Permission requesting functions
fun requestBluetoothPermissions(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
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
    launcher.launch(permissions)
}

fun requestLocationPermission(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
}

fun requestNotificationPermission(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }
}