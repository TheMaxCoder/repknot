package com.repknot.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WavyLinearProgress(progress: () -> Float, modifier: Modifier = Modifier,
                       color: Color = MaterialTheme.colorScheme.primary,
                       trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    val target = progress().coerceIn(0f, 1f)
    val value by animateFloatAsState(target, label = "progress")
    val amplitude by animateFloatAsState(if (target <= 0.02f || target >= 0.98f) 0f else 1f, label = "wave amplitude")
    // Compose's infinite animation clock stops when the system duration scale is zero.
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier.height(10.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) }) {
        val stroke = 4.dp.toPx()
        val wavelength = 32.dp.toPx()
        val radius = stroke / 2f
        val start = radius
        val end = (size.width - radius).coerceAtLeast(start)
        val split = start + (end - start) * value
        val center = size.height / 2f
        if (value > 0f && split > start) {
            val path = Path().apply {
                moveTo(start, center + 3.dp.toPx() * amplitude * sin((-phase * 2f * PI).toFloat()))
                var x = start + 1.dp.toPx()
                while (x < split) {
                    lineTo(x, center + 3.dp.toPx() * amplitude * sin(((x - start) / wavelength - phase) * 2f * PI.toFloat()))
                    x += 1.dp.toPx()
                }
                lineTo(split, center + 3.dp.toPx() * amplitude * sin(((split - start) / wavelength - phase) * 2f * PI.toFloat()))
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        val trackStart = if (value <= 0f) start else split + 4.dp.toPx() + stroke
        if (value < 1f && trackStart < end) drawLine(trackColor, Offset(trackStart, center), Offset(end, center), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
