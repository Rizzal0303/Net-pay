package com.netplus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuickActionGrid(
    onTestConnectionClicked: () -> Unit,
    onSpeedTestClicked: () -> Unit,
    onSelfHelpClicked: () -> Unit,
    onCsHelpClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_action_grid"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Layanan & Bantuan Cepat",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Netplus Care",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Tes Koneksi / Ping Server ke Router
                ActionItem(
                    icon = Icons.Default.NetworkCheck,
                    label = "Tes Jaringan",
                    subLabel = "Ping Server",
                    iconTint = Color(0xFF2563EB),
                    bgTint = Color(0xFFEFF6FF),
                    testTag = "action_test_connection",
                    onClick = onTestConnectionClicked
                )

                // 2. Uji Kecepatan Internet (Speedtest)
                ActionItem(
                    icon = Icons.Default.Speed,
                    label = "Uji Speed",
                    subLabel = "Speedtest",
                    iconTint = Color(0xFFD97706),
                    bgTint = Color(0xFFFFFBEB),
                    testTag = "action_speed_test",
                    onClick = onSpeedTestClicked
                )

                // 3. Solusi Mandiri (Restart Router & Cek Indikator)
                ActionItem(
                    icon = Icons.Default.Build,
                    label = "Solusi Mandiri",
                    subLabel = "Restart Modem",
                    iconTint = Color(0xFF059669),
                    bgTint = Color(0xFFECFDF5),
                    testTag = "action_self_help",
                    onClick = onSelfHelpClicked
                )

                // 4. Hubungi CS WhatsApp
                ActionItem(
                    icon = Icons.Default.HeadsetMic,
                    label = "Bantuan CS",
                    subLabel = "Chat 24 Jam",
                    iconTint = Color(0xFF7C3AED),
                    bgTint = Color(0xFFF5F3FF),
                    testTag = "action_cs_help",
                    onClick = onCsHelpClicked
                )
            }
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    subLabel: String,
    iconTint: Color,
    bgTint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = bgTint,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subLabel,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
