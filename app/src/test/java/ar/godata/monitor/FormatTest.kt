package ar.godata.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun formats() {
        assertEquals("0 B", bytes(0.0))
        assertEquals("1.5 KiB", bytes(1536.0))
        assertEquals("4.0 GiB", kb(4L * 1024 * 1024))
        assertEquals("12.3 %", pct(12.34))
        assertEquals("45s", dur(45.0))
        assertEquals("1h 1m", dur(3700.0))
        assertEquals("1d 1h", dur(90000.0))
        assertEquals("13/11 23:19", whenTs("2026-11-13T23:19:48.12-03:00"))
        assertEquals("—", whenTs("0001-01-01T00:00:00Z"))
        assertEquals("23:19:48", clock("2026-11-13T23:19:48Z"))
    }
}
