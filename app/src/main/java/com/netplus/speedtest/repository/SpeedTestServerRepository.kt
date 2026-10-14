package com.netplus.speedtest.repository

import android.content.Context
import com.netplus.speedtest.model.LibreSpeedServer
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SpeedTestServerRepository(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("librespeed_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val netplusDefaultServer = LibreSpeedServer(
        id = 1,
        name = "Data Buana Nusantara by Netplus",
        server = "https://speedtest.gonetplus.web.id/backend",
        candidateEndpoints = listOf(
            "http://192.168.88.2:82/backend",
            "https://speedtest.gonetplus.web.id/backend"
        ),
        dlURL = "garbage.php",
        ulURL = "empty.php",
        pingURL = "empty.php",
        getIpURL = "getIP.php",
        telemetryURL = "results/telemetry.php",
        sponsorName = "Netplus",
        sponsorURL = "https://gonetplus.web.id",
        isDefault = true
    )

    val defaultServers = listOf(
        netplusDefaultServer,
        LibreSpeedServer(
            id = 82,
            name = "Tokyo, Japan (A573)",
            server = "https://librespeed.a573.net",
            dlURL = "backend/garbage.php",
            ulURL = "backend/empty.php",
            pingURL = "backend/empty.php",
            getIpURL = "backend/getIP.php",
            sponsorName = "A573"
        ),
        LibreSpeedServer(
            id = 51,
            name = "Amsterdam, Netherlands (Clouvider)",
            server = "https://ams.speedtest.clouvider.net/backend",
            dlURL = "garbage.php",
            ulURL = "empty.php",
            pingURL = "empty.php",
            getIpURL = "getIP.php",
            sponsorName = "Clouvider"
        )
    )

    suspend fun fetchServers(): List<LibreSpeedServer> = withContext(Dispatchers.IO) {
        val customServers = loadCustomServers()
        try {
            val request = Request.Builder()
                .url("https://librespeed.org/backend-servers/servers.php")
                .header("User-Agent", "LibreSpeed-Android/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string()
                if (!json.isNullOrBlank()) {
                    val listType = Types.newParameterizedType(List::class.java, LibreSpeedServer::class.java)
                    val adapter = moshi.adapter<List<LibreSpeedServer>>(listType)
                    val fetched = adapter.fromJson(json)
                    if (!fetched.isNullOrEmpty()) {
                        return@withContext listOf(netplusDefaultServer) + customServers + fetched.filter {
                            it.server != netplusDefaultServer.server
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        listOf(netplusDefaultServer) + customServers + defaultServers.filter { it.id != netplusDefaultServer.id }
    }

    private fun loadCustomServers(): List<LibreSpeedServer> {
        val json = sharedPrefs.getString("custom_servers", null) ?: return emptyList()
        return try {
            val listType = Types.newParameterizedType(List::class.java, LibreSpeedServer::class.java)
            val adapter = moshi.adapter<List<LibreSpeedServer>>(listType)
            adapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getSelectedServer(): LibreSpeedServer {
        val savedServerUrl = sharedPrefs.getString("selected_server_url", null)
        if (savedServerUrl != null) {
            val all = listOf(netplusDefaultServer) + loadCustomServers() + defaultServers
            val found = all.find { it.server == savedServerUrl || it.candidateEndpoints.contains(savedServerUrl) }
            if (found != null) return found
        }
        return netplusDefaultServer
    }

    fun saveSelectedServer(server: LibreSpeedServer) {
        sharedPrefs.edit().putString("selected_server_url", server.server).apply()
    }
}
