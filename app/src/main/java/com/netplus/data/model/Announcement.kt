package com.netplus.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Announcement(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "title") val title: String = "",
    @Json(name = "category") val category: String = "INFO", // "GANGGUAN", "MAINTENANCE", "INFO", "PROMO"
    @Json(name = "message") val message: String = "",
    @Json(name = "targetScope") val targetScope: String = "SEMUA",
    @Json(name = "status") val status: String = "ACTIVE",
    @Json(name = "estimatedEnd") val estimatedEnd: String? = null,
    @Json(name = "createdBy") val createdBy: String? = "Admin",
    @Json(name = "createdAt") val createdAt: Long? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AnnouncementApiResponse(
    @Json(name = "status") val status: String = "",
    @Json(name = "code") val code: Int = 200,
    @Json(name = "count") val count: Int = 0,
    @Json(name = "message") val message: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "adminPhone") val adminPhone: String? = null,
    @Json(name = "data") val data: List<Announcement> = emptyList()
)
