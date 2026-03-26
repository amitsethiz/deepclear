package com.deepclear.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepclear.app.ui.theme.ChartFree
import com.deepclear.app.ui.theme.ChartFreeDark
import com.deepclear.app.ui.theme.ChartUsed
import com.deepclear.app.ui.theme.ChartUsedDark
import com.deepclear.app.ui.theme.Cyan
import com.deepclear.app.ui.theme.DeepBlue
import com.deepclear.app.ui.theme.Teal
import com.deepclear.app.ui.theme.TealLight
import com.deepclear.app.util.FileSize

/**
 * Modern donut chart showing used vs free storage.
 * Features:
 *   - Animated sweep on first draw
 *   - Gradient fills for both arcs
 *   - Center text with used/total storage
 *   - Smooth rounded caps
 */
@Composable
fun DonutChart(
    usedBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    strokeWidth: Dp = 22.dp,
    animationDuration: Int = 1200
) {
    val usedFraction = if (totalBytes > 0) {
        (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(usedFraction) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = animationDuration,
                easing = FastOutSlowInEasing
            )
        )
    }

    val usedSweep = usedFraction * 360f * animatedProgress.value
    val freeSweep = (1f - usedFraction) * 360f * animatedProgress.value

    // Gradient brushes
    val usedBrush = Brush.sweepGradient(
        colors = listOf(DeepBlue, ChartUsed, ChartUsedDark, DeepBlue)
    )
    val freeBrush = Brush.sweepGradient(
        colors = listOf(Teal, ChartFree, ChartFreeDark, TealLight, Teal)
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val canvasSize = this.size.minDimension
            val stroke = strokeWidth.toPx()
            val arcSize = canvasSize - stroke
            val topLeft = Offset(stroke / 2f, stroke / 2f)

            // Background track (subtle)
            drawArc(
                color = ChartFree.copy(alpha = 0.1f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Free arc (Teal gradient)
            if (freeSweep > 0.5f) {
                drawArc(
                    brush = freeBrush,
                    startAngle = -90f + usedSweep,
                    sweepAngle = freeSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            // Used arc (Deep Blue gradient) — drawn on top
            if (usedSweep > 0.5f) {
                drawArc(
                    brush = usedBrush,
                    startAngle = -90f,
                    sweepAngle = usedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = FileSize.format(usedBytes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "of ${FileSize.format(totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "used",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
