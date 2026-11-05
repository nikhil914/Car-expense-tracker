@file:OptIn(ExperimentalLayoutApi::class)

package ae.sayyara.app.ui.components

import ae.sayyara.app.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

data class ChartPoint(val x: Double, val y: Double)

data class LineSeries(
    val points: List<ChartPoint>,
    val color: Color,
    val drawLine: Boolean = true,
    val dotRadius: Dp = 3.dp,
)

data class StackedBar(val label: String, val segments: List<Pair<Color, Double>>) {
    val total: Double get() = segments.sumOf { it.second.coerceAtLeast(0.0) }
}

private class Plot(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val rtl: Boolean,
) {
    /** Maps a 0..1 fraction along the time axis; time runs right-to-left in RTL. */
    fun x(fraction: Float): Float = if (rtl) right - fraction * (right - left) else left + fraction * (right - left)
}

private fun DrawScope.yAxis(
    measurer: TextMeasurer,
    style: TextStyle,
    gridColor: Color,
    minY: Double,
    maxY: Double,
    yLabel: (Double) -> String,
    rtl: Boolean,
): Plot {
    val ticks = (0..2).map { minY + (maxY - minY) * it / 2.0 }
    val labels = ticks.map { measurer.measure(yLabel(it), style) }
    val labelW = labels.maxOf { it.size.width }.toFloat() + 8.dp.toPx()
    val labelH = labels.first().size.height.toFloat()
    val plot = Plot(
        left = if (rtl) 0f else labelW,
        right = if (rtl) size.width - labelW else size.width,
        top = labelH / 2f,
        bottom = size.height - labelH - 6.dp.toPx(),
        rtl = rtl,
    )
    ticks.forEachIndexed { i, v ->
        val frac = if (maxY == minY) 0f else ((v - minY) / (maxY - minY)).toFloat()
        val y = plot.bottom - frac * (plot.bottom - plot.top)
        drawLine(gridColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
        val l = labels[i]
        val lx = if (rtl) plot.right + 4.dp.toPx() else plot.left - 4.dp.toPx() - l.size.width
        drawText(l, topLeft = Offset(lx, y - l.size.height / 2f))
    }
    return plot
}

private fun DrawScope.drawXLabel(measurer: TextMeasurer, style: TextStyle, text: String, centerX: Float, plot: Plot) {
    val l = measurer.measure(text, style)
    val x = (centerX - l.size.width / 2f).coerceIn(0f, (size.width - l.size.width).coerceAtLeast(0f))
    drawText(l, topLeft = Offset(x, plot.bottom + 4.dp.toPx()))
}

@Composable
private fun NoData(height: Dp, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.chart_no_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Multi-series line/dot chart drawn on a Canvas. The x axis mirrors in RTL. */
@Composable
fun LineChart(
    series: List<LineSeries>,
    yLabel: (Double) -> String,
    xLabel: (Double) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
) {
    val all = series.flatMap { it.points }
    if (all.isEmpty()) {
        NoData(height, modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription },
    ) {
        var minX = all.minOf { it.x }
        var maxX = all.maxOf { it.x }
        if (maxX == minX) { minX -= 1.0; maxX += 1.0 }
        var minY = all.minOf { it.y }
        var maxY = all.maxOf { it.y }
        if (maxY == minY) { minY -= 1.0; maxY += 1.0 }
        val pad = (maxY - minY) * 0.08
        minY -= pad
        maxY += pad

        val plot = yAxis(measurer, style, grid, minY, maxY, yLabel, rtl)
        fun px(x: Double) = plot.x(((x - minX) / (maxX - minX)).toFloat())
        fun py(y: Double) = plot.bottom - ((y - minY) / (maxY - minY)).toFloat() * (plot.bottom - plot.top)

        drawXLabel(measurer, style, xLabel(all.minOf { it.x }), px(all.minOf { it.x }), plot)
        if (all.maxOf { it.x } != all.minOf { it.x }) {
            drawXLabel(measurer, style, xLabel(all.maxOf { it.x }), px(all.maxOf { it.x }), plot)
        }

        series.forEach { s ->
            val pts = s.points.sortedBy { it.x }.map { Offset(px(it.x), py(it.y)) }
            if (s.drawLine && pts.size > 1) {
                val path = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path,
                    color = s.color,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            pts.forEach { drawCircle(s.color, radius = s.dotRadius.toPx(), center = it) }
        }
    }
}

/** Stacked monthly bars. Bars run right-to-left in RTL so time direction matches reading order. */
@Composable
fun StackedBarChart(
    bars: List<StackedBar>,
    yLabel: (Double) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
) {
    if (bars.isEmpty() || bars.all { it.total <= 0.0 }) {
        NoData(height, modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val maxY = bars.maxOf { it.total }
        val plot = yAxis(measurer, style, grid, 0.0, maxY, yLabel, rtl)
        val slot = (plot.right - plot.left) / bars.size
        val barW = (slot * 0.6f).coerceAtMost(36.dp.toPx())
        val labelEvery = ((bars.size + 5) / 6).coerceAtLeast(1)
        bars.forEachIndexed { i, bar ->
            val center = plot.x((i + 0.5f) / bars.size)
            var y = plot.bottom
            bar.segments.forEach { (color, value) ->
                if (value <= 0.0) return@forEach
                val h = (value / maxY).toFloat() * (plot.bottom - plot.top)
                drawRect(color, topLeft = Offset(center - barW / 2f, y - h), size = Size(barW, h))
                y -= h
            }
            if (i % labelEvery == 0 || i == bars.lastIndex) {
                drawXLabel(measurer, style, bar.label, center, plot)
            }
        }
    }
}

@Composable
fun ChartLegend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
