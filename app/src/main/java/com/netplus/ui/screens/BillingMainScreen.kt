package com.netplus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.netplus.data.model.BillCheckResponse
import com.netplus.ui.components.AnnouncementBannerCarousel
import com.netplus.ui.components.AnnouncementDetailDialog
import com.netplus.ui.components.NotificationCenterBottomSheet
import com.netplus.ui.components.PromoPopupDialog
import com.netplus.ui.components.BillStatusCard
import com.netplus.ui.components.ConnectionTestDialog
import com.netplus.ui.components.CustomerCard
import com.netplus.ui.components.HeaderHeroBanner
import com.netplus.ui.components.MidtransPaymentScreen
import com.netplus.ui.components.PaymentMethodsDialog
import com.netplus.ui.components.QuickActionGrid
import com.netplus.ui.components.ReceiptDialog
import com.netplus.ui.components.ReportIssueDialog
import com.netplus.ui.components.SearchSection
import com.netplus.ui.components.SecuritySettingsDialog
import com.netplus.ui.components.SelfHelpGuideDialog
import com.netplus.ui.components.TransactionHistoryList
import com.netplus.ui.viewmodel.BillingUiState
import com.netplus.ui.viewmodel.BillingViewModel
import com.netplus.util.FormatUtils
import com.netplus.speedtest.ui.SpeedTestScreen
import com.netplus.speedtest.viewmodel.SpeedTestViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun BillingMainScreen(
    viewModel: BillingViewModel,
    modifier: Modifier = Modifier,
    fragmentActivity: FragmentActivity? = null
) {
    val state by viewModel.state.collectAsState()
    val activity = fragmentActivity ?: (LocalContext.current as? FragmentActivity)

    // 1. If Midtrans Payment is active, show the Full-Screen Midtrans Checkout View
    if (!state.midtransPaymentUrl.isNullOrBlank()) {
        val paymentUrl = state.midtransPaymentUrl!!
        val currentBill = (state.uiState as? BillingUiState.Success)?.bill
        val totalAmount = currentBill?.let { if (it.totalDue > 0) it.totalDue else it.packageFee } ?: 0L
        MidtransPaymentScreen(
            paymentUrl = paymentUrl,
            totalAmountFormatted = FormatUtils.formatRupiah(totalAmount),
            onPaymentSuccess = { viewModel.onMidtransPaymentSuccess() },
            onClose = { viewModel.closeMidtransPayment() }
        )
    } else if (state.showSpeedTestScreen) {
        val speedTestViewModel: SpeedTestViewModel = viewModel()
        SpeedTestScreen(
            viewModel = speedTestViewModel,
            onBack = { viewModel.closeSpeedTest() }
        )
    } else {
        // 2. Standard Screen Transitions between Search & Dashboard
        AnimatedContent(
            targetState = state.uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ScreenTransition"
        ) { uiState ->
            when (uiState) {
                is BillingUiState.Success -> {
                    // Back button returns to Search
                    BackHandler {
                        viewModel.resetToSearch()
                    }

                    val localCtx = LocalContext.current
                    DashboardView(
                        bill = uiState.bill,
                        isRefreshing = state.isRefreshing,
                        isBiometricEnabled = state.isBiometricEnabled,
                        announcements = state.announcements,
                        adminWhatsapp = state.adminWhatsappNumber,
                        onAnnouncementClick = { ann -> viewModel.openAnnouncementDetail(ann) },
                        onOpenNotifications = { viewModel.openNotificationCenter() },
                        hasUnreadNotifications = state.announcements.isNotEmpty(),
                        onRefresh = { viewModel.refreshBill() },
                        onBackToSearch = { viewModel.resetToSearch() },
                        onOpenPayment = { viewModel.openPaymentSheet() },
                        onTestConnection = { viewModel.startConnectionTest() },
                        onSpeedTest = { viewModel.openSpeedTest() },
                        onSelfHelp = { viewModel.openSelfHelpDialog() },
                        onOpenReceipt = { trans -> viewModel.openReceipt(trans) },
                        onOpenSecuritySettings = { viewModel.openSecuritySettings() }
                    )
                }

                else -> {
                    SearchAndStatusView(
                        state = state,
                        viewModel = viewModel,
                        activity = activity
                    )
                }
            }
        }

        // Modal Dialog: Digital Receipt
        state.selectedReceipt?.let { trans ->
            ReceiptDialog(
                transaction = trans,
                onDismiss = { viewModel.closeReceipt() }
            )
        }

        // Modal Dialog: Payment Options (Midtrans In-App + Transfer)
        if (state.showPaymentSheet) {
            val currentBill = (state.uiState as? BillingUiState.Success)?.bill
            if (currentBill != null) {
                PaymentMethodsDialog(
                    bill = currentBill,
                    adminWhatsapp = state.adminWhatsappNumber,
                    isCreatingMidtrans = state.isCreatingMidtrans,
                    onPayWithMidtrans = { viewModel.startMidtransPayment(openInExternalBrowser = false) },
                    onDismiss = { viewModel.closePaymentSheet() }
                )
            }
        }

        // Modal Dialog: Lapor Gangguan
        if (state.showReportIssueDialog) {
            val currentCustomer = (state.uiState as? BillingUiState.Success)?.bill?.customer
            ReportIssueDialog(
                customer = currentCustomer,
                adminWhatsapp = state.adminWhatsappNumber,
                onDismiss = { viewModel.closeReportIssueDialog() }
            )
        }

        // Modal Dialog: Solusi Mandiri (Restart Router & Cek Indikator)
        if (state.showSelfHelpDialog) {
            val currentCustomer = (state.uiState as? BillingUiState.Success)?.bill?.customer
            SelfHelpGuideDialog(
                customer = currentCustomer,
                adminWhatsapp = state.adminWhatsappNumber,
                onDismiss = { viewModel.closeSelfHelpDialog() }
            )
        }

        // Modal Dialog: Diagnostik & Tes Jaringan Server
        if (state.showConnectionTestDialog) {
            ConnectionTestDialog(
                isTesting = state.isTestingConnection,
                testResult = state.connectionTestResult,
                onRetry = { viewModel.startConnectionTest() },
                onDismiss = { viewModel.closeConnectionTest() }
            )
        }

        // Modal Dialog: Pengaturan Keamanan & Ingat Akun (Biometrik)
        if (state.showSecuritySettingsDialog) {
            val currentBill = (state.uiState as? BillingUiState.Success)?.bill
            val targetCustId = currentBill?.customer?.customerCode?.ifBlank { currentBill.customer.id.toString() } ?: state.rememberedId.ifBlank { state.query }
            val targetCustName = currentBill?.customer?.name ?: state.rememberedName
            SecuritySettingsDialog(
                customerId = targetCustId,
                customerName = targetCustName,
                initialBiometricEnabled = state.isBiometricEnabled,
                initialAutoLoginEnabled = state.isAutoLoginEnabled,
                onSavePreferences = { enableBio, enableAuto ->
                    viewModel.saveRememberPreference(enableBio, enableAuto)
                },
                onClearPreferences = {
                    viewModel.clearRememberPreference()
                },
                onDismiss = { viewModel.closeSecuritySettings() }
            )
        }

        // Modal Sheet: Pusat Informasi & Notifikasi Pengumuman
        if (state.showNotificationCenter) {
            NotificationCenterBottomSheet(
                announcements = state.announcements,
                onSelectAnnouncement = { ann ->
                    viewModel.openAnnouncementDetail(ann)
                },
                onDismiss = { viewModel.closeNotificationCenter() }
            )
        }

        // Modal Dialog: Detail Pengumuman Lengkap
        state.selectedAnnouncement?.let { ann ->
            AnnouncementDetailDialog(
                announcement = ann,
                adminWhatsapp = state.adminWhatsappNumber,
                onDismiss = { viewModel.closeAnnouncementDetail() }
            )
        }

        // Modal Dialog: Promo Popup Otomatis saat Pertama Masuk Dashboard
        state.activePromoPopup?.let { promo ->
            PromoPopupDialog(
                promo = promo,
                adminWhatsapp = state.adminWhatsappNumber,
                onDismiss = { viewModel.dismissPromoPopup() },
                onClaimPromo = { viewModel.dismissPromoPopup() }
            )
        }
    }
}

/**
 * 1. LAYAR PENCARIAN & STATUS AWAL
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAndStatusView(
    state: com.netplus.ui.viewmodel.BillingScreenState,
    viewModel: BillingViewModel,
    activity: FragmentActivity? = null
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .verticalScroll(scrollState)
        ) {
            HeaderHeroBanner()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-14).dp)
                    .padding(horizontal = 16.dp)
            ) {
                SearchSection(
                    query = state.query,
                    onQueryChanged = { viewModel.onQueryChanged(it) },
                    onSearch = { viewModel.checkBill() },
                    savedIds = state.savedIds,
                    onSelectId = { id ->
                        viewModel.onQueryChanged(id)
                        viewModel.checkBill(id)
                    },
                    onRemoveSavedId = { viewModel.removeSavedId(it) },
                    rememberedId = state.rememberedId,
                    rememberedName = state.rememberedName,
                    isBiometricEnabled = state.isBiometricEnabled,
                    onQuickLogin = {
                        activity?.let {
                            viewModel.triggerQuickLoginWithBiometric(it)
                        } ?: viewModel.checkBill(state.rememberedId)
                    },
                    onOpenSecuritySettings = { viewModel.openSecuritySettings() },
                    onClearRememberedAccount = { viewModel.clearRememberPreference() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (val uiState = state.uiState) {
                    is BillingUiState.Loading -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Mengambil Data ke Server...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Memeriksa Kode: ${state.query}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is BillingUiState.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFE11D48),
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Data Tidak Ditemukan",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFBE123C)
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = uiState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.checkBill() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Coba Lagi")
                                }
                            }
                        }
                    }

                    else -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Layanan Mandiri Pelanggan Netplus",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Masukkan Kode Pelanggan Anda di atas untuk membuka dashboard tagihan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

/**
 * 2. LAYAR DASHBOARD PELANGGAN LENGKAP & ELEGAN
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardView(
    bill: BillCheckResponse,
    isRefreshing: Boolean,
    isBiometricEnabled: Boolean,
    announcements: List<com.netplus.data.model.Announcement>,
    adminWhatsapp: String? = null,
    onAnnouncementClick: (com.netplus.data.model.Announcement) -> Unit,
    onOpenNotifications: () -> Unit,
    hasUnreadNotifications: Boolean,
    onRefresh: () -> Unit,
    onBackToSearch: () -> Unit,
    onOpenPayment: () -> Unit,
    onTestConnection: () -> Unit,
    onSpeedTest: () -> Unit,
    onSelfHelp: () -> Unit,
    onOpenReceipt: (com.netplus.data.model.Transaction) -> Unit,
    onOpenSecuritySettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()
    val customer = bill.customer
    val isActive = customer.status.equals("ACTIVE", ignoreCase = true)

    val gradientHeader = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF090E1A),
            Color(0xFF0F172A),
            Color(0xFF1E3A8A)
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Header Hero Dashboard Elegan
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .background(gradientHeader)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Top Bar: Back / Ganti Kode, Security Settings, and Live Network Status Chip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                modifier = Modifier
                                    .testTag("change_id_button")
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onBackToSearch() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Ganti Kode",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ganti Kode",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Keamanan / Ingat Akun (Biometrik) Button
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isBiometricEnabled) Color(0xFF10B981).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isBiometricEnabled) Color(0xFF10B981).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .testTag("security_settings_button")
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenSecuritySettings() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isBiometricEnabled) Icons.Default.Fingerprint else Icons.Default.Security,
                                            contentDescription = "Keamanan",
                                            tint = if (isBiometricEnabled) Color(0xFF34D399) else Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isBiometricEnabled) "Sidik Jari" else "Ingat Saya",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isBiometricEnabled) Color(0xFF34D399) else Color.White,
                                                fontSize = 10.5.sp
                                            )
                                        )
                                    }
                                }

                                // Live connection badge
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isActive) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFE11D48).copy(alpha = 0.2f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isActive) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFE11D48).copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (isActive) Color(0xFF34D399) else Color(0xFFF87171))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isActive) "FIBER AKTIF" else "ISOLIR",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isActive) Color(0xFF34D399) else Color(0xFFF87171),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                // Notification Bell Button in Dashboard
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenNotifications() }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = "Pusat Pengumuman",
                                                tint = Color.White,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                    if (hasUnreadNotifications) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFEF4444))
                                                .align(Alignment.TopEnd)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Customer Identity Profile
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF2563EB).copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF60A5FA).copy(alpha = 0.8f)),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = customer.name.firstOrNull()?.uppercase() ?: "P",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "Halo, ${customer.name} 👋",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 20.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val displayCode = customer.customerCode.ifBlank { customer.id.toString() }
                                    Text(
                                        text = "Kode: $displayCode",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF93C5FD),
                                            fontSize = 12.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.White.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = customer.packageName?.ifBlank { "Broadband" } ?: "Broadband",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-16).dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Dynamic Announcement & Promo Banner Carousel in Dashboard
                    if (announcements.isNotEmpty()) {
                        AnnouncementBannerCarousel(
                            announcements = announcements,
                            onAnnouncementClick = onAnnouncementClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 1. Status Tagihan Aktif (Fintech statement card)
                    BillStatusCard(
                        bill = bill,
                        adminWhatsapp = adminWhatsapp,
                        onViewReceipt = onOpenReceipt,
                        onPayNow = onOpenPayment
                    )

                    // 2. Layanan & Bantuan Cepat (4 fungsi: Tes Jaringan, Uji Speed, Solusi Mandiri, Bantuan CS)
                    QuickActionGrid(
                        onTestConnectionClicked = onTestConnection,
                        onSpeedTestClicked = onSpeedTest,
                        onSelfHelpClicked = onSelfHelp,
                        onCsHelpClicked = {
                            val custCode = customer.customerCode.ifBlank { customer.id.toString() }
                            val message = "Halo CS Netplus, saya butuh bantuan untuk Kode Pelanggan $custCode atas nama ${customer.name}."
                            com.netplus.util.AdminContactHelper.openWhatsapp(
                                context = context,
                                targetPhone = adminWhatsapp,
                                message = message
                            )
                        }
                    )

                    // 3. Detail Profil & Spesifikasi Teknis Jaringan
                    CustomerCard(customer = customer)

                    // 4. Riwayat Transaksi Historis & Cetak Struk
                    TransactionHistoryList(
                        transactions = bill.history,
                        onSelectTransaction = onOpenReceipt
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
