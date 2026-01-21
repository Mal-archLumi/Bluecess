package com.example.bluecess.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bluecess.domain.models.BluetoothDevice
import com.example.bluecess.ui.theme.BluecessGreen
import com.example.bluecess.ui.theme.BluecessRed
import com.example.bluecess.ui.theme.CardGray
import com.example.bluecess.ui.theme.TextPrimary
import com.example.bluecess.ui.theme.TextSecondary
import com.example.bluecess.ui.theme.Typography

@Composable
fun DeviceCard(
    device: BluetoothDevice,
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
            Column {
                Text(
                    text = device.name,
                    style = Typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = if (device.isConnected) "Connected" else "Not connected",
                    style = Typography.labelMedium,
                    color = TextSecondary
                )
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