package com.netplus.speedtest.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netplus.speedtest.model.SpeedTestPhase
import java.util.Locale
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

private val CyanAccent = Color(0xFF00E5FF)
private val BlueAccent = Color(0xFF2979FF)
private val GreenAccent = Color(0xFF00E676)
private val PurpleAccent = Color(0xFFD500F9)
private val AmberAccent = Color(0xFFFFAB00)
private val RedAccent = Color(0xFFFF5252)

@Composable
fun SpeedMeterGauge(
    speedMbps: Float,
    phase: SpeedTestPhase,
    useMegabytes: Boolean,
    onToggleUnit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isZeroed = phase == SpeedTestPhase.COMPLETED || phase == SpeedTestPhase.IDLE || phase == SpeedTestPhase.ERROR
    val rawDisplaySpeed = if (useMegabytes) speedMbps / 8f else speedMbps
    val targetDisplaySpeed = if (isZeroed) 0f else rawDisplaySpeed
    val unitLabel = if (useMegabytes) "MB/s" else "Mbps"

    val animDuration = when (phase) {
        SpeedTestPhase.COMPLETED -> 450
        SpeedTestPhase.IDLE -> 350
        SpeedTestPhase.ERROR -> 300
        else -> 120 // Fast 120ms frame updates for high-frame-rate fluidity
    }

    val animatedDisplaySpeed by animateFloatAsState(
        targetValue = targetDisplaySpeed,
        animationSpec = tween(
            durationMillis = animDuration,
            easing = if (phase == SpeedTestPhase.COMPLETED) FastOutSlowInEasing else LinearOutSlowInEasing
        ),
        label = "display_speed"
    )

    val targetNormalized = if (isZeroed) 0f else speedToFraction(speedMbps)
    val animatedProgress by animateFloatAsState(
        targetValue = targetNormalized,
        animationSpec = tween(
            durationMillis = animDuration,
            easing = if (phase == SpeedTestPhase.COMPLETED) FastOutSlowInEasing else LinearOutSlowInEasing
        ),
        label = "gauge_progress"
    )

    val primaryColor = when (phase) {
        SpeedTestPhase.DOWNLOAD -> CyanAccent
        SpeedTestPhase.UPLOAD -> GreenAccent
        SpeedTestPhase.PING -> PurpleAccent
        SpeedTestPhase.PREPARING -> AmberAccent
        SpeedTestPhase.COMPLETED -> BlueAccent
        SpeedTestPhase.ERROR -> RedAccent
        else -> CyanAccent
    }

    Box(
        modifier = modifier
            .size(280.dp)
            .testTag("speed_gauge"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSpeedometer(
                progress = animatedProgress,
                activeColor = primaryColor,
                trackColor = Color(0xFF334155).copy(alpha = 0.45f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 28.dp)
        ) {
            val phaseLabel = when (phase) {
                SpeedTestPhase.IDLE -> "SIAP"
                SpeedTestPhase.PREPARING -> "MENGHUBUNGKAN..."
                SpeedTestPhase.PING -> "UJI LATENSI"
                SpeedTestPhase.DOWNLOAD -> "DOWNLOAD"
                SpeedTestPhase.UPLOAD -> "UPLOAD"
                SpeedTestPhase.COMPLETED -> "SELESAI"
                SpeedTestPhase.ERROR -> "GAGAL"
            }

            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.labelSmall,
                color = primaryColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            val formattedSpeed = when {
                animatedDisplaySpeed >= 100f -> String.format(Locale.US, "%.0f", animatedDisplaySpeed)
                animatedDisplaySpeed >= 10f -> String.format(Locale.US, "%.1f", animatedDisplaySpeed)
                animatedDisplaySpeed > 0.05f -> String.format(Locale.US, "%.2f", animatedDisplaySpeed)
                else -> "0.0"
            }

            Text(
                text = formattedSpeed,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = primaryColor.copy(alpha = 0.15f),
                modifier = Modifier
                    .clickable { onToggleUnit() }
                    .testTag("unit_toggle_button")
            ) {
                Text(
                    text = unitLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}

private fun DrawScope.drawSpeedometer(
    progress: Float,
    activeColor: Color,
    trackColor: Color
) {
    val strokeWidth = 14.dp.toPx()
    val padding = 24.dp.toPx()
    val arcSize = size.width - padding * 2
    val topLeft = Offset(padding, padding)
    val startAngle = 140f
    val sweepAngle = 260f

    // Inactive background arc track
    drawArc(
        color = trackColor,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = Size(arcSize, arcSize),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Active illuminated gradient arc
    if (progress > 0.005f) {
        val activeSweep = (sweepAngle * progress).coerceIn(1f, sweepAngle)
        val arcGradient = Brush.linearGradient(
            colors = listOf(
                activeColor.copy(alpha = 0.5f),
                activeColor
            ),
            start = Offset(padding, size.height - padding),
            end = Offset(size.width - padding, padding)
        )

        drawArc(
            brush = arcGradient,
            startAngle = startAngle,
            sweepAngle = activeSweep,
            useCenter = false,
            topLeft = topLeft,
            size = Size(arcSize, arcSize),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }

    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = arcSize / 2f
    val innerRadius = outerRadius - 16.dp.toPx()

    // Tick marks around gauge (0, 1, 5, 10, 25, 50, 100, 250, 500, 1000 Mbps)
    val tickValues = listOf(0f, 1f, 5f, 10f, 25f, 50f, 100f, 250f, 500f, 1000f)
    tickValues.forEach { speedVal ->
        val frac = speedToFraction(speedVal)
        val angleDeg = startAngle + sweepAngle * frac
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val p1 = Offset(
            (center.x + innerRadius * cos(angleRad)).toFloat(),
            (center.y + innerRadius * sin(angleRad)).toFloat()
        )
        val p2 = Offset(
            (center.x + (outerRadius - 6.dp.toPx()) * cos(angleRad)).toFloat(),
            (center.y + (outerRadius - 6.dp.toPx()) * sin(angleRad)).toFloat()
        )

        val isReached = frac <= progress
        val tickColor = if (isReached) activeColor else trackColor.copy(alpha = 0.6f)
        drawLine(
            color = tickColor,
            start = p1,
            end = p2,
            strokeWidth = if (speedVal in listOf(0f, 10f, 100f, 1000f)) 3.dp.toPx() else 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // Glowing tip bead at current progress point
    val currentAngleDeg = startAngle + sweepAngle * progress
    val currentAngleRad = Math.toRadians(currentAngleDeg.toDouble())
    val beadRadius = outerRadius - (strokeWidth / 2f)
    val beadCenter = Offset(
        (center.x + beadRadius * cos(currentAngleRad)).toFloat(),
        (center.y + beadRadius * sin(currentAngleRad)).toFloat()
    )

    drawCircle(
        color = activeColor.copy(alpha = 0.35f),
        radius = 12.dp.toPx(),
        center = beadCenter
    )
    drawCircle(
        color = Color.White,
        radius = 4.dp.toPx(),
        center = beadCenter
    )
}

fun speedToFraction(speedMbps: Float): Float {
    if (speedMbps <= 0f) return 0f
    if (speedMbps >= 1000f) return 1f
    val logVal = ln(speedMbps + 1.0)
    val maxLog = ln(1001.0)
    return (logVal / maxLog).toFloat().coerceIn(0f, 1f)
}
