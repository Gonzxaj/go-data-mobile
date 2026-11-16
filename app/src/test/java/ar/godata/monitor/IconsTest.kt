package ar.godata.monitor

import ar.godata.monitor.ui.Ic
import org.junit.Assert.assertTrue
import org.junit.Test

class IconsTest {
    /** The icons are SVG path strings parsed at runtime: a typo would crash the screen that draws it. */
    @Test
    fun everyIconParses() {
        val icons = with(Ic) {
            listOf(
                Database, Home, Activity, Shield, Settings, Cpu, Memory, Disk, Wifi, Network, Box, Clock,
                Refresh, Bars, Award, Thermometer, Layers, Bell, Check, Server, Globe, Swap, LogOut, Alert,
            )
        }
        icons.forEach { assertTrue(it.root.size > 0) }
    }
}
