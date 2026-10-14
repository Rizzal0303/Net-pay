package com.netplus.speedtest.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netplus.speedtest.model.LibreSpeedServer
import com.netplus.speedtest.model.SpeedTestPhase
import com.netplus.speedtest.model.SpeedTestState
import com.netplus.speedtest.viewmodel.SpeedTestViewModel
import java.util.Locale

private val CyanAccent = Color(0xFF00E5FF)
private val BlueAccent = Color(0xFF2979FF)
private val GreenAccent = Color(0xFF00E676)
private val PurpleAccent = Color(0xFFD500F9)
private val AmberAccent = Color(0xFFFFAB00)
private val RedAccent = Color(0xFFFF5252)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(
    viewModel: SpeedTestViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.speedTestState.collectAsState()
    val serverList by viewModel.serverList.collectAsState()
    var showServerPicker by remember { mutableStateOf(false) }

    val isTesting = state.phase == SpeedTestPhase.PREPARING ||
            state.phase == SpeedTestPhase.PING ||
            state.phase == SpeedTestPhase.DOWNLOAD ||
            state.phase == SpeedTestPhase.UPLOAD

    BackHandler {
        if (isTesting) {
            viewModel.cancelTest()
        }
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Uji Kecepatan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "LibreSpeed Netplus Engine",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isTesting) {
                                viewModel.cancelTest()
                            }
                            onBack()
                        },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Server Selector Card
            ServerSelectorPill(
                server = state.selectedServer,
                isTesting = isTesting,
                onClick = { if (!isTesting) showServerPicker = true },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Test Phase Steps Pill Row
            PhaseStepsIndicator(
                phase = state.phase,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Speedometer Gauge
            SpeedMeterGauge(
                speedMbps = state.currentSpeedMbps,
                phase = state.phase,
                useMegabytes = state.useMegabytes,
                onToggleUnit = { viewModel.toggleUnit() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics Cards Row 1: Ping & Jitter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "PING",
                    value = if (state.pingMs > 0) String.format(Locale.US, "%.1f", state.pingMs) else "--",
                    unit = "ms",
                    icon = Icons.Default.Wifi,
                    iconColor = PurpleAccent,
                    modifier = Modifier.weight(1f),
                    testTag = "metric_ping"
                )
                MetricCard(
                    title = "JITTER",
                    value = if (state.jitterMs > 0) String.format(Locale.US, "%.1f", state.jitterMs) else "--",
                    unit = "ms",
                    icon = Icons.Default.Dns,
                    iconColor = AmberAccent,
                    modifier = Modifier.weight(1f),
                    testTag = "metric_jitter"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Cards Row 2: Download & Upload (updates live during each phase)
            val unitLabel = if (state.useMegabytes) "MB/s" else "Mbps"
            val liveDlVal = when {
                state.phase == SpeedTestPhase.DOWNLOAD -> {
                    val v = if (state.useMegabytes) state.currentSpeedMbps / 8f else state.currentSpeedMbps
                    String.format(Locale.US, "%.2f", v)
                }
                state.downloadSpeedMbps > 0 -> {
                    val v = if (state.useMegabytes) state.downloadSpeedMbps / 8f else state.downloadSpeedMbps
                    String.format(Locale.US, "%.2f", v)
                }
                else -> "--"
            }

            val liveUlVal = when {
                state.phase == SpeedTestPhase.UPLOAD -> {
                    val v = if (state.useMegabytes) state.currentSpeedMbps / 8f else state.currentSpeedMbps
                    String.format(Locale.US, "%.2f", v)
                }
                state.uploadSpeedMbps > 0 -> {
                    val v = if (state.useMegabytes) state.uploadSpeedMbps / 8f else state.uploadSpeedMbps
                    String.format(Locale.US, "%.2f", v)
                }
                else -> "--"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "DOWNLOAD",
                    value = liveDlVal,
                    unit = unitLabel,
                    icon = Icons.Default.ArrowDownward,
                    iconColor = CyanAccent,
                    modifier = Modifier.weight(1f),
                    isActive = state.phase == SpeedTestPhase.DOWNLOAD,
                    testTag = "metric_download"
                )
                MetricCard(
                    title = "UPLOAD",
                    value = liveUlVal,
                    unit = unitLabel,
                    icon = Icons.Default.ArrowUpward,
                    iconColor = GreenAccent,
                    modifier = Modifier.weight(1f),
                    isActive = state.phase == SpeedTestPhase.UPLOAD,
                    testTag = "metric_upload"
                )
            }

            // Error Card when test encounters an error
            AnimatedVisibility(
                visible = state.phase == SpeedTestPhase.ERROR,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .testTag("error_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = RedAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pengujian Terkendala",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RedAccent
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.errorMessage ?: "Koneksi tidak stabil atau server pengujian belum merespon. Silakan coba kembali atau pilih server lain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showServerPicker = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ganti Server")
                            }
                            Button(
                                onClick = { viewModel.startSpeedTest() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanAccent,
                                    contentColor = Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Uji Ulang", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Action Button (START / STOP)
            if (isTesting) {
                Button(
                    onClick = { viewModel.cancelTest() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("stop_test_button")
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BATALKAN TES",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = { viewModel.startSpeedTest() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_test_button")
                ) {
                    Icon(
                        imageVector = if (state.phase == SpeedTestPhase.COMPLETED) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.phase == SpeedTestPhase.COMPLETED) "UJI ULANG KECEPATAN" else "MULAI UJI KECEPATAN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Client IP / ISP info
            if (state.clientIp.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "IP: ${state.clientIp}" + if (state.clientIsp.isNotBlank()) " • ${state.clientIsp}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showServerPicker) {
        ServerPickerBottomSheet(
            servers = serverList,
            selectedServer = state.selectedServer,
            onSelectServer = { server ->
                viewModel.selectServer(server)
                showServerPicker = false
            },
            onDismiss = { showServerPicker = false }
        )
    }
}

@Composable
fun PhaseStepsIndicator(
    phase: SpeedTestPhase,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        Triple("Ping", SpeedTestPhase.PING, PurpleAccent),
        Triple("Download", SpeedTestPhase.DOWNLOAD, CyanAccent),
        Triple("Upload", SpeedTestPhase.UPLOAD, GreenAccent),
        Triple("Selesai", SpeedTestPhase.COMPLETED, BlueAccent)
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEach { (name, stepPhase, color) ->
                val isActive = phase == stepPhase
                val isDone = when (stepPhase) {
                    SpeedTestPhase.PING -> phase == SpeedTestPhase.DOWNLOAD || phase == SpeedTestPhase.UPLOAD || phase == SpeedTestPhase.COMPLETED
                    SpeedTestPhase.DOWNLOAD -> phase == SpeedTestPhase.UPLOAD || phase == SpeedTestPhase.COMPLETED
                    SpeedTestPhase.UPLOAD -> phase == SpeedTestPhase.COMPLETED
                    else -> false
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isActive -> color.copy(alpha = 0.2f)
                        isDone -> color.copy(alpha = 0.1f)
                        else -> Color.Transparent
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isActive -> color
                                        isDone -> color.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isActive -> color
                                isDone -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServerSelectorPill(
    server: LibreSpeedServer?,
    isTesting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .clickable(enabled = !isTesting) { onClick() }
            .testTag("server_picker_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = CyanAccent.copy(alpha = 0.2f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SERVER PENGUJIAN",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = server?.name ?: "Pilih Server Speedtest",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isTesting) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyanAccent.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "GANTI",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    testTag: String = ""
) {
    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) iconColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isActive) BorderStroke(1.dp, iconColor.copy(alpha = 0.4f)) else null
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) iconColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerPickerBottomSheet(
    servers: List<LibreSpeedServer>,
    selectedServer: LibreSpeedServer?,
    onSelectServer: (LibreSpeedServer) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Pilih Server Pengujian",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))

            servers.forEach { srv ->
                val isSelected = srv.server == selectedServer?.server
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) CyanAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectServer(srv) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = srv.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) CyanAccent else MaterialTheme.colorScheme.onSurface
                            )
                            if (srv.sponsorName != null) {
                                Text(
                                    text = "Disediakan oleh ${srv.sponsorName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
