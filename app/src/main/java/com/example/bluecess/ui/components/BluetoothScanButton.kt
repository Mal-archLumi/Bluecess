package com.example.bluecess.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.bluecess.R
import com.example.bluecess.ui.theme.BluecessBlue
import com.example.bluecess.ui.theme.TextSecondary
import com.example.bluecess.ui.theme.Typography

@Composable
fun BluetoothScanButton(
    isScanning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Bluetooth Button
        androidx.compose.material3.Surface(
            onClick = onClick,
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = BluecessBlue,
            shadowElevation = 4.dp
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.bluetooth),
                    contentDescription = "Bluetooth",
                    modifier = Modifier
                        .size(60.dp)
                        .rotate(if (isScanning) rotation else 0f),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scan Devices Text
        Text(
            text = if (isScanning) "Scanning..." else "Scan Devices",
            style = Typography.labelLarge,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}