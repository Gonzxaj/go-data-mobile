package ar.godata.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiTest {
    @Test
    fun parsesSessionCookie() {
        assertEquals("abc123", cookieValue("godata_session=abc123; Path=/; HttpOnly"))
        assertNull(cookieValue("godata_session=; Max-Age=0"))
        assertNull(cookieValue("other=1; Path=/"))
        assertNull(cookieValue("godata_session_x=1"))
    }

    @Test
    fun decodesStatsWithNullsAndUnknownFields() {
        val s = json.decodeFromString<Stats>(
            """{"time":"2026-11-13T10:00:00Z","cpu":{"usage_pct":12.5,"per_core":null},
               "ram":{"used_kb":1024,"total_kb":4096,"used_pct":25},"disks":[{"mount":"/","used_pct":50,"fs_type":"ext4"}],
               "disk_io":null,"net":null,"temp":[{"celsius":40,"is_primary_cpu":true}],"processes":null,"extra":1}""",
        )
        assertEquals(12.5, s.cpu.usagePct, 0.0)
        assertEquals(4096L, s.ram.totalKb)
        assertEquals("ext4", s.rootDisk!!.fsType)
        assertEquals(40.0, s.primaryTemp!!.celsius, 0.0)
        assertTrue(s.net.isEmpty() && s.diskIo.isEmpty() && s.processes.isEmpty())
    }

    @Test
    fun mergeKeepsUnfetchedFields() {
        val d = Data(host = HostInfo(hostname = "srv")).merge(Data(stats = Stats()))
        assertEquals("srv", d.host!!.hostname)
        assertTrue(d.stats != null)
    }

    /** Full flow against a real server in session mode; skipped unless GODATA_URL is set:
     *  GODATA_URL=http://127.0.0.1:19245 GODATA_USER=admin GODATA_PASS=... ./gradlew testDebugUnitTest */
    @Test
    fun liveSessionFlow() {
        val url = System.getenv("GODATA_URL") ?: return
        val anon = Api(Saved(url))
        assertEquals(Access.NeedLogin, anon.access())
        val token = anon.login(System.getenv("GODATA_USER")!!, System.getenv("GODATA_PASS")!!)
        val api = Api(Saved(url, token))
        assertEquals(Access.Open, api.access())
        for (s in Section.entries) api.fetch(s, withHost = true)
        api.logout()
        val e = runCatching { api.fetch(Section.General, false) }.exceptionOrNull() as ApiException
        assertTrue(e.unauthorized)
    }
}
