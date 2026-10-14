package com.netplus.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Customer(
    @Json(name = "id") val id: Long,
    @Json(name = "customerCode") val customerCode: String,
    @Json(name = "name") val name: String,
    @Json(name = "phone") val phone: String? = "",
    @Json(name = "address") val address: String? = "",
    @Json(name = "odpLocation") val odpLocation: String? = "",
    @Json(name = "packageId") val packageId: Long? = 0L,
    @Json(name = "packageName") val packageName: String? = "",
    @Json(name = "monthlyFee") val monthlyFee: Long = 0L,
    @Json(name = "dueDay") val dueDay: Int = 10,
    @Json(name = "ipOrPppoe") val ipOrPppoe: String? = "",
    @Json(name = "status") val status: String = "ACTIVE",
    @Json(name = "createdAt") val createdAt: Long? = 0L,
    @Json(name = "updatedAt") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Transaction(
    @Json(name = "id") val id: Long,
    @Json(name = "invoiceNumber") val invoiceNumber: String,
    @Json(name = "customerId") val customerId: Long,
    @Json(name = "customerName") val customerName: String,
    @Json(name = "customerCode") val customerCode: String,
    @Json(name = "packageName") val packageName: String? = "",
    @Json(name = "monthlyFee") val monthlyFee: Long = 0L,
    @Json(name = "adminFee") val adminFee: Long = 0L,
    @Json(name = "discount") val discount: Long = 0L,
    @Json(name = "totalAmount") val totalAmount: Long = 0L,
    @Json(name = "periodMonth") val periodMonth: String,
    @Json(name = "paymentMethod") val paymentMethod: String = "TUNAI",
    @Json(name = "cashierName") val cashierName: String? = "",
    @Json(name = "cashierRole") val cashierRole: String? = "",
    @Json(name = "notes") val notes: String? = "",
    @Json(name = "paidAt") val paidAt: Long = 0L,
    @Json(name = "isPrinted") val isPrinted: Int = 0,
    @Json(name = "isDeposited") val isDeposited: Int = 0,
    @Json(name = "settlementBatchId") val settlementBatchId: Long? = null,
    @Json(name = "settlementBatchNumber") val settlementBatchNumber: String? = null,
    @Json(name = "depositedAt") val depositedAt: Long? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class BillCheckResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "message") val message: String? = null,
    @Json(name = "customer") val customer: Customer,
    @Json(name = "currentPeriod") val currentPeriod: String,
    @Json(name = "isPaid") val isPaid: Boolean,
    @Json(name = "paidTransaction") val paidTransaction: Transaction? = null,
    @Json(name = "dueDate") val dueDate: String? = null,
    @Json(name = "packageFee") val packageFee: Long = 0L,
    @Json(name = "adminFee") val adminFee: Long = 0L,
    @Json(name = "discount") val discount: Long = 0L,
    @Json(name = "totalDue") val totalDue: Long = 0L,
    @Json(name = "unpaidMonthsCount") val unpaidMonthsCount: Int = 0,
    @Json(name = "unpaidPeriods") val unpaidPeriods: List<String> = emptyList(),
    @Json(name = "statusTagihan") val statusTagihan: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "adminPhone") val adminPhone: String? = null,
    @Json(name = "companyPhone") val companyPhone: String? = null,
    @Json(name = "history") val history: List<Transaction> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MidtransTokenResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "token") val token: String? = null,
    @Json(name = "redirect_url") val redirectUrl: String? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: T? = null
)

@JsonClass(generateAdapter = true)
data class PingResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "message") val message: String? = null,
    @Json(name = "status") val status: String? = "ONLINE",
    @Json(name = "latency_ms") val latencyMs: Int? = null,
    @Json(name = "packet_loss") val packetLoss: Int? = 0,
    @Json(name = "ip") val ip: String? = null,
    @Json(name = "route") val route: String? = "Server Netplus -> Modem Pelanggan"
)
