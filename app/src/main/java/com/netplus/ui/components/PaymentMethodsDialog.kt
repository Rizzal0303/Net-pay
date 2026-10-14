package com.netplus.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netplus.data.model.BillCheckResponse
import com.netplus.util.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fintech-style Bottom Sheet Drawer for payment flow (Option A).
 * Featuring luxury bill summary card, interactive segmented tabs (Instant, Bank Transfer, Cashier WA),
 * digital ATM virtual cards, and seamless Midtrans integration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsDialog(
    bill: BillCheckResponse,
    adminWhatsapp: String? = null,
    isCreatingMidtrans: Boolean = false,
    onPayWithMidtrans: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val totalAmount = if (bill.totalDue > 0) bill.totalDue else bill.packageFee
    val custCode = bill.customer.customerCode.ifBlank { bill.customer.id.toString() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Tab state: 0 = Instan (Midtrans/QRIS/VA), 1 = Transfer Bank Manual, 2 = Kasir/WA
    var selectedTab by remember { mutableIntStateOf(0) }
    var isBreakdownExpanded by remember { mutableStateOf(false) }

    val waConfirmationMessage = """
        Halo Kasir Netplus, saya ingin konfirmasi pembayaran tagihan WiFi:
        - Kode Pelanggan: $custCode
        - Nama Pelanggan: ${bill.customer.name}
        - Periode: ${bill.currentPeriod}
        - Paket: ${bill.customer.packageName ?: "Broadband"}
        - Total Tagihan: ${FormatUtils.formatRupiah(totalAmount)}
        - Tujuan Transfer: Bank BRI (655901025987537) / SeaBank (901976185320) a.n mohammad rizzal p
        Mohon konfirmasi dan verifikasi status pembayaran. Terima kasih.
    """.trimIndent()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("payment_methods_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = null,
                            tint = Color(0xFF1E40AF),
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Metode Pembayaran",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Netplus Secure Billing Gateway",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Luxury Fintech Bill Summary Card (Header Ringkasan Tagihan yang Mewah)
            LuxuryBillSummaryCard(
                bill = bill,
                totalAmount = totalAmount,
                custCode = custCode,
                isExpanded = isBreakdownExpanded,
                onToggleExpand = { isBreakdownExpanded = !isBreakdownExpanded }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Segmented Navigation Tabs (Instan, Bank, Kasir)
            FintechSegmentedTabs(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Tab Contents
            when (selectedTab) {
                0 -> {
                    // TAB 1: Instan & Otomatis (QRIS, E-Wallet, Virtual Account via Midtrans di Dalam Aplikasi)
                    InstantPaymentTabContent(
                        isCreatingMidtrans = isCreatingMidtrans,
                        onPayWithMidtrans = onPayWithMidtrans
                    )
                }
                1 -> {
                    // TAB 2: Transfer Bank Manual (Hanya BRI & SeaBank a.n mohammad rizzal p)
                    BankTransferTabContent(
                        context = context,
                        onOpenWaConfirmation = {
                            openWhatsApp(context, waConfirmationMessage, adminWhatsapp)
                        }
                    )
                }
                2 -> {
                    // TAB 3: Kasir / Loket / WhatsApp Support
                    CashierTabContent(
                        context = context,
                        adminWhatsapp = adminWhatsapp,
                        onOpenWa = {
                            openWhatsApp(context, waConfirmationMessage, adminWhatsapp)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * Kartu Ringkasan Tagihan Bergaya Fintech Mewah dengan Accordion Breakdown.
 */
@Composable
private fun LuxuryBillSummaryCard(
    bill: BillCheckResponse,
    totalAmount: Long,
    custCode: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring()),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TOTAL TAGIHAN INTERNET",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.2.sp
                    )
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E3A8A)
                ) {
                    Text(
                        text = "Periode ${bill.currentPeriod}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF93C5FD),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Nominal Besar
            Text(
                text = FormatUtils.formatRupiah(totalAmount),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF60A5FA),
                    fontSize = 28.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Kode: $custCode",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = bill.customer.packageName?.ifBlank { "Paket Broadband" } ?: "Paket Broadband",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFBBF24),
                            fontSize = 11.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            // Accordion Toggle: Rincian Biaya
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isExpanded) "Sembunyikan Rincian" else "Lihat Rincian Biaya",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BreakdownRow(label = "Tarif Paket Internet", value = FormatUtils.formatRupiah(bill.packageFee))
                    BreakdownRow(label = "Biaya Admin Gateway", value = "Rp 0 (Gratis)", isHighlight = true)
                    BreakdownRow(
                        label = "Jatuh Tempo",
                        value = bill.dueDate ?: "Tanggal ${bill.customer.dueDay} ${bill.currentPeriod}"
                    )
                    BreakdownRow(label = "Status Pelunasan", value = "Otomatis & Real-Time")
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color = if (isHighlight) Color(0xFF34D399) else Color(0xFFF1F5F9),
                fontSize = 12.sp
            )
        )
    }
}

/**
 * Segmented Navigation Tabs dengan pill indicator modern.
 */
@Composable
private fun FintechSegmentedTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TabPillItem(
                title = "Instan & QRIS",
                icon = Icons.Default.FlashOn,
                isSelected = selectedTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(0) }
            )
            TabPillItem(
                title = "Transfer Bank",
                icon = Icons.Default.AccountBalance,
                isSelected = selectedTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(1) }
            )
            TabPillItem(
                title = "Kasir & WA",
                icon = Icons.Default.SupportAgent,
                isSelected = selectedTab == 2,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(2) }
            )
        }
    }
}

@Composable
private fun TabPillItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF1E40AF) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }
    }
}

/**
 * TAB 1: Konten Pembayaran Instan (QRIS, E-Wallet, VA via Snap Midtrans).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstantPaymentTabContent(
    isCreatingMidtrans: Boolean,
    onPayWithMidtrans: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Feature Highlight Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFEFF6FF),
            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Verifikasi Otomatis (1 - 5 Detik)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Tanpa perlu upload bukti transfer. Tagihan langsung otomatis LUNAS dan koneksi internet tetap aktif lancar.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF1E3A8A),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Visual Channel Badges
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaymentChannelBadge("QRIS Semua Bank & E-Wallet")
                    PaymentChannelBadge("BCA Virtual Account")
                    PaymentChannelBadge("Mandiri VA")
                    PaymentChannelBadge("BRI VA")
                    PaymentChannelBadge("BNI VA")
                    PaymentChannelBadge("GoPay")
                    PaymentChannelBadge("ShopeePay")
                    PaymentChannelBadge("Indomaret / Alfamart")
                }
            }
        }

        // Primary Hero Button (Snap In-App Only)
        Button(
            onClick = onPayWithMidtrans,
            enabled = !isCreatingMidtrans,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("pay_midtrans_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E40AF)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            if (isCreatingMidtrans) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Menyiapkan Gerbang Pembayaran...",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
            } else {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Bayar Instan di Aplikasi (Snap)",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                )
            }
        }

        // Security & In-App Guarantee Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "Transaksi Aman di Dalam Aplikasi • Midtrans • SSL 256-Bit",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
private fun PaymentChannelBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = BorderStroke(0.7.dp, Color(0xFFBFDBFE))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E3A8A),
                fontSize = 10.sp
            ),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

/**
 * TAB 2: Konten Transfer Bank Manual dengan Kartu Virtual ATM.
 */
@Composable
private fun BankTransferTabContent(
    context: Context,
    onOpenWaConfirmation: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Pilih salah satu rekening resmi Netplus berikut:",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        )

        // 1. Bank BRI Card
        VirtualBankCard(
            bankName = "Bank BRI",
            accountNumber = "655901025987537",
            accountHolder = "a.n mohammad rizzal p",
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF00529C), Color(0xFF0077B6))
            ),
            context = context
        )

        // 2. SeaBank Card
        VirtualBankCard(
            bankName = "SeaBank",
            accountNumber = "901976185320",
            accountHolder = "a.n mohammad rizzal p",
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFE8590C), Color(0xFFFF922B))
            ),
            context = context
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Confirmation Instruction Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFFEF3C7),
            border = BorderStroke(1.dp, Color(0xFFFDE68A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Setelah transfer sukses, klik tombol di bawah untuk mengirim foto bukti transfer ke kasir via WhatsApp agar diverifikasi.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF92400E),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                )
            }
        }

        // WhatsApp Confirmation Button
        Button(
            onClick = onOpenWaConfirmation,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Kirim Bukti Transfer ke Kasir WA",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
            )
        }
    }
}

/**
 * Kartu Bank Berdesain Kartu Virtual ATM dengan Tombol Salin Interaktif.
 */
@Composable
private fun VirtualBankCard(
    bankName: String,
    accountNumber: String,
    accountHolder: String,
    brush: Brush,
    context: Context
) {
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bankName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        )
                    }

                    // Copy Action Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (copied) Color(0xFF10B981) else Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Nomor Rekening", accountNumber)
                            clipboard.setPrimaryClip(clip)
                            copied = true
                            Toast.makeText(context, "Nomor rekening $bankName tersalin!", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                delay(2500)
                                copied = false
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Salin",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (copied) "Tersalin!" else "Salin No. Rek",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Account Number with Monospace typography
                Text(
                    text = accountNumber,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 20.sp,
                        letterSpacing = 1.5.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = accountHolder,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

/**
 * TAB 3: Konten Layanan Kasir & WhatsApp Langsung.
 */
@Composable
private fun CashierTabContent(
    context: Context,
    adminWhatsapp: String? = null,
    onOpenWa: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFDCFCE7),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Layanan Kasir Resmi Netplus",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Jam Operasional: 08.00 - 21.00 WIB",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text(
                    text = "Anda dapat melakukan pembayaran tunai langsung di kantor kasir Netplus atau berkonsultasi mengenai tagihan via WhatsApp resmi.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kontak WhatsApp Kasir:",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        text = com.netplus.util.AdminContactHelper.formatDisplayPhone(adminWhatsapp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    )
                }
            }
        }

        // Action Button: Direct WhatsApp Chat
        Button(
            onClick = onOpenWa,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Buka Chat WhatsApp Kasir",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.5.sp
                )
            )
        }
    }
}

/**
 * Utility function to launch WhatsApp with encoded message safely.
 */
private fun openWhatsApp(context: Context, message: String, targetPhone: String? = null) {
    com.netplus.util.AdminContactHelper.openWhatsapp(
        context = context,
        targetPhone = targetPhone,
        message = message
    )
}
