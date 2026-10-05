package com.example.mandaring.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.mandaring.pitch.PitchTrack
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Frequencies that get a labelled gridline when they fall inside the visible range. */
private val GRID_HZ = listOf(60f, 80f, 100f, 125f, 150f, 200f, 250f, 300f, 400f, 500f)

/** The chart never zooms in further than this, so a flat tone does not look dramatic. */
private const val MIN_SPAN_OCTAVES = 1f

/** Pitch over time on a logarithmic frequency axis, with gaps where the voice is unvoiced. */
@Composable
fun PitchChart(track: PitchTrack, cursorMs: Float?, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val cursorColor = MaterialTheme.colorScheme.tertiary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
    val range = remember(track) { visibleRange(track) }

    Canvas(modifier) {
        val labelWidth = 36.dp.toPx()
        val plotWidth = size.width - labelWidth
        val (low, high) = range

        fun x(timeMs: Float) = labelWidth + plotWidth * timeMs / track.durationMs
        fun y(hz: Float) = size.height * (1f - ln(hz / low) / ln(high / low))

        for (hz in GRID_HZ) {
            if (hz < low || hz > high) continue
            drawLine(gridColor, Offset(labelWidth, y(hz)), Offset(size.width, y(hz)), strokeWidth = 1.dp.toPx())
            val label = textMeasurer.measure(hz.toInt().toString(), labelStyle)
            val top = (y(hz) - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)
            drawText(label, topLeft = Offset(0f, top))
        }

        val path = Path()
        var penDown = false
        for (i in 0 until track.size) {
            if (!track.isVoiced(i)) {
                penDown = false
                continue
            }
            val px = x(i * track.hopMs)
            val py = y(track.f0[i])
            if (penDown) path.lineTo(px, py) else path.moveTo(px, py)
            penDown = true
        }
        drawPath(
            path,
            lineColor,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        if (cursorMs != null) {
            drawLine(cursorColor, Offset(x(cursorMs), 0f), Offset(x(cursorMs), size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

/** Lowest and highest frequency to show: the voiced range with some headroom. */
private fun visibleRange(track: PitchTrack): Pair<Float, Float> {
    var low = Float.MAX_VALUE
    var high = 0f
    for (i in 0 until track.size) {
        if (!track.isVoiced(i)) continue
        low = min(low, track.f0[i])
        high = max(high, track.f0[i])
    }
    if (high == 0f) return 100f to 400f

    val centre = sqrt(low * high)
    val halfSpan = max(high / low * 1.15f, 2f.pow(MIN_SPAN_OCTAVES)).let { sqrt(it) }
    return centre / halfSpan to centre * halfSpan
}
