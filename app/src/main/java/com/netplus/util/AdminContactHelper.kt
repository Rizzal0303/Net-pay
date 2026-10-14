package com.netplus.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Helper terpusat untuk mengelola nomor kontak WhatsApp Admin Netplus
 * dengan sinkronisasi dinamis dari database dan fallback aman.
 */
object AdminContactHelper {
    // Nomor WhatsApp cadangan resmi (fallback) jika database offline atau belum memuat kontak
    const val DEFAULT_WHATSAPP = "6281584210833"

    /**
     * Membersihkan dan menstandarisasi nomor HP Indonesia ke format internasional WhatsApp (62xxx)
     */
    fun normalizeWhatsappNumber(raw: String?): String {
        if (raw.isNullOrBlank()) return DEFAULT_WHATSAPP
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("62") -> digits
            digits.startsWith("0") -> "62" + digits.substring(1)
            digits.startsWith("8") -> "62" + digits
            digits.length >= 8 -> digits
            else -> DEFAULT_WHATSAPP
        }
    }

    /**
     * Membentuk format tampilan nomor yang rapi untuk ditampilkan di UI (misal: +62 812-3456-7890)
     */
    fun formatDisplayPhone(raw: String?): String {
        val normalized = normalizeWhatsappNumber(raw)
        return if (normalized.startsWith("62") && normalized.length >= 10) {
            val part1 = normalized.substring(0, 2)
            val part2 = normalized.substring(2, 5)
            val part3 = if (normalized.length >= 9) normalized.substring(5, 9) else normalized.substring(5)
            val part4 = if (normalized.length > 9) "-" + normalized.substring(9) else ""
            "+$part1 $part2-$part3$part4"
        } else {
            "+$normalized"
        }
    }

    /**
     * Membentuk URL link WhatsApp resmi
     */
    fun getWhatsappUrl(targetPhone: String? = null, message: String = ""): String {
        val phone = normalizeWhatsappNumber(targetPhone)
        val encodedMsg = Uri.encode(message)
        return "https://api.whatsapp.com/send?phone=$phone&text=$encodedMsg"
    }

    /**
     * Membuka aplikasi WhatsApp langsung dengan nomor tujuan dan pesan terformat
     */
    fun openWhatsapp(context: Context, targetPhone: String? = null, message: String = "") {
        try {
            val url = getWhatsappUrl(targetPhone, message)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
