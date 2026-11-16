package ar.godata.monitor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.godata.monitor.Series
import ar.godata.monitor.dur
import ar.godata.monitor.lumo.AppTheme
import ar.godata.monitor.lumo.Dimens
import ar.godata.monitor.lumo.components.Text
import ar.godata.monitor.lumo.components.progressindicators.CircularProgressIndicator

@Composable
fun muted() = AppTheme.colors.textSecondary

@Composable
fun accent() = AppTheme.colors.tertiary

/** [ok] below [warn], orange from [warn], red from [crit]. */
@Composable
fun pctColor(v: Double, warn: Double = 70.0, crit: Double = 90.0, ok: Color = accent()): Color = when {
    v >= crit -> AppTheme.colors.error
    v >= warn -> AppTheme.colors.warning
    else -> ok
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun Note(text: String, color: Color = muted()) {
    Text(text, style = AppTheme.typography.body2, color = color)
}

/**
 * Base surface of every card: a faint diagonal gradient, a hairline border lit from the top and
 * large corners. [tint] washes the card with a state color (alerts, security status).
 */
@Composable
fun GlassCard(modifier: Modifier = Modifier, tint: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(c.surfaceHigh, c.surface)))
            .then(if (tint != null) Modifier.background(Brush.linearGradient(listOf(tint.copy(alpha = 0.16f), Color.Transparent))) else Modifier)
            .border(1.dp, Brush.verticalGradient(listOf(tint?.copy(alpha = 0.35f) ?: c.outline, c.outline.copy(alpha = 0.35f))), shape)
            .padding(Dimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Rounded square holding a line icon, tinted with [tint]. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = Dimens.iconTile) {
    val shape = RoundedCornerShape(Dimens.iconTileRadius)
    Box(
        Modifier.size(size).clip(shape).background(tint.copy(alpha = 0.14f)).border(1.dp, tint.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, size * 0.5f) }
}

@Composable
fun SectionCard(
    title: String? = null,
    trailing: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = AppTheme.colors.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconTile(icon, iconTint, Dimens.iconTileSmall)
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, style = AppTheme.typography.h4, color = AppTheme.colors.text, modifier = Modifier.weight(1f))
                if (trailing != null) Pill(trailing, accent())
            }
            Spacer(Modifier.height(2.dp))
        }
        content()
    }
}

/** Small rounded label, tinted with [color]. */
@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = AppTheme.typography.label2,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Big tinted percentage, top-right of resource cards. */
@Composable
fun MetricBadge(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = AppTheme.typography.metric,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** Rounded bar; the fill glides to the new value on each poll. */
@Composable
fun UsageBar(value: Double, color: Color) {
    val fraction by animateFloatAsState((value / 100).toFloat().coerceIn(0f, 1f), label = "usage")
    Box(
        Modifier.fillMaxWidth().height(Dimens.barHeight).clip(CircleShape).background(AppTheme.colors.outline),
    ) {
        Box(
            Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color))),
        )
    }
}

/** One line: label on the left, value on the right, both cut with … when long. */
@Composable
fun KV(key: String, value: String, valueColor: Color = AppTheme.colors.text, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            key,
            style = AppTheme.typography.body2,
            color = muted(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = AppTheme.typography.body2,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 190.dp),
        )
    }
}

@Composable
fun ListCard(title: String, rows: List<Pair<String, String>>, empty: String, mono: Boolean = false, icon: ImageVector? = null) {
    SectionCard(title, trailing = if (rows.isNotEmpty()) rows.size.toString() else null, icon = icon) {
        if (rows.isEmpty() && empty.isNotEmpty()) Note(empty)
        rows.take(MAX_ROWS).forEach { (k, v) -> KV(k, v, mono = mono) }
    }
}

const val MAX_ROWS = 30

data class Tile(
    val label: String,
    val value: String,
    val note: String = "",
    val pct: Double? = null,
    val warn: Double = 70.0,
    val crit: Double = 90.0,
)

/** Two-column grid of metric tiles. */
@Composable
fun Tiles(tiles: List<Tile>) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                pair.forEach { TileCard(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TileCard(t: Tile, modifier: Modifier) {
    val color = t.pct?.let { pctColor(it, t.warn, t.crit) } ?: AppTheme.colors.text
    GlassCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.label, style = AppTheme.typography.label2, color = muted(), maxLines = 1)
            Text(t.value, style = AppTheme.typography.metric, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.note.isNotEmpty()) {
                Text(t.note, style = AppTheme.typography.body3, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (t.pct != null) UsageBar(t.pct, color)
    }
}

/** Line through [points] with smooth corners, plus the area under it faded to transparent. */
fun DrawScope.areaLine(points: List<Offset>, color: Color, fillAlpha: Float = 0.3f) {
    if (points.size < 2) return
    val line = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val mx = (a.x + b.x) / 2
            cubicTo(mx, a.y, mx, b.y, b.x, b.y)
        }
    }
    val area = Path().apply {
        addPath(line)
        lineTo(points.last().x, size.height)
        lineTo(points.first().x, size.height)
        close()
    }
    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = fillAlpha), Color.Transparent)))
    drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Area chart of the last minutes; x = seconds before the newest sample. */
@Composable
fun HistoryChart(series: List<Series>?, format: (Double) -> String) {
    SectionCard("Actividad reciente", icon = Ic.Activity) {
        if (series == null) {
            Loading()
            return@SectionCard
        }
        val colors = listOf(AppTheme.colors.primary, AppTheme.colors.info)
        val all = series.flatMap { it.second }
        if (all.size < 2) {
            Note("juntando datos…")
            return@SectionCard
        }
        val last = all.maxOf { it.time }
        val first = all.minOf { it.time }
        val maxV = all.maxOf { it.value }.coerceAtLeast(1e-9)
        val grid = AppTheme.colors.outline

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(format(maxV), style = AppTheme.typography.label3, color = muted(), modifier = Modifier.weight(1f))
            series.forEachIndexed { i, (name, _) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                    Dot(colors[i % colors.size], 6.dp)
                    Text(" $name", style = AppTheme.typography.label3, color = muted())
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val span = (last - first).coerceAtLeast(1).toFloat()
            for (f in listOf(0f, 0.5f, 1f)) {
                drawLine(grid, start = Offset(0f, size.height * f), end = Offset(size.width, size.height * f), strokeWidth = 1f)
            }
            series.forEachIndexed { i, (_, points) ->
                areaLine(
                    points.map { Offset((it.time - first) / span * size.width, size.height - (it.value / maxV).toFloat() * size.height) },
                    colors[i % colors.size],
                )
            }
        }
        Row {
            Text("hace ${dur((last - first).toDouble())}", style = AppTheme.typography.label3, color = muted(), modifier = Modifier.weight(1f))
            Text("ahora", style = AppTheme.typography.label3, color = muted())
        }
    }
}

/** Go-data's mark, as on the web console: "GD" on a violet gradient square. */
@Composable
fun LogoBadge(size: Dp = 48.dp) {
    val p = AppTheme.colors.primary
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        Modifier.size(size).clip(shape)
            .background(Brush.linearGradient(listOf(p, p.copy(red = p.red * 0.55f, green = p.green * 0.5f))))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text("GD", color = Color.White, style = AppTheme.typography.h1.copy(fontSize = (size.value * 0.36f).sp, letterSpacing = 0.sp))
    }
}
