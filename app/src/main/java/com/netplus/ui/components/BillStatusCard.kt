package com.netplus.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netplus.data.model.BillCheckResponse
import com.netplus.data.model.Transaction
import com.netplus.util.FormatUtils

@Composable
fun BillStatusCard(
    bill: BillCheckResponse,
    adminWhatsapp: String? = null,
    onViewReceipt: (Transaction) -> Unit,
    onPayNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPaid = bill.isPaid
    val isMenunggak = !isPaid && bill.unpaidPeriods.size > 1
    val totalAmount = if (isPaid) (bill.paidTransaction?.totalAmount ?: bill.packageFee) else bill.totalDue

    val statusText = when {
        isPaid -> "LUNAS"
        isMenunggak -> "MENUNGGAK ${bill.unpaidPeriods.size} BULAN"
        else -> "BELUM LUNAS"
    }

    val headerGradient = when {
        isPaid -> Brush.horizontalGradient(listOf(Color(0xFF047857), Color(0xFF10B981)))
        isMenunggak -> Brush.horizontalGradient(listOf(Color(0xFFBE123C), Color(0xFFE11D48), Color(0xFFFB7185)))
        else -> Brush.horizontalGradient(listOf(Color(0xFFB45309), Color(0xFFD97706), Color(0xFFF59E0B)))
    }

    val statusBadgeColor = when {
        isPaid -> Color(0xFF059669)
        isMenunggak -> Color(0xFFE11D48)
        else -> Color(0xFFD97706)
    }
    val statusBadgeBg = when {
        isPaid -> Color(0xFFECFDF5)
        isMenunggak -> Color(0xFFFFF1F2)
        else -> Color(0xFFFFFBEB)
    }
    val statusBorder = when {
        isPaid -> Color(0xFFA7F3D0)
        isMenunggak -> Color(0xFFFECDD3)
        else -> Color(0xFFFDE68A)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bill_status_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Elegant top decorative color bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(headerGradient)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Period & Status Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isMenunggak) "Status Periode Tagihan" else "Periode Penagihan",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = bill.currentPeriod,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Status Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusBadgeBg,
                        border = BorderStroke(1.dp, statusBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPaid) Icons.Default.CheckCircle else Icons.Default.PendingActions,
                                contentDescription = null,
                                tint = statusBadgeColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = statusBadgeColor,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount Display Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = when {
                                isPaid -> "Total Pembayaran Diterima"
                                isMenunggak -> "Total Tunggakan (${bill.unpaidPeriods.size} Bulan)"
                                else -> "Total Tagihan Berjalan"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = FormatUtils.formatRupiah(totalAmount),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp,
                                color = when {
                                    isPaid -> Color(0xFF047857)
                                    isMenunggak -> Color(0xFFBE123C)
                                    else -> Color(0xFF1E3A8A)
                                }
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        if (isPaid && bill.paidTransaction != null) {
                            val trans = bill.paidTransaction
                            DetailRow("No. Kwitansi Invoice", trans.invoiceNumber)
                            DetailRow("Waktu Pembayaran", FormatUtils.formatDateTime(trans.paidAt))
                            DetailRow("Metode / Saluran", trans.paymentMethod)
                            DetailRow("Petugas Kasir", trans.cashierName?.ifBlank { "Sistem Otomatis" } ?: "Sistem Otomatis")
                        } else if (isMenunggak) {
                            DetailRow("Tarif Paket Internet", "${FormatUtils.formatRupiah(bill.packageFee)} / bulan")
                            DetailRow("Jumlah Bulan Menunggak", "${bill.unpaidPeriods.size} Bulan")
                            DetailRow("Batas Jatuh Tempo", bill.dueDate ?: "Tanggal ${bill.customer.dueDay} ${bill.currentPeriod}")
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Rincian Periode Belum Dibayar:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFBE123C),
                                    fontSize = 11.5.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            bill.unpaidPeriods.forEach { period ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• Tagihan $period",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
                                    )
                                    Text(
                                        text = FormatUtils.formatRupiah(bill.packageFee),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFBE123C)
                                        )
                                    )
                                }
                            }
                        } else {
                            DetailRow("Tarif Paket Internet", FormatUtils.formatRupiah(bill.packageFee))
                            DetailRow("Biaya Admin Sistem", "Gratis (Rp 0)")
                            DetailRow("Batas Jatuh Tempo", bill.dueDate ?: "Tanggal ${bill.customer.dueDay} ${bill.currentPeriod}")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Distinct Action Buttons (No redundant duplicates!)
                if (isPaid && bill.paidTransaction != null) {
                    // Paid state: View & Share official digital receipt
                    Button(
                        onClick = { onViewReceipt(bill.paidTransaction) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("view_receipt_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Buka Struk Bukti Bayar",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                } else {
                    // Unpaid state: 2 distinct useful buttons
                    // Button 1: Pay now via Midtrans / Transfer
                    // Button 2: Direct WhatsApp confirmation to cashier (with pre-filled customer details)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPayNow,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp)
                                .testTag("pay_bill_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMenunggak) "Bayar Semua Tunggakan" else "Bayar Sekarang",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                openCashierWhatsApp(
                                    context = context,
                                    customerId = bill.customer.customerCode.ifBlank { bill.customer.id.toString() },
                                    customerName = bill.customer.name,
                                    period = if (isMenunggak) bill.unpaidPeriods.joinToString(", ") else bill.currentPeriod,
                                    amount = FormatUtils.formatRupiah(totalAmount),
                                    adminPhone = adminWhatsapp
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("chat_cashier_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF059669)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF059669))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Kasir WA",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun openCashierWhatsApp(
    context: Context,
    customerId: String,
    customerName: String,
    period: String,
    amount: String,
    adminPhone: String? = null
) {
    val message = """
        Halo Kasir Netplus, saya ingin konfirmasi tagihan WiFi:
        - Kode Pelanggan: $customerId
        - Nama Pelanggan: $customerName
        - Periode: $period
        - Total Tagihan: $amount
        Mohon informasi rekening pembayaran atau konfirmasi status. Terima kasih.
    """.trimIndent()

    com.netplus.util.AdminContactHelper.openWhatsapp(
        context = context,
        targetPhone = adminPhone,
        message = message
    )
}
