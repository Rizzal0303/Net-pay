package com.netplus.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.netplus.data.model.BillCheckResponse
import com.netplus.data.remote.ApiClient
import com.netplus.data.remote.BillingApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class BillingRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("billingku_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SAVED_IDS = "saved_customer_ids"
        private const val KEY_REMEMBERED_ID = "remembered_id"
        private const val KEY_REMEMBERED_NAME = "remembered_name"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTO_LOGIN = "auto_login"
        private const val KEY_ADMIN_WHATSAPP = "admin_whatsapp_number"
    }

    fun getAdminWhatsapp(): String {
        val stored = prefs.getString(KEY_ADMIN_WHATSAPP, null)
        return com.netplus.util.AdminContactHelper.normalizeWhatsappNumber(stored)
    }

    fun saveAdminWhatsapp(phone: String?) {
        if (!phone.isNullOrBlank()) {
            val normalized = com.netplus.util.AdminContactHelper.normalizeWhatsappNumber(phone)
            prefs.edit().putString(KEY_ADMIN_WHATSAPP, normalized).apply()
        }
    }

    fun isAutoLoginEnabled(): Boolean = isBiometricEnabled() && prefs.getBoolean(KEY_AUTO_LOGIN, false)
    fun setAutoLoginEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_LOGIN, enabled).apply()

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    fun setBiometricEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()

    fun getRememberedId(): String {
        // Akun tersimpan hanya sah jika dilindungi oleh sidik jari
        if (!isBiometricEnabled()) return ""
        return prefs.getString(KEY_REMEMBERED_ID, "") ?: ""
    }

    fun getRememberedName(): String {
        if (!isBiometricEnabled()) return ""
        return prefs.getString(KEY_REMEMBERED_NAME, "") ?: ""
    }

    fun saveRememberedCustomer(id: String, name: String, enableBiometric: Boolean, enableAutoLogin: Boolean) {
        if (!enableBiometric) {
            // Hapus jika tanpa sidik jari
            clearRememberedCustomer()
            return
        }
        prefs.edit()
            .putString(KEY_REMEMBERED_ID, id)
            .putString(KEY_REMEMBERED_NAME, name)
            .putBoolean(KEY_BIOMETRIC_ENABLED, true)
            .putBoolean(KEY_AUTO_LOGIN, enableAutoLogin)
            .apply()
    }

    fun clearRememberedCustomer() {
        prefs.edit()
            .remove(KEY_REMEMBERED_ID)
            .remove(KEY_REMEMBERED_NAME)
            .remove(KEY_BIOMETRIC_ENABLED)
            .remove(KEY_AUTO_LOGIN)
            .apply()
    }

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, ApiClient.DEFAULT_BASE_URL) ?: ApiClient.DEFAULT_BASE_URL
        set(value) {
            prefs.edit().putString(KEY_BASE_URL, value).apply()
            apiService = ApiClient.createService(value)
        }

    private var apiService: BillingApiService = ApiClient.createService(baseUrl)

    suspend fun pingCustomerRouter(customerId: String, ip: String?): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.pingCustomerIp(
                id = customerId.trim(),
                customerId = customerId.trim(),
                ip = ip?.trim()
            )

            if (response.isSuccessful && response.body() != null) {
                val pingBody = response.body()!!
                val isOnline = pingBody.success && (pingBody.status?.equals("ONLINE", ignoreCase = true) != false)
                val latency = pingBody.latencyMs
                val packetLoss = pingBody.packetLoss ?: if (isOnline) 0 else 100

                if (isOnline) {
                    val latencyText = if (latency != null) "$latency ms" else "< 10 ms"
                    val quality = when {
                        latency == null || latency < 15 -> "Sangat Cepat & Stabil"
                        latency < 45 -> "Bagus & Stabil"
                        latency < 90 -> "Cukup Baik"
                        else -> "Tinggi"
                    }
                    val msg = buildString {
                        append("• Jalur Uji: Server Netplus ➔ Modem Pelanggan\n")
                        append("• Respon Modem: $latencyText ($quality)\n")
                        append("• Packet Loss: $packetLoss%\n")
                        append("• Status: Terhubung Normal\n")
                        if (!pingBody.message.isNullOrBlank()) {
                            append("• Keterangan Server: ${pingBody.message}")
                        }
                    }
                    Pair(true, msg.trim())
                } else {
                    val msg = buildString {
                        append("• Jalur Uji: Server Netplus ➔ Modem Pelanggan\n")
                        append("• Respon: Request Time Out (RTO)\n")
                        append("• Packet Loss: 100%\n")
                        append("• Status: Modem Tidak Menjawab\n")
                        val serverMsg = pingBody.message ?: "Server tidak menerima respon balik dari modem Anda."
                        append("• Keterangan: $serverMsg")
                    }
                    Pair(false, msg.trim())
                }
            } else if (response.code() == 404) {
                // api_ping_ip.php belum ditaruh di hosting
                Pair(
                    false,
                    "Endpoint api_ping_ip.php belum tersedia di server.\nSilakan letakkan file api_ping_ip.php di hosting agar server Netplus dapat mengeksekusi ping langsung ke modem pelanggan."
                )
            } else {
                Pair(false, "Server penguji merespons kode HTTP ${response.code()}. Silakan coba beberapa saat lagi.")
            }
        } catch (e: Exception) {
            Pair(false, "Gagal menghubungi server penguji: ${e.localizedMessage ?: "Koneksi terputus."}")
        }
    }

    suspend fun testServerConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.ping()
            if (response.isSuccessful || response.code() != 404) {
                Pair(true, "Terhubung ke server (${baseUrl})")
            } else {
                Pair(false, "Server merespons kode HTTP: ${response.code()}")
            }
        } catch (e: Exception) {
            Pair(false, "Gagal terhubung ke server: ${e.localizedMessage ?: e.message}")
        }
    }

    suspend fun checkBill(idInput: String): Result<BillCheckResponse> = withContext(Dispatchers.IO) {
        val trimmedInput = idInput.trim()
        if (trimmedInput.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Silakan masukkan Kode Pelanggan Anda."))
        }

        try {
            val response = apiService.checkBill(id = trimmedInput, customerId = trimmedInput)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    // Simpan ke riwayat pencarian
                    val codeToSave = body.customer.customerCode.ifBlank { trimmedInput }
                    saveRecentId(codeToSave)
                    saveAdminWhatsapp(body.phone ?: body.adminPhone ?: body.companyPhone)
                    return@withContext Result.success(body)
                } else if (body != null && !body.message.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception(body.message))
                }
            }

            val errorBodyStr = response.errorBody()?.string()
            if (!errorBodyStr.isNullOrEmpty()) {
                val parsedMsg = parseErrorMessage(errorBodyStr, trimmedInput)
                return@withContext Result.failure(Exception(parsedMsg))
            }

            if (response.code() == 404) {
                return@withContext Result.failure(
                    Exception("Kode Pelanggan '$trimmedInput' tidak ditemukan di database server.")
                )
            }

            return@withContext Result.failure(
                Exception("Gagal mengambil tagihan (HTTP ${response.code()}) dari server.")
            )
        } catch (e: Exception) {
            return@withContext Result.failure(
                Exception("Gagal terhubung ke https://billing.gonetplus.web.id/: ${e.localizedMessage ?: e.message}")
            )
        }
    }

    suspend fun getMidtransPaymentUrl(customerId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getMidtransPayUrl(customerId.trim())
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    val rawUrl = body.redirectUrl ?: if (!body.token.isNullOrEmpty()) {
                        "https://app.sandbox.midtrans.com/snap/v4/redirection/${body.token.trim()}"
                    } else null
                    val url = rawUrl?.trim()?.replace("\\/", "/")

                    if (!url.isNullOrEmpty()) {
                        android.util.Log.d("BillingRepo", "Midtrans Payment URL obtained: $url")
                        return@withContext Result.success(url)
                    }
                }
                return@withContext Result.failure(
                    Exception(body?.message ?: "Gagal mendapatkan tautan pembayaran Midtrans.")
                )
            }

            val err = response.errorBody()?.string()
            val parsedMsg = if (!err.isNullOrEmpty()) parseErrorMessage(err, customerId) else "Server error (${response.code()})"
            Result.failure(Exception(parsedMsg))
        } catch (e: Exception) {
            Result.failure(Exception("Gagal menghubungi gateway Midtrans: ${e.localizedMessage ?: e.message}"))
        }
    }

    private fun parseErrorMessage(jsonStr: String, idSearched: String): String {
        return try {
            val json = JSONObject(jsonStr)
            json.optString("message", "Terjadi kesalahan pada server untuk kode '$idSearched'.")
        } catch (e: Exception) {
            "Kode Pelanggan '$idSearched' tidak terdaftar dalam database server."
        }
    }

    fun getSavedIds(): List<String> {
        val raw = prefs.getString(KEY_SAVED_IDS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun saveRecentId(id: String) {
        val existing = getSavedIds().toMutableList()
        existing.remove(id)
        existing.add(0, id)
        if (existing.size > 5) {
            existing.removeAt(existing.lastIndex)
        }
        prefs.edit().putString(KEY_SAVED_IDS, existing.joinToString(",")).apply()
    }

    fun removeSavedId(id: String) {
        val existing = getSavedIds().toMutableList()
        existing.remove(id)
        prefs.edit().putString(KEY_SAVED_IDS, existing.joinToString(",")).apply()
    }

    suspend fun getAnnouncements(scope: String? = null): Result<List<com.netplus.data.model.Announcement>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getAnnouncements(status = "ACTIVE", scope = scope)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.status.equals("success", ignoreCase = true)) {
                    saveAdminWhatsapp(body.phone ?: body.adminPhone)
                    return@withContext Result.success(body.data)
                }
            }
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAdminContact(): String = withContext(Dispatchers.IO) {
        // 1. Coba ambil dari billingku.php?action=get_admin_contact
        try {
            val response = apiService.getAdminContact(apiKey = "billingku_secret")
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val data = body["data"] as? Map<*, *>
                val phone = (data?.get("phone") ?: data?.get("admin_phone") ?: body["phone"] ?: body["adminPhone"])?.toString()
                if (!phone.isNullOrBlank()) {
                    saveAdminWhatsapp(phone)
                    return@withContext getAdminWhatsapp()
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback: ambil dari api_check_bill.php?action=get_admin_contact
        try {
            val fallbackResponse = apiService.getAdminContactFallback(apiKey = "billingku_secret")
            if (fallbackResponse.isSuccessful && fallbackResponse.body() != null) {
                val body = fallbackResponse.body()!!
                val data = body["data"] as? Map<*, *>
                val phone = (data?.get("phone") ?: data?.get("admin_phone") ?: body["phone"] ?: body["adminPhone"])?.toString()
                if (!phone.isNullOrBlank()) {
                    saveAdminWhatsapp(phone)
                    return@withContext getAdminWhatsapp()
                }
            }
        } catch (_: Exception) {}

        getAdminWhatsapp()
    }
}
