package com.example.bluecess.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.bluecess.ui.theme.BluecessGreen
import com.example.bluecess.ui.theme.CardGray
import com.example.bluecess.ui.theme.TextPrimary
import com.example.bluecess.ui.theme.Typography

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
                // Placeholder for app icon - you'll need actual app icons
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = androidx.compose.ui.graphics.Color.LightGray
                ) {
                    // This would be an actual app icon
                    androidx.compose.foundation.layout.Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = appName.first().toString(),
                            style = Typography.titleLarge,
                            color = androidx.compose.ui.graphics.Color.White
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