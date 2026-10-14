package com.netplus.speedtest.model

enum class SpeedTestPhase {
    IDLE,
    PREPARING,
    PING,
    DOWNLOAD,
    UPLOAD,
    COMPLETED,
    ERROR
}

data class SpeedTestState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val currentSpeedMbps: Float = 0f,
    val progressFraction: Float = 0f,
    val pingMs: Float = 0f,
    val jitterMs: Float = 0f,
    val downloadSpeedMbps: Float = 0f,
    val uploadSpeedMbps: Float = 0f,
    val clientIp: String = "",
    val clientIsp: String = "",
    val selectedServer: LibreSpeedServer? = null,
    val errorMessage: String? = null,
    val throughputHistory: List<Float> = emptyList(),
    val useMegabytes: Boolean = false,
    val telemetryId: String? = null,
    val telemetryUrl: String? = null
)
