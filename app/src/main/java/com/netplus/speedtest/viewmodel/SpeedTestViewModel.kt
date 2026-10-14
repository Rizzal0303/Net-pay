package com.netplus.speedtest.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netplus.speedtest.engine.LibreSpeedEngine
import com.netplus.speedtest.model.LibreSpeedServer
import com.netplus.speedtest.model.SpeedTestPhase
import com.netplus.speedtest.model.SpeedTestState
import com.netplus.speedtest.repository.SpeedTestServerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpeedTestViewModel(application: Application) : AndroidViewModel(application) {

    private val serverRepo = SpeedTestServerRepository(application)
    private val engine = LibreSpeedEngine()

    private val _speedTestState = MutableStateFlow(SpeedTestState())
    val speedTestState: StateFlow<SpeedTestState> = _speedTestState.asStateFlow()

    private val _serverList = MutableStateFlow<List<LibreSpeedServer>>(emptyList())
    val serverList: StateFlow<List<LibreSpeedServer>> = _serverList.asStateFlow()

    private val _isLoadingServers = MutableStateFlow(false)
    val isLoadingServers: StateFlow<Boolean> = _isLoadingServers.asStateFlow()

    private var speedTestJob: Job? = null

    init {
        loadServers()
    }

    fun loadServers() {
        viewModelScope.launch {
            _isLoadingServers.value = true
            val initialSelected = serverRepo.getSelectedServer()
            _speedTestState.update { it.copy(selectedServer = initialSelected) }

            val servers = serverRepo.fetchServers()
            _serverList.value = servers

            var selected = servers.find { it.server == initialSelected.server } ?: servers.firstOrNull() ?: initialSelected
            if (selected.candidateEndpoints.isNotEmpty()) {
                selected = engine.resolveFastestEndpoint(selected)
            }
            _speedTestState.update { it.copy(selectedServer = selected) }
            _isLoadingServers.value = false

            // Fetch IP info in background
            try {
                val (ip, isp) = engine.fetchClientIp(selected)
                _speedTestState.update { it.copy(clientIp = ip, clientIsp = isp) }
            } catch (_: Exception) {}
        }
    }

    fun selectServer(server: LibreSpeedServer) {
        viewModelScope.launch {
            var target = server
            if (target.candidateEndpoints.isNotEmpty()) {
                target = engine.resolveFastestEndpoint(target)
            }
            serverRepo.saveSelectedServer(target)
            _speedTestState.update { it.copy(selectedServer = target) }
        }
    }

    fun toggleUnit() {
        _speedTestState.update { it.copy(useMegabytes = !it.useMegabytes) }
    }

    fun startSpeedTest() {
        if (_speedTestState.value.phase != SpeedTestPhase.IDLE &&
            _speedTestState.value.phase != SpeedTestPhase.COMPLETED &&
            _speedTestState.value.phase != SpeedTestPhase.ERROR
        ) return

        speedTestJob?.cancel()
        speedTestJob = viewModelScope.launch {
            val baseServer = _speedTestState.value.selectedServer ?: serverRepo.getSelectedServer()

            _speedTestState.update {
                it.copy(
                    phase = SpeedTestPhase.PREPARING,
                    currentSpeedMbps = 0f,
                    progressFraction = 0f,
                    pingMs = 0f,
                    jitterMs = 0f,
                    downloadSpeedMbps = 0f,
                    uploadSpeedMbps = 0f,
                    errorMessage = null,
                    throughputHistory = emptyList(),
                    telemetryId = null,
                    telemetryUrl = null
                )
            }

            try {
                // Quick resolution of fastest endpoint if needed
                val activeServer = if (baseServer.activeEndpoint.isNullOrBlank() && baseServer.candidateEndpoints.isNotEmpty()) {
                    engine.resolveFastestEndpoint(baseServer).also { resolved ->
                        _speedTestState.update { it.copy(selectedServer = resolved) }
                    }
                } else baseServer

                // Quick client IP fetch if missing
                if (_speedTestState.value.clientIp.isBlank()) {
                    try {
                        val (ip, isp) = engine.fetchClientIp(activeServer)
                        _speedTestState.update { it.copy(clientIp = ip, clientIsp = isp) }
                    } catch (_: Exception) {}
                }

                // 1. PING & JITTER (~0.3s)
                _speedTestState.update { it.copy(phase = SpeedTestPhase.PING, progressFraction = 0f) }
                val (ping, jitter) = engine.measurePingAndJitter(
                    server = activeServer,
                    pingCount = 6,
                    onProgress = { samplePing, progress ->
                        _speedTestState.update {
                            it.copy(
                                pingMs = samplePing,
                                progressFraction = progress
                            )
                        }
                    }
                )
                _speedTestState.update {
                    it.copy(
                        pingMs = ping,
                        jitterMs = jitter,
                        progressFraction = 1f
                    )
                }
                delay(250)

                // 2. DOWNLOAD (~6.5s)
                _speedTestState.update {
                    it.copy(
                        phase = SpeedTestPhase.DOWNLOAD,
                        currentSpeedMbps = 0f,
                        progressFraction = 0f,
                        throughputHistory = emptyList()
                    )
                }
                val downloadSpeed = engine.measureDownloadSpeed(
                    server = activeServer,
                    durationMs = 6500L,
                    streamsCount = 3,
                    onProgress = { instantSpeed, fraction ->
                        _speedTestState.update { state ->
                            val history = (state.throughputHistory + instantSpeed).takeLast(40)
                            state.copy(
                                currentSpeedMbps = instantSpeed,
                                progressFraction = fraction,
                                throughputHistory = history
                            )
                        }
                    }
                )
                _speedTestState.update {
                    it.copy(
                        downloadSpeedMbps = downloadSpeed,
                        currentSpeedMbps = downloadSpeed,
                        progressFraction = 1f
                    )
                }
                delay(400) // Smooth visual transition between Download and Upload

                // 3. UPLOAD (~6.0s)
                _speedTestState.update {
                    it.copy(
                        phase = SpeedTestPhase.UPLOAD,
                        currentSpeedMbps = 0f,
                        progressFraction = 0f,
                        throughputHistory = emptyList()
                    )
                }
                val uploadSpeed = engine.measureUploadSpeed(
                    server = activeServer,
                    durationMs = 6000L,
                    streamsCount = 2,
                    onProgress = { instantSpeed, fraction ->
                        _speedTestState.update { state ->
                            val history = (state.throughputHistory + instantSpeed).takeLast(40)
                            state.copy(
                                currentSpeedMbps = instantSpeed,
                                progressFraction = fraction,
                                throughputHistory = history
                            )
                        }
                    }
                )
                _speedTestState.update {
                    it.copy(
                        uploadSpeedMbps = uploadSpeed,
                        currentSpeedMbps = uploadSpeed,
                        progressFraction = 1f
                    )
                }
                delay(300)

                // Phase Completed
                _speedTestState.update {
                    it.copy(
                        phase = SpeedTestPhase.COMPLETED,
                        currentSpeedMbps = 0f,
                        progressFraction = 1f
                    )
                }

                // 4. Background Telemetry submission
                launch {
                    try {
                        val telemetry = engine.submitTelemetry(
                            server = activeServer,
                            downloadSpeedMbps = downloadSpeed,
                            uploadSpeedMbps = uploadSpeed,
                            pingMs = ping,
                            jitterMs = jitter,
                            clientIp = _speedTestState.value.clientIp,
                            clientIsp = _speedTestState.value.clientIsp
                        )
                        if (telemetry != null) {
                            _speedTestState.update {
                                it.copy(
                                    telemetryId = telemetry.id,
                                    telemetryUrl = telemetry.resultUrl
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }

            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    val userFriendlyMsg = when {
                        e.message?.contains("timeout", ignoreCase = true) == true ->
                            "Koneksi ke server pengujian batas waktu (timeout). Silakan periksa jaringan dan coba lagi."
                        e.message?.contains("unreachable", ignoreCase = true) == true ||
                        e.message?.contains("connect", ignoreCase = true) == true ->
                            "Tidak dapat terhubung ke server pengujian. Coba ganti server cadangan."
                        else -> e.message ?: "Pengujian terputus. Silakan coba kembali."
                    }
                    _speedTestState.update {
                        it.copy(
                            phase = SpeedTestPhase.ERROR,
                            errorMessage = userFriendlyMsg
                        )
                    }
                }
            }
        }
    }

    fun cancelTest() {
        speedTestJob?.cancel()
        _speedTestState.update {
            it.copy(
                phase = SpeedTestPhase.IDLE,
                currentSpeedMbps = 0f,
                progressFraction = 0f
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        speedTestJob?.cancel()
    }
}
