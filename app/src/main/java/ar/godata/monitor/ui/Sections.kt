// One composable per dashboard section, mirroring go-data's web views.
package ar.godata.monitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import ar.godata.monitor.Alarm
import ar.godata.monitor.UptimeSession
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import ar.godata.monitor.lumo.components.HorizontalDivider
import ar.godata.monitor.lumo.AppTheme
import ar.godata.monitor.lumo.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.godata.monitor.Data
import ar.godata.monitor.Section
import ar.godata.monitor.Stats
import ar.godata.monitor.bytes
import ar.godata.monitor.clock
import ar.godata.monitor.dur
import ar.godata.monitor.kb
import ar.godata.monitor.num
import ar.godata.monitor.pct
import ar.godata.monitor.rate
import ar.godata.monitor.whenTs

@Composable
fun SectionContent(section: Section, d: Data) {
    val s = d.stats ?: return Loading()
    when (section) {
        Section.General -> General(d, s)
        Section.Cpu -> Cpu(d, s)
        Section.Ram -> Ram(d, s)
        Section.Disk -> Disk(d, s)
        Section.Net -> Net(d, s)
        Section.Conns -> Conns(d)
        Section.Containers -> Containers(d, s)
        Section.Security -> Security(d)
    }
}

private fun level(v: Double, warn: Double, crit: Double, ok: String, mid: String, high: String) =
    if (v >= crit) high else if (v >= warn) mid else ok

@Composable
private fun General(d: Data, s: Stats) {
    val c = AppTheme.colors
    d.sessions?.let { Uptime(it) }

    val cores = d.host?.coresTotal ?: 0
    ResourceCard(
        "Procesador", Ic.Cpu, c.info, s.cpu.usagePct,
        Ic.Cpu to "$cores núcleos · load ${num(s.load.load1, 2)}",
        s.primaryTemp?.let { Ic.Thermometer to "${num(it.celsius, 0)} °C" },
    )
    ResourceCard(
        "Memoria RAM", Ic.Memory, c.primary, s.ram.usedPct,
        Ic.Memory to "${kb(s.ram.usedKb)} / ${kb(s.ram.totalKb)}",
        Ic.Layers to "disp. ${kb(s.ram.availableKb)}",
    )

    d.alarms?.let { Alerts(it) }

    d.host?.let { h ->
        SectionCard("Hardware", icon = Ic.Server) {
            KV("Host", h.hostname)
            KV("Sistema", "${h.osName} ${h.osVersion}")
            KV("Kernel", h.kernelVersion)
            KV("Arquitectura", h.architecture)
            KV("Virtualización", h.virtualization.ifBlank { "—" })
            KV("CPU", h.cpuModel)
            KV("Núcleos", h.coresTotal.toString())
            KV("RAM", kb(h.ramTotalKb))
            KV("Encendido", whenTs(h.bootTime))
        }
        ListCard(
            "Discos físicos",
            h.disks.map { it.device to listOf(it.model, it.kind, bytes(it.sizeBytes.toDouble())).filter(String::isNotBlank).joinToString(" · ") },
            "sin discos",
            icon = Ic.Disk,
        )
        ListCard(
            "Dispositivos USB",
            h.usbDevices.map { it.product.ifBlank { it.productId } to it.manufacturer.ifBlank { it.vendorId } },
            "sin dispositivos",
            icon = Ic.Network,
        )
    }

    d.sessions?.let { sessions ->
        ListCard(
            "Historial de sesiones",
            sessions.reversed().take(10).map { whenTs(it.start) to "${dur(it.durationSeconds)} · ${if (it.ongoing) "activa" else "terminó"}" },
            "sin sesiones",
            icon = Ic.Clock,
        )
    }
}

/** Current uptime, drawn over the durations of the latest sessions, with restart stats below. */
@Composable
private fun Uptime(sessions: List<UptimeSession>) {
    val c = AppTheme.colors
    val current = sessions.firstOrNull { it.ongoing }
    val finished = sessions.filter { !it.ongoing }.map { it.durationSeconds }
    val recent = sessions.takeLast(12).map { it.durationSeconds }
    GlassCard {
        Box {
            if (recent.size >= 2) {
                val color = c.primary
                Canvas(Modifier.matchParentSize().padding(start = 110.dp)) {
                    val max = recent.max().coerceAtLeast(1.0)
                    val step = size.width / (recent.size - 1)
                    areaLine(
                        recent.mapIndexed { i, v -> Offset(i * step, size.height * (0.95f - 0.8f * (v / max).toFloat())) },
                        color,
                        fillAlpha = 0.35f,
                    )
                }
            }
            Row {
                IconTile(Ic.Clock, c.primary)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Tiempo activo", style = AppTheme.typography.body2, color = muted())
                    Text(current?.let { dur(it.durationSeconds) } ?: "—", style = AppTheme.typography.display, color = c.text, maxLines = 1)
                    current?.let { Text("desde ${whenTs(it.start)}", style = AppTheme.typography.body3, color = muted()) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Stat("Reinicios", (sessions.size - 1).coerceAtLeast(0).toString(), Modifier.weight(1f), Ic.Refresh, c.success)
            VDivider()
            Stat("Promedio", if (finished.isEmpty()) "—" else dur(finished.average()), Modifier.weight(1f), Ic.Bars, c.info)
            VDivider()
            Stat("Récord", dur(sessions.maxOfOrNull { it.durationSeconds } ?: 0.0), Modifier.weight(1f), Ic.Award, c.warning)
        }
    }
}

@Composable
private fun VDivider() {
    Box(Modifier.padding(horizontal = 8.dp).width(1.dp).fillMaxHeight().background(AppTheme.colors.outline))
}

@Composable
private fun Alerts(alarms: List<Alarm>) {
    val c = AppTheme.colors
    val tint = when {
        alarms.isEmpty() -> c.success
        alarms.any { it.severity == "crit" } -> c.error
        else -> c.warning
    }
    GlassCard(tint = tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Ic.Bell, if (alarms.isEmpty()) c.warning else tint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Alertas activas", style = AppTheme.typography.h4, color = c.text)
                Text(
                    if (alarms.isEmpty()) "sin alertas activas" else "${alarms.size} activa${if (alarms.size == 1) "" else "s"}",
                    style = AppTheme.typography.body2,
                    color = tint,
                )
            }
            if (alarms.isEmpty()) {
                Box(Modifier.size(32.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Ic.Check, c.background, 18.dp)
                }
            } else {
                MetricBadge(alarms.size.toString(), tint)
            }
        }
        alarms.forEach { a ->
            val color = if (a.severity == "crit") c.error else c.warning
            Row(verticalAlignment = Alignment.Top) {
                Icon(Ic.Alert, color, 18.dp, Modifier.padding(top = 1.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.message, color = c.text, style = AppTheme.typography.body2)
                    Text("desde ${whenTs(a.since)}", style = AppTheme.typography.body3, color = muted())
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, icon: ImageVector? = null, tint: Color = muted()) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, tint, 20.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column {
            Text(value, style = AppTheme.typography.h4.copy(fontFeatureSettings = "tnum"), color = AppTheme.colors.text, maxLines = 1)
            Text(label, style = AppTheme.typography.label2, color = muted(), maxLines = 1)
        }
    }
}

/** Resource with an icon, a big percentage, its bar and two footnotes. [tint] is its color while healthy. */
@Composable
private fun ResourceCard(
    title: String,
    icon: ImageVector,
    tint: Color,
    value: Double,
    left: Pair<ImageVector, String>,
    right: Pair<ImageVector, String>?,
) {
    val color = pctColor(value, ok = tint)
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, color)
            Spacer(Modifier.width(14.dp))
            Text(title, style = AppTheme.typography.h4, color = AppTheme.colors.text, modifier = Modifier.weight(1f))
            MetricBadge(pct(value), color)
        }
        UsageBar(value, color)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Footnote(left, Modifier.weight(1f))
            right?.let { Footnote(it) }
        }
    }
}

@Composable
private fun Footnote(item: Pair<ImageVector, String>, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(item.first, muted(), 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(item.second, style = AppTheme.typography.body3, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Cpu(d: Data, s: Stats) {
    val c = s.cpu
    val t = s.primaryTemp
    Tiles(
        listOf(
            Tile("Uso", pct(c.usagePct), "iowait ${num(c.iowaitPct, 1)}% · sys ${num(c.systemPct, 1)}%", c.usagePct),
            Tile("Usuario", pct(c.userPct), "espacio de usuario", c.userPct),
            Tile("Sistema", pct(c.systemPct), "llamadas al kernel", c.systemPct),
            Tile("Iowait", pct(c.iowaitPct), level(c.iowaitPct, 10.0, 20.0, "sin espera", "esperando I/O", "cuello de disco"), c.iowaitPct, 10.0, 20.0),
            Tile("Steal", pct(c.stealPct), if (c.stealPct > 0) "CPU robada (VPS)" else "sin contención", c.stealPct, 5.0, 15.0),
            Tile("Load", num(s.load.load1, 2), "5m ${num(s.load.load5, 2)} · 15m ${num(s.load.load15, 2)}", s.load.normalizedPct),
            if (t == null) {
                Tile("Temperatura", "N/D", "sin sensor")
            } else {
                Tile(
                    "Temperatura", "${num(t.celsius, 1)} °C",
                    level(t.celsius, 70.0, 85.0, "normal", "elevada", "alta"),
                    ((t.celsius - 30) / 60 * 100).coerceIn(0.0, 100.0), 66.0, 91.0,
                )
            },
        ),
    )
    HistoryChart(d.history) { pct(it) }
    ListCard("Top procesos por CPU", s.processes.map { "${it.name} (${it.pid})" to pct(it.cpuPct) }, "sin datos", icon = Ic.Cpu)
}

@Composable
private fun Ram(d: Data, s: Stats) {
    val r = s.ram
    Tiles(
        listOf(
            Tile("En uso", pct(r.usedPct), kb(r.usedKb), r.usedPct),
            Tile("Disponible", kb(r.availableKb), "libre + caché + buffers"),
            Tile("Free", kb(r.freeKb), "sin caché ni buffers"),
            if (s.swap.present) Tile("Swap", pct(s.swap.usedPct), kb(s.swap.usedKb), s.swap.usedPct) else Tile("Swap", "N/D", "sin swap"),
            Tile("Caché", kb(r.cachedKb), "reclamable"),
        ),
    )
    HistoryChart(d.history) { pct(it) }
}

@Composable
private fun Disk(d: Data, s: Stats) {
    val io = s.diskIo
    val latency = io.maxOfOrNull { it.latencyMs } ?: 0.0
    val root = s.rootDisk
    val tiles = buildList {
        if (root != null) {
            add(Tile("En uso", pct(root.usedPct), bytes(root.usedBytes.toDouble()), root.usedPct))
            add(Tile("Disponible", bytes(root.availBytes.toDouble()), "libre en /"))
        } else {
            add(Tile("En uso", "—", "sin datos de /"))
        }
        add(Tile("Lectura", rate(io.sumOf { it.readBps }), "global"))
        add(Tile("Escritura", rate(io.sumOf { it.writeBps }), "global"))
        add(Tile("IOPS", "${num(io.sumOf { it.readIops + it.writeIops }, 0)} ops/s", "${io.size} discos"))
        add(Tile("Latencia I/O", "${num(latency, 1)} ms", level(latency, 20.0, 50.0, "normal", "elevada", "alta"), latency.coerceAtMost(100.0), 20.0, 50.0))
    }
    Tiles(tiles)
    HistoryChart(d.history) { pct(it) }
    SectionCard("Sistemas de archivos", trailing = s.disks.size.toString(), icon = Ic.Disk) {
        s.disks.forEachIndexed { i, x ->
            if (i > 0) HorizontalDivider(color = AppTheme.colors.outline)
            val color = pctColor(x.usedPct)
            KV(x.mount, pct(x.usedPct), color, mono = true)
            UsageBar(x.usedPct, color)
            Note("${x.fsType} · ${bytes(x.usedBytes.toDouble())} / ${bytes(x.totalBytes.toDouble())}")
        }
    }
}

@Composable
private fun Net(d: Data, s: Stats) {
    val rx = s.net.sumOf { it.rxBps }
    val tx = s.net.sumOf { it.txBps }
    val errs = s.net.sumOf { it.errorsPs + it.dropsPs }
    Tiles(
        listOf(
            Tile("Entrante", rate(rx), "↓ descarga"),
            Tile("Saliente", rate(tx), "↑ subida"),
            Tile("Consumo total", rate(rx + tx)),
            Tile("Paquetes", "${num(s.net.sumOf { it.packetsPs }, 0)} pps", "${s.net.size} interfaces"),
            Tile("Errores / drops", "${num(errs, 2)} /s", if (errs > 0) "hay errores o drops" else "sin errores", errs.coerceAtMost(100.0), 1.0, 5.0),
        ),
    )
    HistoryChart(d.history) { rate(it) }
    ListCard("Interfaces", s.net.map { it.name to "↓ ${rate(it.rxBps)}  ↑ ${rate(it.txBps)}" }, "sin interfaces", mono = true, icon = Ic.Wifi)
}

@Composable
private fun Conns(d: Data) {
    val sum = d.netSummary ?: return Loading()
    if (!sum.enabled) {
        Note("El monitoreo de conexiones está desactivado en el servidor.")
        return
    }
    if (sum.capability.isNotBlank()) Note("captura: ${sum.capability}")
    Tiles(
        buildList {
            add(Tile("Activas", sum.activeTotal.toString()))
            add(Tile("IPs únicas", sum.uniqueIps.toString()))
            add(Tile("TCP", sum.activeTcp.toString()))
            add(Tile("UDP", sum.activeUdp.toString()))
            add(Tile("Entrantes", sum.inbound.toString()))
            if (sum.outboundEnabled) add(Tile("Salientes", sum.outbound.toString()))
        },
    )
    HistoryChart(d.history) { num(it, 0) }
    ListCard("Top IPs", d.netTopIps.orEmpty().map { it.key to it.count.toString() }, "sin datos", mono = true)
    ListCard("Top puertos", d.netTopPorts.orEmpty().map { it.key to it.count.toString() }, "sin datos", mono = true)

    val flows = d.netConns.orEmpty()
    SectionCard("Conexiones activas", trailing = flows.size.toString(), icon = Ic.Network) {
        if (flows.isEmpty()) Note("sin conexiones")
        flows.take(MAX_ROWS).forEachIndexed { i, f ->
            if (i > 0) HorizontalDivider(color = AppTheme.colors.outline)
            val inbound = f.direction == "inbound"
            KV("${f.remoteIp}:${f.remotePort}", "${if (inbound) "→" else "←"} :${f.localPort}", accent(), mono = true)
            Note("${f.proto} · ${f.state} · desde ${clock(f.since)}")
        }
    }
    ListCard(
        "Últimas conexiones detectadas",
        d.netEvents.orEmpty().map { "${clock(it.time)}  ${it.remoteIp}" to "${it.proto} :${it.localPort}" },
        "sin eventos",
        mono = true,
    )
    ListCard(
        "Historial de IPs",
        d.netIpHistory.orEmpty().map { it.ip to "${it.connections} · ${whenTs(it.lastSeen)}" },
        "sin historial todavía",
        mono = true,
    )
}

@Composable
private fun Containers(d: Data, s: Stats) {
    val list = d.containers ?: return Loading()
    val k = s.sockets
    val running = list.count { it.state == "running" }
    Note("$running en ejecución de ${list.size}")
    ListCard(
        "Sockets",
        listOf(
            "TCP en uso" to k.tcpInUse.toString(),
            "TCP huérfanos" to k.tcpOrphan.toString(),
            "TCP time-wait" to k.tcpTimeWait.toString(),
            "UDP en uso" to k.udpInUse.toString(),
            "Total" to k.totalUsed.toString(),
        ),
        "",
    )
    ListCard(
        "Puertos publicados",
        list.flatMap { c -> c.portChips.filter { it.published }.map { it.label to c.name } },
        "ninguno",
        mono = true,
    )
    if (list.isEmpty()) Note("Sin contenedores (o Docker no disponible).")
    list.forEach { c ->
        val stateColor = when {
            c.state == "running" && c.health == "unhealthy" -> AppTheme.colors.error
            c.state == "running" -> AppTheme.colors.success
            c.state == "restarting" || c.state == "paused" -> AppTheme.colors.warning
            else -> AppTheme.colors.error
        }
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.name,
                    style = AppTheme.typography.h4,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Pill(c.state, stateColor)
            }
            Text(c.image, style = AppTheme.typography.body3, color = muted(), fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Note(c.status)
            if (c.state == "running") {
                Row(Modifier.fillMaxWidth()) {
                    Stat("CPU", pct(c.cpuPct), Modifier.weight(1f))
                    Stat("RAM", bytes(c.memUsedBytes.toDouble()), Modifier.weight(1f))
                    Stat("RAM %", pct(c.memPct), Modifier.weight(1f))
                }
                Note("red ↓ ${rate(c.netRxBps)}  ↑ ${rate(c.netTxBps)}")
            }
            val extra = listOfNotNull(
                c.health.takeIf { it.isNotBlank() && it != "none" }?.let { "health: $it" },
                c.restartCount.takeIf { it > 0 }?.let { "$it reinicios" },
            )
            if (extra.isNotEmpty()) Note(extra.joinToString(" · "), AppTheme.colors.warning)
            if (c.portChips.isNotEmpty()) {
                Text(
                    c.portChips.joinToString("   ") { it.label },
                    style = AppTheme.typography.body3,
                    color = accent(),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun Security(d: Data) {
    val ssh = d.ssh ?: return Loading()
    val conn = d.connEvents ?: return Loading()
    val failed = ssh.events.count { !it.success && it.action != "closed" }
    val (title, note, color) = when {
        !ssh.enabled -> Triple("SSH sin vigilar", "el servidor no tiene acceso al log de SSH", muted())
        failed > 0 -> Triple("Intentos fallidos", "$failed intentos fallidos recientes", AppTheme.colors.warning)
        else -> Triple("Sin intentos fallidos", "ningún acceso SSH fallido reciente", AppTheme.colors.success)
    }
    GlassCard(tint = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Ic.Shield, color)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = AppTheme.typography.h3, color = AppTheme.colors.text)
                Note(note, color)
            }
        }
    }

    SectionCard("Intentos de SSH", trailing = ssh.events.size.toString(), icon = Ic.Shield) {
        if (ssh.events.isEmpty()) Note(if (ssh.enabled) "sin eventos" else "no disponible")
        ssh.events.reversed().take(MAX_ROWS).forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = AppTheme.colors.outline)
            val c: Color = when {
                e.success -> AppTheme.colors.success
                e.action == "closed" -> muted()
                else -> AppTheme.colors.error
            }
            KV(e.ip, e.action, c, mono = true)
            Note("${whenTs(e.time)} · ${e.user.ifBlank { "—" }}")
        }
    }
    ListCard(
        "Conexiones entrantes vigiladas",
        conn.events.reversed().map { "${whenTs(it.time)}  ${it.ip}" to ":${it.localPort}" },
        if (conn.enabled) "sin conexiones" else "no disponible",
        mono = true,
    )
}
