package com.netplus.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    private val localeId = Locale("id", "ID")
    private val rupiahFormat = NumberFormat.getCurrencyInstance(localeId).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    fun formatRupiah(amount: Long): String {
        return try {
            val formatted = rupiahFormat.format(amount)
            formatted.replace("Rp", "Rp ")
        } catch (e: Exception) {
            "Rp $amount"
        }
    }

    fun formatDateTime(timestamp: Long): String {
        if (timestamp <= 0) return "-"
        return try {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", localeId)
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "-"
        }
    }

    fun formatDateOnly(timestamp: Long): String {
        if (timestamp <= 0) return "-"
        return try {
            val sdf = SimpleDateFormat("dd MMMM yyyy", localeId)
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "-"
        }
    }
}
