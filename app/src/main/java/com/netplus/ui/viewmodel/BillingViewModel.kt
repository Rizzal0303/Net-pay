package com.netplus.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netplus.data.model.BillCheckResponse
import com.netplus.data.model.Transaction
import com.netplus.data.repository.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BillingUiState {
    data object Idle : BillingUiState
    data object Loading : BillingUiState
    data class Success(
        val bill: BillCheckResponse
    ) : BillingUiState
    data class Error(val message: String) : BillingUiState
}

data class BillingScreenState(
    val query: String = "",
    val uiState: BillingUiState = BillingUiState.Idle,
    val isRefreshing: Boolean = false,
    val savedIds: List<String> = emptyList(),
    val selectedReceipt: Transaction? = null,
    val showPaymentSheet: Boolean = false,
    val showReportIssueDialog: Boolean = false,
    val midtransPaymentUrl: String? = null,
    val isCreatingMidtrans: Boolean = false,
    val showConnectionTestDialog: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionTestResult: Pair<Boolean, String>? = null,
    val rememberedId: String = "",
    val rememberedName: String = "",
    val isBiometricEnabled: Boolean = false,
    val isAutoLoginEnabled: Boolean = false,
    val showSecuritySettingsDialog: Boolean = false,
    val showSelfHelpDialog: Boolean = false,
    val showSpeedTestScreen: Boolean = false,
    val announcements: List<com.netplus.data.model.Announcement> = emptyList(),
    val showNotificationCenter: Boolean = false,
    val selectedAnnouncement: com.netplus.data.model.Announcement? = null,
    val activePromoPopup: com.netplus.data.model.Announcement? = null,
    val hasShownPromoThisSession: Boolean = false,
    val adminWhatsappNumber: String = com.netplus.util.AdminContactHelper.DEFAULT_WHATSAPP
)

class BillingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BillingRepository(application.applicationContext)

    private val _state = MutableStateFlow(
        BillingScreenState(
            query = repository.getRememberedId(),
            savedIds = repository.getSavedIds(),
            rememberedId = repository.getRememberedId(),
            rememberedName = repository.getRememberedName(),
            isBiometricEnabled = repository.isBiometricEnabled(),
            isAutoLoginEnabled = repository.isAutoLoginEnabled(),
            adminWhatsappNumber = repository.getAdminWhatsapp()
        )
    )
    val state: StateFlow<BillingScreenState> = _state.asStateFlow()

    private var hasCheckedAutoLogin = false

    init {
        loadAnnouncements()
        loadAdminContact()
    }

    fun loadAdminContact() {
        viewModelScope.launch {
            val contact = repository.fetchAdminContact()
            _state.update { it.copy(adminWhatsappNumber = contact) }
        }
    }

    fun loadAnnouncements(scope: String? = null) {
        viewModelScope.launch {
            val result = repository.getAnnouncements(scope)
            result.onSuccess { list ->
                _state.update {
                    it.copy(
                        announcements = list,
                        adminWhatsappNumber = repository.getAdminWhatsapp()
                    )
                }
                checkAndTriggerPromoPopup()
            }
        }
    }

    fun openNotificationCenter() {
        _state.update { it.copy(showNotificationCenter = true) }
    }

    fun closeNotificationCenter() {
        _state.update { it.copy(showNotificationCenter = false) }
    }

    fun openAnnouncementDetail(announcement: com.netplus.data.model.Announcement) {
        _state.update { it.copy(selectedAnnouncement = announcement) }
    }

    fun closeAnnouncementDetail() {
        _state.update { it.copy(selectedAnnouncement = null) }
    }

    fun onQueryChanged(newQuery: String) {
        _state.update { it.copy(query = newQuery.trim()) }
    }

    fun checkBill(idToSearch: String? = null) {
        val targetId = (idToSearch ?: _state.value.query).trim()
        if (targetId.isEmpty()) {
            _state.update { it.copy(uiState = BillingUiState.Error("Silakan masukkan Kode Pelanggan.")) }
            return
        }

        _state.update {
            it.copy(
                query = targetId,
                uiState = BillingUiState.Loading
            )
        }

        viewModelScope.launch {
            val result = repository.checkBill(targetId)
            result.fold(
                onSuccess = { billData ->
                    _state.update {
                        it.copy(
                            uiState = BillingUiState.Success(bill = billData),
                            savedIds = repository.getSavedIds(),
                            adminWhatsappNumber = repository.getAdminWhatsapp()
                        )
                    }
                    val odp = billData.customer.odpLocation
                    if (!odp.isNullOrBlank()) {
                        loadAnnouncements(scope = odp)
                    }
                    checkAndTriggerPromoPopup()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            uiState = BillingUiState.Error(
                                error.message ?: "Kode Pelanggan tidak ditemukan di server database."
                            ),
                            savedIds = repository.getSavedIds()
                        )
                    }
                }
            )
        }
    }

    fun refreshBill() {
        val targetId = _state.value.query.trim()
        if (targetId.isEmpty()) return

        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val currentBill = (_state.value.uiState as? BillingUiState.Success)?.bill
            val odp = currentBill?.customer?.odpLocation

            // Reload announcements / notifications from billingku
            val annResult = repository.getAnnouncements(odp)
            annResult.onSuccess { list ->
                _state.update { it.copy(announcements = list) }
            }

            val result = repository.checkBill(targetId)
            result.fold(
                onSuccess = { billData ->
                    _state.update {
                        it.copy(
                            uiState = BillingUiState.Success(bill = billData),
                            isRefreshing = false,
                            savedIds = repository.getSavedIds(),
                            adminWhatsappNumber = repository.getAdminWhatsapp()
                        )
                    }
                    val newOdp = billData.customer.odpLocation
                    if (newOdp != odp && !newOdp.isNullOrBlank()) {
                        val updatedAnn = repository.getAnnouncements(newOdp)
                        updatedAnn.onSuccess { list ->
                            _state.update { it.copy(announcements = list) }
                        }
                    }
                    Toast.makeText(getApplication(), "Data tagihan & notifikasi diperbarui", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    _state.update { it.copy(isRefreshing = false) }
                    Toast.makeText(
                        getApplication(),
                        "Gagal memperbarui: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    fun startMidtransPayment(openInExternalBrowser: Boolean = false) {
        val currentBill = (_state.value.uiState as? BillingUiState.Success)?.bill ?: return
        val customerId = if (currentBill.customer.id > 0) {
            currentBill.customer.id.toString()
        } else {
            _state.value.query.ifBlank { currentBill.customer.customerCode }
        }

        _state.update { it.copy(isCreatingMidtrans = true) }

        viewModelScope.launch {
            val result = repository.getMidtransPaymentUrl(customerId)
            result.fold(
                onSuccess = { url ->
                    if (openInExternalBrowser) {
                        _state.update {
                            it.copy(
                                isCreatingMidtrans = false,
                                showPaymentSheet = false
                            )
                        }
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            getApplication<Application>().startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                getApplication(),
                                "Gagal membuka browser: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            _state.update { it.copy(midtransPaymentUrl = url) }
                        }
                    } else {
                        // Open full-screen Midtrans WebView
                        _state.update {
                            it.copy(
                                isCreatingMidtrans = false,
                                showPaymentSheet = false,
                                midtransPaymentUrl = url
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isCreatingMidtrans = false) }
                    Toast.makeText(
                        getApplication(),
                        "Midtrans Gateway: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    fun onMidtransPaymentSuccess() {
        _state.update { it.copy(midtransPaymentUrl = null) }
        Toast.makeText(getApplication(), "Pembayaran Midtrans berhasil diproses!", Toast.LENGTH_SHORT).show()
        // Refresh bill status to show paid and receipt
        checkBill()
    }

    fun closeMidtransPayment() {
        _state.update { it.copy(midtransPaymentUrl = null) }
        // Re-check in case payment finished just before closing
        checkBill()
    }

    fun removeSavedId(id: String) {
        repository.removeSavedId(id)
        _state.update { it.copy(savedIds = repository.getSavedIds()) }
    }

    fun openReceipt(transaction: Transaction) {
        _state.update { it.copy(selectedReceipt = transaction) }
    }

    fun closeReceipt() {
        _state.update { it.copy(selectedReceipt = null) }
    }

    fun openPaymentSheet() {
        _state.update { it.copy(showPaymentSheet = true) }
    }

    fun closePaymentSheet() {
        _state.update { it.copy(showPaymentSheet = false) }
    }

    fun openReportIssueDialog() {
        _state.update { it.copy(showReportIssueDialog = true) }
    }

    fun closeReportIssueDialog() {
        _state.update { it.copy(showReportIssueDialog = false) }
    }

    fun openSelfHelpDialog() {
        _state.update { it.copy(showSelfHelpDialog = true) }
    }

    fun closeSelfHelpDialog() {
        _state.update { it.copy(showSelfHelpDialog = false) }
    }

    fun openSpeedTest() {
        _state.update { it.copy(showSpeedTestScreen = true) }
    }

    fun closeSpeedTest() {
        _state.update { it.copy(showSpeedTestScreen = false) }
    }

    fun startConnectionTest() {
        _state.update {
            it.copy(
                showConnectionTestDialog = true,
                isTestingConnection = true,
                connectionTestResult = null
            )
        }
        viewModelScope.launch {
            val currentCustomer = (_state.value.uiState as? BillingUiState.Success)?.bill?.customer
            val targetCustId = currentCustomer?.customerCode?.ifBlank { currentCustomer.id.toString() } ?: _state.value.query.trim()
            val targetIp = currentCustomer?.ipOrPppoe ?: ""

            val result = repository.pingCustomerRouter(customerId = targetCustId, ip = targetIp)
            _state.update {
                it.copy(
                    isTestingConnection = false,
                    connectionTestResult = Pair(result.first, result.second)
                )
            }
        }
    }

    fun closeConnectionTest() {
        _state.update { it.copy(showConnectionTestDialog = false) }
    }

    fun copyBillSummary() {
        val currentBill = (_state.value.uiState as? BillingUiState.Success)?.bill ?: return
        val totalAmount = if (currentBill.isPaid) {
            currentBill.paidTransaction?.totalAmount ?: currentBill.packageFee
        } else {
            currentBill.totalDue
        }
        val codeText = currentBill.customer.customerCode.ifBlank { currentBill.customer.id.toString() }
        val summary = """
            Tagihan Netplus:
            - Kode Pelanggan: $codeText
            - Nama: ${currentBill.customer.name}
            - Paket: ${currentBill.customer.packageName}
            - Periode: ${currentBill.currentPeriod}
            - Status: ${if (currentBill.isPaid) "LUNAS" else "BELUM LUNAS"}
            - Total: ${com.netplus.util.FormatUtils.formatRupiah(totalAmount)}
        """.trimIndent()

        val clipboard = getApplication<Application>().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Tagihan Netplus", summary))
        Toast.makeText(getApplication(), "Rincian tagihan berhasil disalin!", Toast.LENGTH_SHORT).show()
    }

    fun openSecuritySettings() {
        _state.update { it.copy(showSecuritySettingsDialog = true) }
    }

    fun closeSecuritySettings() {
        _state.update { it.copy(showSecuritySettingsDialog = false) }
    }

    fun saveRememberPreference(enableBiometric: Boolean, enableAutoLogin: Boolean) {
        if (!enableBiometric) {
            clearRememberPreference()
            Toast.makeText(getApplication(), "Akun dihapus dari perangkat karena sidik jari dinonaktifkan.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentCustomer = (_state.value.uiState as? BillingUiState.Success)?.bill?.customer
        val codeToSave = currentCustomer?.customerCode?.ifBlank { currentCustomer.id.toString() }
            ?: _state.value.query
        val nameToSave = currentCustomer?.name ?: "Pelanggan Netplus"

        repository.saveRememberedCustomer(codeToSave, nameToSave, enableBiometric = true, enableAutoLogin = enableAutoLogin)
        _state.update {
            it.copy(
                rememberedId = codeToSave,
                rememberedName = nameToSave,
                isBiometricEnabled = true,
                isAutoLoginEnabled = enableAutoLogin,
                showSecuritySettingsDialog = false
            )
        }
        Toast.makeText(getApplication(), "Akun berhasil disimpan & wajib verifikasi Sidik Jari!", Toast.LENGTH_SHORT).show()
    }

    fun clearRememberPreference() {
        repository.clearRememberedCustomer()
        _state.update {
            it.copy(
                rememberedId = "",
                rememberedName = "",
                isBiometricEnabled = false,
                isAutoLoginEnabled = false,
                showSecuritySettingsDialog = false
            )
        }
        Toast.makeText(getApplication(), "Akun telah dihapus dari perangkat ini.", Toast.LENGTH_SHORT).show()
    }

    fun triggerQuickLoginWithBiometric(activity: androidx.fragment.app.FragmentActivity) {
        val targetId = _state.value.rememberedId.ifBlank { _state.value.query }
        if (targetId.isBlank()) return

        if (com.netplus.util.BiometricHelper.isBiometricAvailable(activity)) {
            com.netplus.util.BiometricHelper.authenticate(
                activity = activity,
                title = "Autentikasi Sidik Jari",
                subtitle = "Verifikasi sidik jari untuk membuka akun Kode: $targetId",
                onSuccess = {
                    checkBill(targetId)
                },
                onError = { err ->
                    Toast.makeText(getApplication(), "Verifikasi gagal: $err", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Toast.makeText(getApplication(), "Akses akun tersimpan wajib sidik jari. Sensor tidak tersedia.", Toast.LENGTH_LONG).show()
        }
    }

    fun performAutoLoginIfEligible(activity: androidx.fragment.app.FragmentActivity) {
        if (hasCheckedAutoLogin) return
        hasCheckedAutoLogin = true

        val rememberedId = repository.getRememberedId()
        if (rememberedId.isNotBlank() && repository.isAutoLoginEnabled() && repository.isBiometricEnabled()) {
            if (com.netplus.util.BiometricHelper.isBiometricAvailable(activity)) {
                com.netplus.util.BiometricHelper.authenticate(
                    activity = activity,
                    title = "Autentikasi Akun Netplus",
                    subtitle = "Sentuh sensor sidik jari untuk login otomatis Kode: $rememberedId",
                    negativeButtonText = "Batal",
                    onSuccess = {
                        checkBill(rememberedId)
                    },
                    onError = {
                        // Tetap di halaman pencarian jika dibatalkan
                    }
                )
            }
        }
    }

    fun dismissPromoPopup() {
        _state.update { it.copy(activePromoPopup = null) }
    }

    private fun checkAndTriggerPromoPopup() {
        val currentState = _state.value
        if (currentState.hasShownPromoThisSession) return
        if (currentState.uiState !is BillingUiState.Success) return

        val promoItem = currentState.announcements.firstOrNull {
            it.status.equals("ACTIVE", ignoreCase = true) &&
            it.category.equals("PROMO", ignoreCase = true)
        }
        if (promoItem != null) {
            _state.update {
                it.copy(
                    activePromoPopup = promoItem,
                    hasShownPromoThisSession = true
                )
            }
        }
    }

    fun resetToSearch() {
        _state.update {
            it.copy(
                uiState = BillingUiState.Idle,
                activePromoPopup = null,
                hasShownPromoThisSession = false
            )
        }
    }
}
