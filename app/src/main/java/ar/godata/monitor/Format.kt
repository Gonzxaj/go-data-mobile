package ar.godata.monitor

import java.util.Locale

fun pct(v: Double) = String.format(Locale.US, "%.1f %%", v)

fun num(v: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", v)

fun bytes(b: Double): String {
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var v = maxOf(b, 0.0)
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "${v.toLong()} B" else "${num(v, 1)} ${units[i]}"
}

fun kb(k: Long) = bytes(k * 1024.0)

fun rate(bps: Double) = "${bytes(bps)}/s"

fun dur(secs: Double): String {
    val s = maxOf(secs, 0.0).toLong()
    val d = s / 86400
    val h = s % 86400 / 3600
    val m = s % 3600 / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}

/** RFC 3339 from the server, in its own timezone -> `13/11 23:19`. */
fun whenTs(ts: String): String {
    if (ts.startsWith("0001")) return "—" // Go's zero time.Time
    if (ts.length < 16) return ts.ifBlank { "—" }
    return "${ts.substring(8, 10)}/${ts.substring(5, 7)} ${ts.substring(11, 16)}"
}

/** RFC 3339 -> `23:19:48`. */
fun clock(ts: String) = if (ts.length >= 19) ts.substring(11, 19) else ts.ifBlank { "—" }
