package com.example.bluecess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bluecess.ui.theme.BluecessTheme
import com.example.bluecess.ui.theme.Typography

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BluecessTheme {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Connections and Permissions",
            style = Typography.titleLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // You'll add more settings content here later
        Text(
            text = "Settings screen will be implemented in Phase 5",
            style = Typography.bodyMedium
        )
    }
}