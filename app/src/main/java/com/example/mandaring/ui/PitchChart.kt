package com.example.mandaring.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mandaring.pitch.Contour
import com.example.mandaring.pitch.PitchRange
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

/** With a speaker range, the chart shows the five tone levels plus half a level either side. */
private const val LOWEST_LEVEL = 0.5f
private const val HIGHEST_LEVEL = 5.5f

private val LEVEL_LABEL_WIDTH = 20.dp
private val HZ_LABEL_WIDTH = 36.dp

/**
 * Pitch over time on a logarithmic frequency axis, with gaps where the voice is unvoiced.
 *
 * With a [range] the axis is the speaker's five tone levels, drawn as bands, and pitch outside
 * them is held at the edge of the chart. Without one the axis is in Hz, fitted to the track.
 */
@Composable
fun PitchChart(track: PitchTrack, range: PitchRange?, cursorMs: Float?, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val bandColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val cursorColor = MaterialTheme.colorScheme.tertiary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
    val bounds = remember(track, range) {
        if (range != null) range.hz(LOWEST_LEVEL) to range.hz(HIGHEST_LEVEL) else fittedBounds(track)
    }

    Canvas(modifier) {
        val labelWidth = (if (range != null) LEVEL_LABEL_WIDTH else HZ_LABEL_WIDTH).toPx()
        val plotWidth = size.width - labelWidth
        val (low, high) = bounds

        fun x(timeMs: Float) = labelWidth + plotWidth * timeMs / track.durationMs
        fun y(hz: Float) = size.height * (1f - ln(hz / low) / ln(high / low))

        if (range != null) {
            drawLevelBands(labelWidth, bandColor, textMeasurer, labelStyle) { y(range.hz(it)) }
        } else {
            for (hz in GRID_HZ) {
                if (hz < low || hz > high) continue
                drawLine(gridColor, Offset(labelWidth, y(hz)), Offset(size.width, y(hz)), strokeWidth = 1.dp.toPx())
                drawAxisLabel(hz.toInt().toString(), y(hz), textMeasurer, labelStyle)
            }
        }

        drawCurve(track.size, lineColor, 4.dp) { i ->
            if (track.isVoiced(i)) Offset(x(i * track.hopMs), y(track.f0[i].coerceIn(low, high))) else null
        }

        if (cursorMs != null) {
            drawLine(cursorColor, Offset(x(cursorMs), 0f), Offset(x(cursorMs), size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

/**
 * An attempt drawn over the faded contour of the [reference] it imitates, on the five tone
 * levels. Both are on the frames of [reference], as is [cursorMs].
 */
@Composable
fun OverlayChart(reference: Contour, attempt: Contour?, cursorMs: Float?, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val ghostColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val bandColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val cursorColor = MaterialTheme.colorScheme.tertiary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier) {
        val labelWidth = LEVEL_LABEL_WIDTH.toPx()
        val plotWidth = size.width - labelWidth

        fun x(timeMs: Float) = labelWidth + plotWidth * timeMs / reference.durationMs
        fun y(level: Float) = size.height * (HIGHEST_LEVEL - level) / (HIGHEST_LEVEL - LOWEST_LEVEL)
        fun point(contour: Contour, frame: Int) = if (contour.isVoiced(frame)) {
            Offset(x(frame * contour.hopMs), y(contour.levels[frame].coerceIn(LOWEST_LEVEL, HIGHEST_LEVEL)))
        } else {
            null
        }

        drawLevelBands(labelWidth, bandColor, textMeasurer, labelStyle) { y(it) }
        drawCurve(reference.size, ghostColor, 12.dp) { point(reference, it) }
        if (attempt != null) {
            drawCurve(attempt.size, lineColor, 4.dp) { point(attempt, it) }
        }

        // An attempt's silence before and after speaking falls outside the reference's time span.
        if (cursorMs != null && cursorMs in 0f..reference.durationMs) {
            drawLine(cursorColor, Offset(x(cursorMs), 0f), Offset(x(cursorMs), size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

/** Shades every other tone level and numbers all five, to the right of [labelWidth]. */
private fun DrawScope.drawLevelBands(
    labelWidth: Float,
    bandColor: Color,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    y: (level: Float) -> Float,
) {
    for (level in 1..5) {
        val top = y(level + 0.5f)
        val bottom = y(level - 0.5f)
        if (level % 2 == 1) {
            drawRect(bandColor, Offset(labelWidth, top), Size(size.width - labelWidth, bottom - top))
        }
        drawAxisLabel(level.toString(), y(level.toFloat()), textMeasurer, labelStyle)
    }
}

private fun DrawScope.drawAxisLabel(text: String, y: Float, textMeasurer: TextMeasurer, style: TextStyle) {
    val layout = textMeasurer.measure(text, style)
    val top = (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
    drawText(layout, topLeft = Offset(0f, top))
}

/** Joins the points of consecutive frames, lifting the pen where [point] is null. */
private fun DrawScope.drawCurve(frames: Int, color: Color, width: Dp, point: (frame: Int) -> Offset?) {
    val path = Path()
    var penDown = false
    for (i in 0 until frames) {
        val offset = point(i)
        if (offset == null) {
            penDown = false
            continue
        }
        if (penDown) path.lineTo(offset.x, offset.y) else path.moveTo(offset.x, offset.y)
        penDown = true
    }
    drawPath(path, color, style = Stroke(width = width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Lowest and highest frequency to show: the voiced range of the track with some headroom. */
private fun fittedBounds(track: PitchTrack): Pair<Float, Float> {
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
