package com.netplus.speedtest.engine

import com.netplus.speedtest.model.LibreSpeedServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

class LibreSpeedEngine {

    private val connectionPool = ConnectionPool(16, 5, TimeUnit.MINUTES)

    private val httpClient = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val fastClient = httpClient.newBuilder()
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .readTimeout(1200, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Automatically tests candidate endpoints (e.g. local LAN vs public WAN) in parallel
     * using a quick 1200ms timeout to avoid long delays.
     */
    suspend fun resolveFastestEndpoint(server: LibreSpeedServer): LibreSpeedServer = withContext(Dispatchers.IO) {
        if (server.candidateEndpoints.isEmpty()) {
            return@withContext server.copy(activeEndpoint = server.server)
        }
        if (server.candidateEndpoints.size == 1) {
            return@withContext server.copy(activeEndpoint = server.candidateEndpoints.first())
        }

        val results = coroutineScope {
            server.candidateEndpoints.map { endpoint ->
                async {
                    val trimmed = endpoint.trim().trimEnd('/')
                    val pingEndpoint = server.pingURL.trim().trimStart('/')
                    val separator = if (pingEndpoint.contains("?")) "&" else "?"
                    val target = "$trimmed/$pingEndpoint${separator}cors=true&r=${System.currentTimeMillis()}"
                    val startNano = System.nanoTime()
                    try {
                        val request = Request.Builder()
                            .url(target)
                            .header("Cache-Control", "no-cache")
                            .build()
                        fastClient.newCall(request).execute().use { response ->
                            response.body?.bytes()
                            if (response.isSuccessful) {
                                val elapsedMs = (System.nanoTime() - startNano) / 1_000_000f
                                return@async Pair(endpoint, elapsedMs)
                            }
                        }
                    } catch (_: Exception) {
                    }
                    Pair(endpoint, Float.MAX_VALUE)
                }
            }.awaitAll()
        }

        val reachable = results.filter { it.second < Float.MAX_VALUE }
        val winner = if (reachable.isNotEmpty()) {
            reachable.minByOrNull { it.second }?.first ?: server.candidateEndpoints.last()
        } else {
            // Default to the public server URL if local LAN is unreachable
            server.candidateEndpoints.lastOrNull { !it.contains("192.168.") } ?: server.candidateEndpoints.first()
        }

        server.copy(activeEndpoint = winner)
    }

    /**
     * Measure ping and jitter by performing fast keep-alive requests.
     */
    suspend fun measurePingAndJitter(
        server: LibreSpeedServer,
        pingCount: Int = 6,
        onProgress: (currentPingMs: Float, progressFraction: Float) -> Unit
    ): Pair<Float, Float> = withContext(Dispatchers.IO) {
        val pings = mutableListOf<Float>()
        val pingUrl = server.resolveUrl(server.pingURL)
        val separator = if (pingUrl.contains("?")) "&" else "?"

        // Warm-up ping to establish TCP/TLS connection
        try {
            val warmupTarget = "$pingUrl${separator}cors=true&r=warmup_${System.currentTimeMillis()}"
            val warmupReq = Request.Builder()
                .url(warmupTarget)
                .header("Connection", "keep-alive")
                .header("Cache-Control", "no-cache")
                .build()
            httpClient.newCall(warmupReq).execute().use { res ->
                res.body?.bytes()
            }
        } catch (_: Exception) {
        }

        for (i in 0 until pingCount) {
            if (!isActive) break
            val target = "$pingUrl${separator}cors=true&r=${System.currentTimeMillis()}_$i"
            val startNano = System.nanoTime()
            try {
                val request = Request.Builder()
                    .url(target)
                    .header("Connection", "keep-alive")
                    .header("Cache-Control", "no-cache")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    response.body?.bytes()
                    val elapsedMs = (System.nanoTime() - startNano) / 1_000_000f
                    if (response.isSuccessful && elapsedMs > 0f) {
                        pings.add(elapsedMs)
                        onProgress(elapsedMs, (i + 1).toFloat() / pingCount)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            delay(30)
        }

        if (pings.isEmpty()) {
            return@withContext Pair(0f, 0f)
        }

        pings.sort()
        val bestPing = pings.first()

        var jitterSum = 0f
        var jitterCount = 0
        for (i in 1 until pings.size) {
            jitterSum += abs(pings[i] - pings[i - 1])
            jitterCount++
        }
        val jitter = if (jitterCount > 0) jitterSum / jitterCount else 0f

        Pair(bestPing, jitter)
    }

    /**
     * Measure download speed using parallel streams with 800ms rolling window and EMA smoothing.
     * Duration: 6.5s for fast and responsive mobile testing.
     */
    suspend fun measureDownloadSpeed(
        server: LibreSpeedServer,
        durationMs: Long = 6500L,
        streamsCount: Int = 3,
        onProgress: (currentSpeedMbps: Float, fraction: Float) -> Unit
    ): Float = withContext(Dispatchers.IO) {
        val totalBytes = AtomicLong(0L)
        val isRunning = AtomicBoolean(true)
        val startTime = System.currentTimeMillis()
        val speedSamples = mutableListOf<Float>()
        val dlUrl = server.resolveUrl(server.dlURL)
        val separator = if (dlUrl.contains("?")) "&" else "?"

        coroutineScope {
            // Reporter job: 80ms interval, 800ms window, EMA smoothing
            val reporterJob = async {
                val graceTimeMs = 1000L
                var smoothedSpeed = 0f
                val windowSamples = ArrayDeque<Pair<Long, Long>>() // (timeMs, bytes)

                while (isRunning.get() && isActive) {
                    delay(80)
                    val now = System.currentTimeMillis()
                    val currentBytes = totalBytes.get()
                    val totalElapsed = (now - startTime).coerceAtLeast(1)
                    val fraction = (totalElapsed.toFloat() / durationMs).coerceIn(0f, 1f)

                    windowSamples.addLast(Pair(now, currentBytes))
                    while (windowSamples.size > 1 && (now - windowSamples.first().first > 800L)) {
                        windowSamples.removeFirst()
                    }

                    if (windowSamples.size >= 2) {
                        val oldest = windowSamples.first()
                        val timeDeltaSec = (now - oldest.first) / 1000f
                        val bytesDelta = currentBytes - oldest.second
                        if (timeDeltaSec > 0.12f && bytesDelta >= 0) {
                            val instantRate = (bytesDelta * 8f) / (timeDeltaSec * 1_000_000f)
                            smoothedSpeed = if (smoothedSpeed <= 0.1f) {
                                instantRate
                            } else {
                                smoothedSpeed * 0.70f + instantRate * 0.30f
                            }
                            if (totalElapsed > graceTimeMs) {
                                speedSamples.add(smoothedSpeed)
                            }
                            onProgress(smoothedSpeed, fraction)
                        }
                    }

                    if (now - startTime >= durationMs) {
                        isRunning.set(false)
                        break
                    }
                }
            }

            // Parallel downloader stream workers with staggered starts
            val workers = (0 until streamsCount).map { streamIndex ->
                async {
                    delay(streamIndex * 150L)
                    val buffer = ByteArray(32768)
                    while (isRunning.get() && isActive) {
                        val target = "$dlUrl${separator}ckSize=20&cors=true&r=${System.currentTimeMillis()}_$streamIndex"
                        try {
                            val request = Request.Builder()
                                .url(target)
                                .header("Cache-Control", "no-cache, no-store")
                                .build()
                            httpClient.newCall(request).execute().use { response ->
                                val stream: InputStream? = response.body?.byteStream()
                                if (stream != null) {
                                    while (isRunning.get() && isActive) {
                                        val read = stream.read(buffer)
                                        if (read == -1) break
                                        totalBytes.addAndGet(read.toLong())
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            delay(100)
                        }
                    }
                }
            }

            reporterJob.await()
            workers.forEach { it.cancel() }
        }

        val validSamples = if (speedSamples.size > 5) {
            speedSamples.drop((speedSamples.size * 0.2).toInt())
        } else {
            speedSamples
        }

        if (validSamples.isNotEmpty()) {
            validSamples.average().toFloat()
        } else {
            0f
        }
    }

    /**
     * Measure upload speed using parallel 1MB chunked streams with 800ms rolling window and EMA smoothing.
     * Duration: 6.0s for fast and reliable measurement without socket timeout errors.
     */
    suspend fun measureUploadSpeed(
        server: LibreSpeedServer,
        durationMs: Long = 6000L,
        streamsCount: Int = 2,
        onProgress: (currentSpeedMbps: Float, fraction: Float) -> Unit
    ): Float = withContext(Dispatchers.IO) {
        val totalBytes = AtomicLong(0L)
        val isRunning = AtomicBoolean(true)
        val startTime = System.currentTimeMillis()
        val speedSamples = mutableListOf<Float>()
        val ulUrl = server.resolveUrl(server.ulURL)
        val separator = if (ulUrl.contains("?")) "&" else "?"

        coroutineScope {
            // Reporter job: 80ms interval, 800ms window, EMA smoothing
            val reporterJob = async {
                val graceTimeMs = 1000L
                var smoothedSpeed = 0f
                val windowSamples = ArrayDeque<Pair<Long, Long>>() // (timeMs, bytes)

                while (isRunning.get() && isActive) {
                    delay(80)
                    val now = System.currentTimeMillis()
                    val currentBytes = totalBytes.get()
                    val totalElapsed = (now - startTime).coerceAtLeast(1)
                    val fraction = (totalElapsed.toFloat() / durationMs).coerceIn(0f, 1f)

                    windowSamples.addLast(Pair(now, currentBytes))
                    while (windowSamples.size > 1 && (now - windowSamples.first().first > 800L)) {
                        windowSamples.removeFirst()
                    }

                    if (windowSamples.size >= 2) {
                        val oldest = windowSamples.first()
                        val timeDeltaSec = (now - oldest.first) / 1000f
                        val bytesDelta = currentBytes - oldest.second
                        if (timeDeltaSec > 0.12f && bytesDelta >= 0) {
                            val instantRate = (bytesDelta * 8f) / (timeDeltaSec * 1_000_000f)
                            smoothedSpeed = if (smoothedSpeed <= 0.1f) {
                                instantRate
                            } else {
                                smoothedSpeed * 0.70f + instantRate * 0.30f
                            }
                            if (totalElapsed > graceTimeMs) {
                                speedSamples.add(smoothedSpeed)
                            }
                            onProgress(smoothedSpeed, fraction)
                        }
                    }

                    if (now - startTime >= durationMs) {
                        isRunning.set(false)
                        break
                    }
                }
            }

            // Parallel uploader stream workers using safe 1MB chunked uploads
            val workers = (0 until streamsCount).map { streamIndex ->
                async {
                    delay(streamIndex * 200L)
                    val chunkSize = 1024 * 1024L // 1 MB chunk (safe from socket timeouts)
                    while (isRunning.get() && isActive) {
                        val target = "$ulUrl${separator}cors=true&r=${System.currentTimeMillis()}_$streamIndex"
                        try {
                            val requestBody = CountingRequestBody(chunkSize) { writtenInChunk, _ ->
                                if (isRunning.get()) {
                                    totalBytes.addAndGet(writtenInChunk)
                                }
                            }
                            val request = Request.Builder()
                                .url(target)
                                .post(requestBody)
                                .header("Cache-Control", "no-cache, no-store")
                                .build()
                            httpClient.newCall(request).execute().close()
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            delay(100)
                        }
                    }
                }
            }

            reporterJob.await()
            workers.forEach { it.cancel() }
        }

        val validSamples = if (speedSamples.size > 5) {
            speedSamples.drop((speedSamples.size * 0.2).toInt())
        } else {
            speedSamples
        }

        if (validSamples.isNotEmpty()) {
            validSamples.average().toFloat()
        } else {
            0f
        }
    }

    suspend fun fetchClientIp(server: LibreSpeedServer): Pair<String, String> = withContext(Dispatchers.IO) {
        var ip = ""
        var isp = ""

        try {
            val ipUrl = server.resolveUrl(server.getIpURL)
            val separator = if (ipUrl.contains("?")) "&" else "?"
            val target = "$ipUrl${separator}cors=true&isp=true"
            val request = Request.Builder()
                .url(target)
                .header("Cache-Control", "no-cache")
                .build()
            fastClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        val processed = json.optString("processedString", "")
                        if (processed.contains(" - ")) {
                            ip = processed.substringBefore(" - ").trim()
                            isp = processed.substringAfter(" - ").trim()
                        } else if (processed.isNotBlank()) {
                            ip = processed.trim()
                        }
                        val rawIspObj = json.optJSONObject("rawIspInfo")
                        if (rawIspObj != null) {
                            val asName = rawIspObj.optString("as_name", "").trim()
                            val rawIsp = rawIspObj.optString("isp", "").trim()
                            val org = rawIspObj.optString("org", "").trim()
                            when {
                                asName.isNotBlank() -> isp = asName
                                rawIsp.isNotBlank() -> isp = rawIsp
                                org.isNotBlank() -> isp = org
                            }
                        }
                    } else if (body.isNotBlank()) {
                        val trimmed = body.trim()
                        if (trimmed.contains(" - ")) {
                            ip = trimmed.substringBefore(" - ").trim()
                            isp = trimmed.substringAfter(" - ").trim()
                        } else {
                            ip = trimmed
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (isp.isBlank() || ip.isBlank() || ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("127.")) {
            try {
                val fallbackReq = Request.Builder()
                    .url("http://ip-api.com/json")
                    .header("Cache-Control", "no-cache")
                    .build()
                fastClient.newCall(fallbackReq).execute().use { fbRes ->
                    if (fbRes.isSuccessful) {
                        val fbBody = fbRes.body?.string() ?: ""
                        if (fbBody.startsWith("{")) {
                            val fbJson = JSONObject(fbBody)
                            if (ip.isBlank() || ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("127.")) {
                                val queryIp = fbJson.optString("query", "").trim()
                                if (queryIp.isNotBlank()) ip = queryIp
                            }
                            if (isp.isBlank()) {
                                val fbIsp = fbJson.optString("isp", "").trim()
                                val fbOrg = fbJson.optString("org", "").trim()
                                val asInfo = fbJson.optString("as", "").trim()
                                isp = when {
                                    fbIsp.isNotBlank() -> fbIsp
                                    fbOrg.isNotBlank() -> fbOrg
                                    asInfo.isNotBlank() -> asInfo.replace(Regex("^AS\\d+\\s*"), "")
                                    else -> ""
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        Pair(ip, isp)
    }

    data class TelemetryResult(
        val id: String,
        val resultUrl: String,
        val endpointUsed: String
    )

    suspend fun submitTelemetry(
        server: LibreSpeedServer,
        downloadSpeedMbps: Float,
        uploadSpeedMbps: Float,
        pingMs: Float,
        jitterMs: Float,
        clientIp: String,
        clientIsp: String
    ): TelemetryResult? = withContext(Dispatchers.IO) {
        val candidateUrls = server.resolveTelemetryCandidates()
        val ispInfoStr = if (clientIsp.isNotBlank()) {
            if (clientIp.isNotBlank()) "$clientIp - $clientIsp" else clientIsp
        } else {
            clientIp
        }
        val dlStr = String.format(java.util.Locale.US, "%.2f", downloadSpeedMbps)
        val ulStr = String.format(java.util.Locale.US, "%.2f", uploadSpeedMbps)
        val pingStr = String.format(java.util.Locale.US, "%.2f", pingMs)
        val jitterStr = String.format(java.util.Locale.US, "%.2f", jitterMs)

        val formBody = FormBody.Builder()
            .add("dl", dlStr)
            .add("ul", ulStr)
            .add("ping", pingStr)
            .add("jitter", jitterStr)
            .add("ispinfo", ispInfoStr)
            .add("extra", "")
            .add("log", "")
            .build()

        for (url in candidateUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(formBody)
                    .header("Cache-Control", "no-cache")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()?.trim() ?: ""
                        var id: String? = null
                        if (body.startsWith("id ")) {
                            id = body.substringAfter("id ").trim()
                        } else if (body.startsWith("id:")) {
                            id = body.substringAfter("id:").trim()
                        } else if (body.startsWith("{")) {
                            try {
                                val json = JSONObject(body)
                                id = json.optString("id", "").ifBlank {
                                    json.optString("testId", "")
                                }
                            } catch (_: Exception) {
                            }
                        } else if (body.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                            id = body
                        }
                        if (!id.isNullOrBlank()) {
                            val baseDir = url.substringBeforeLast("/")
                            val resultUrl = "$baseDir/?id=$id"
                            return@withContext TelemetryResult(
                                id = id,
                                resultUrl = resultUrl,
                                endpointUsed = url
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        null
    }
}
