package com.netplus.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netplus.data.model.Customer
import com.netplus.util.FormatUtils

@Composable
fun CustomerCard(
    customer: Customer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isActive = customer.status.equals("ACTIVE", ignoreCase = true)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("customer_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Customer Identity & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = customer.name.firstOrNull()?.uppercase() ?: "P",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E40AF)
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Terverifikasi",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Copy Kode Pelanggan chip with ripple
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val codeText = customer.customerCode.ifBlank { customer.id.toString() }
                                clipboard.setPrimaryClip(ClipData.newPlainText("Kode Pelanggan", codeText))
                                Toast.makeText(context, "Kode Pelanggan $codeText disalin!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Kode: ${customer.customerCode.ifBlank { customer.id.toString() }}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Salin Kode Pelanggan",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Status Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) Color(0xFFECFDF5) else Color(0xFFFFF1F2),
                    border = BorderStroke(1.dp, if (isActive) Color(0xFFA7F3D0) else Color(0xFFFECDD3))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isActive) Color(0xFF10B981) else Color(0xFFE11D48))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isActive) "AKTIF" else "ISOLIR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isActive) Color(0xFF047857) else Color(0xFFBE123C),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2x2 Metric Grid with refined cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    icon = Icons.Default.Speed,
                    label = "Paket Internet",
                    value = customer.packageName?.ifEmpty { "15 Mbps" } ?: "15 Mbps",
                    subValue = FormatUtils.formatRupiah(customer.monthlyFee) + "/bln",
                    iconColor = Color(0xFF2563EB),
                    bgTint = Color(0xFFEFF6FF),
                    modifier = Modifier.weight(1f)
                )

                MetricTile(
                    icon = Icons.Default.CalendarMonth,
                    label = "Jatuh Tempo",
                    value = "Tgl ${customer.dueDay}",
                    subValue = "Setiap Bulan",
                    iconColor = Color(0xFFD97706),
                    bgTint = Color(0xFFFFFBEB),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    icon = Icons.Default.Router,
                    label = "Titik ODP",
                    value = customer.odpLocation?.ifEmpty { "-" } ?: "-",
                    subValue = "Distribusi Fiber",
                    iconColor = Color(0xFF7C3AED),
                    bgTint = Color(0xFFF5F3FF),
                    modifier = Modifier.weight(1f)
                )

                MetricTile(
                    icon = Icons.Default.Home,
                    label = "Alamat Pasang",
                    value = customer.address?.ifEmpty { "-" } ?: "-",
                    subValue = "Lokasi Pelanggan",
                    iconColor = Color(0xFF059669),
                    bgTint = Color(0xFFECFDF5),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    subValue: String,
    iconColor: Color,
    bgTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = bgTint,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.padding(5.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                ),
                maxLines = 1
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1
            )
        }
    }
}
