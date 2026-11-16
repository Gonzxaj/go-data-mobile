package ar.godata.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.godata.monitor.AppViewModel
import ar.godata.monitor.Section
import ar.godata.monitor.lumo.AppTheme
import ar.godata.monitor.lumo.Dimens
import ar.godata.monitor.lumo.components.Button
import ar.godata.monitor.lumo.components.ButtonVariant
import ar.godata.monitor.lumo.components.Scaffold
import ar.godata.monitor.lumo.components.Text

/** Bottom navigation. Only Settings is UI-only; the others map onto [Section]s, which drive polling. */
private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Inicio", Ic.Home),
    Metrics("Métricas", Ic.Activity),
    Security("Seguridad", Ic.Shield),
    Settings("Ajustes", Ic.Settings),
}

/** Sections under the Metrics tab, picked with the chip row. */
private val metricSections = listOf(Section.Cpu, Section.Ram, Section.Disk, Section.Net, Section.Conns, Section.Containers)

private val Section.icon: ImageVector
    get() = when (this) {
        Section.General -> Ic.Home
        Section.Cpu -> Ic.Cpu
        Section.Ram -> Ic.Memory
        Section.Disk -> Ic.Disk
        Section.Net -> Ic.Wifi
        Section.Conns -> Ic.Network
        Section.Containers -> Ic.Box
        Section.Security -> Ic.Shield
    }

@Composable
fun Dashboard(vm: AppViewModel) {
    var settings by rememberSaveable { mutableStateOf(false) }
    var lastMetric by rememberSaveable { mutableStateOf(Section.Cpu) }
    val tab = when {
        settings -> Tab.Settings
        vm.section == Section.General -> Tab.Home
        vm.section == Section.Security -> Tab.Security
        else -> Tab.Metrics
    }
    Scaffold(
        containerColor = Color.Transparent, // the glow behind comes from App()
        contentColor = AppTheme.colors.text,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Header(vm)
                if (tab == Tab.Metrics) {
                    MetricChips(vm.section) {
                        lastMetric = it
                        vm.section = it
                    }
                }
            }
        },
        bottomBar = {
            BottomNav(tab) { t ->
                settings = t == Tab.Settings
                when (t) {
                    Tab.Home -> vm.section = Section.General
                    Tab.Metrics -> vm.section = lastMetric
                    Tab.Security -> vm.section = Section.Security
                    Tab.Settings -> Unit
                }
            }
        },
    ) { padding: PaddingValues ->
        if (tab == Tab.Metrics && !vm.offline) {
            MetricPager(vm, Modifier.padding(padding)) { lastMetric = it }
        } else {
            // key: each screen starts scrolled to the top.
            key(tab, vm.section) {
                Page(Modifier.padding(padding)) {
                    when {
                        tab == Tab.Settings -> Settings(vm)
                        vm.offline -> GlassCard(tint = AppTheme.colors.error) { OfflineContent(vm) }
                        else -> {
                            if (vm.error.isNotEmpty()) Note(vm.error, AppTheme.colors.error)
                            SectionContent(vm.section, vm.data)
                        }
                    }
                }
            }
        }
    }
}

/** Scrollable column holding one screen's cards. */
@Composable
private fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        content()
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Metric sections side by side: swipe left/right to change. The settled page becomes vm.section
 * (which drives polling); a chip tap sets vm.section and the pager follows.
 */
@Composable
private fun MetricPager(vm: AppViewModel, modifier: Modifier, onSettle: (Section) -> Unit) {
    val pager = rememberPagerState(metricSections.indexOf(vm.section).coerceAtLeast(0)) { metricSections.size }
    LaunchedEffect(vm.section) {
        val i = metricSections.indexOf(vm.section)
        if (i >= 0 && i != pager.currentPage) pager.animateScrollToPage(i)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect {
            onSettle(metricSections[it])
            vm.section = metricSections[it]
        }
    }
    HorizontalPager(pager, modifier.fillMaxSize(), key = { metricSections[it] }) { page ->
        val s = metricSections[page]
        val current = s == vm.section
        Page {
            if (current && vm.error.isNotEmpty()) Note(vm.error, AppTheme.colors.error)
            // history is the chart of the polled section only; a neighbour peeking in mid-swipe shows a spinner, not another section's chart.
            SectionContent(s, if (current) vm.data else vm.data.copy(history = null))
        }
    }
}

@Composable
private fun Header(vm: AppViewModel) {
    val c = AppTheme.colors
    val (label, status) = when {
        vm.offline -> "Sin conexión" to c.error
        vm.error.isNotEmpty() -> "Reintentando" to c.warning
        else -> "En línea" to c.success
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoBadge()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("go-data", style = AppTheme.typography.h2, color = c.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(status)
                Spacer(Modifier.width(6.dp))
                Text(
                    vm.data.host?.hostname ?: vm.saved.url.substringAfter("://"),
                    style = AppTheme.typography.body2,
                    color = muted(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Pill(label, status)
    }
}

@Composable
private fun MetricChips(selected: Section, onSelect: (Section) -> Unit) {
    val list = rememberLazyListState()
    // Keep the selected chip on screen when the section changes by swiping.
    LaunchedEffect(selected) { metricSections.indexOf(selected).takeIf { it >= 0 }?.let { list.animateScrollToItem(it) } }
    LazyRow(
        state = list,
        contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(metricSections) { s -> NavChip(s.label, s.icon, s == selected) { onSelect(s) } }
    }
}

/** Compact tab: violet gradient with a soft glow when selected, outlined surface otherwise. */
@Composable
private fun NavChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(50)
    val base = if (selected) {
        Modifier.shadow(10.dp, shape, ambientColor = c.primary, spotColor = c.primary)
            .background(Brush.horizontalGradient(listOf(c.primary, c.primary.copy(red = c.primary.red * 0.75f))), shape)
    } else {
        Modifier.background(c.surface, shape).border(1.dp, c.outline, shape)
    }
    val tint = if (selected) Color.White else muted()
    Row(
        base.clip(shape).selectable(selected, onClick = onClick, role = Role.Tab).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, tint, 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = AppTheme.typography.label1, color = tint)
    }
}

/** Floating rounded bar; the selected item sits on a violet pill. */
@Composable
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.navRadius)
    Row(
        Modifier.navigationBarsPadding().padding(horizontal = Dimens.gutter, vertical = 8.dp).fillMaxWidth()
            .clip(shape).background(Brush.verticalGradient(listOf(c.surfaceHigh, c.surface)))
            .border(1.dp, c.outline, shape).padding(6.dp),
    ) {
        Tab.entries.forEach { t ->
            val sel = t == selected
            val tint = if (sel) c.primary else muted()
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(Dimens.navRadius - 6.dp))
                    .background(if (sel) c.primary.copy(alpha = 0.16f) else Color.Transparent)
                    .selectable(sel, onClick = { onSelect(t) }, role = Role.Tab)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, tint, 22.dp)
                Spacer(Modifier.height(3.dp))
                Text(t.label, style = AppTheme.typography.label2, color = tint, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Settings(vm: AppViewModel) {
    SectionCard("Servidor", icon = Ic.Globe) {
        KV("URL", vm.saved.url)
        vm.data.host?.let { KV("Host", it.hostname) }
        KV("Sesión", if (vm.saved.token != null) "iniciada" else "sin login")
    }
    Button(
        text = "Cambiar servidor",
        variant = ButtonVariant.PrimaryOutlined,
        onClick = { vm.changeServer() },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        text = "Salir",
        variant = ButtonVariant.DestructiveOutlined,
        onClick = { vm.logout() },
        modifier = Modifier.fillMaxWidth(),
    )
}
