package com.netplus.data.remote

import com.netplus.data.model.ApiResponse
import com.netplus.data.model.AnnouncementApiResponse
import com.netplus.data.model.BillCheckResponse
import com.netplus.data.model.MidtransTokenResponse
import com.netplus.data.model.PingResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface BillingApiService {

    // Primary bill check endpoint
    @GET("api_check_bill.php")
    suspend fun checkBill(
        @Query("id") id: String,
        @Query("customerId") customerId: String? = null
    ): Response<BillCheckResponse>

    // Midtrans Snap token generation endpoint
    @GET("api_midtrans_pay.php")
    suspend fun getMidtransPayUrl(
        @Query("id") id: String
    ): Response<MidtransTokenResponse>

    // Dedicated Ping Router IP endpoint (api_ping_ip.php)
    @GET("api_ping_ip.php")
    suspend fun pingCustomerIp(
        @Query("id") id: String? = null,
        @Query("customerId") customerId: String? = null,
        @Query("ip") ip: String? = null
    ): Response<PingResponse>

    // Announcements & Broadcast Notifications from billingku
    @GET("billingku.php?action=get_announcements")
    suspend fun getAnnouncements(
        @Query("status") status: String = "ACTIVE",
        @Query("scope") scope: String? = null
    ): Response<AnnouncementApiResponse>

    // Dynamic Admin Contact info from billingku
    @GET("billingku.php?action=get_admin_contact")
    suspend fun getAdminContact(
        @Query("api_key") apiKey: String = "billingku_secret"
    ): Response<Map<String, Any?>>

    // Dynamic Admin Contact fallback from api_check_bill.php
    @GET("api_check_bill.php?action=get_admin_contact")
    suspend fun getAdminContactFallback(
        @Query("api_key") apiKey: String = "billingku_secret"
    ): Response<Map<String, Any?>>

    // Fallback Ping / Health check
    @GET("api_check_bill.php")
    suspend fun ping(
        @Query("ping") ping: String = "1"
    ): Response<Map<String, Any?>>
}

object ApiClient {
    const val DEFAULT_BASE_URL = "https://billing.gonetplus.web.id/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private fun getOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun createService(baseUrl: String = DEFAULT_BASE_URL): BillingApiService {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(getOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BillingApiService::class.java)
    }
}
