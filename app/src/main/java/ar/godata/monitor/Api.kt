// Blocking client for the go-data HTTP API. Call it from Dispatchers.IO only.
// Auth is go-data's own session cookie, the same one the web dashboard uses.
package ar.godata.monitor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val COOKIE = "godata_session"

/** What the user saved: the server and, in session mode, its token. */
@Serializable
data class Saved(val url: String = "", val token: String? = null)

enum class Access {
    /** AUTH_MODE=none, or session mode with a still-valid token. */
    Open,
    /** AUTH_MODE=session without a valid token. */
    NeedLogin,
    /** AUTH_MODE=basic. */
    Basic,
}

/** [message] is shown as is: go-data's own errors are already in Spanish. */
/** [down]: the server did not answer, or a proxy in front of it says it is gone (5xx); worth retrying. */
class ApiException(message: String, val unauthorized: Boolean = false, val down: Boolean = false) : Exception(message)

/** Dashboard sections, same as go-data's web.Sections (minus Usuarios). */
enum class Section(val label: String) {
    General("General"),
    Cpu("CPU"),
    Ram("Memoria"),
    Disk("Disco"),
    Net("Red"),
    Conns("Conexiones"),
    Containers("Contenedores"),
    Security("Seguridad"),
}

typealias Series = Pair<String, List<HistoryPoint>>

/** One poll's worth of data. null = not fetched this time (keep the old value). */
data class Data(
    val stats: Stats? = null,
    val host: HostInfo? = null,
    val alarms: List<Alarm>? = null,
    val sessions: List<UptimeSession>? = null,
    /** Main chart of the section. */
    val history: List<Series>? = null,
    val containers: List<Container>? = null,
    val netSummary: NetSummary? = null,
    val netTopIps: List<NetCount>? = null,
    val netTopPorts: List<NetCount>? = null,
    val netConns: List<NetFlow>? = null,
    val netEvents: List<NetFlowEvent>? = null,
    val netIpHistory: List<NetIpEntry>? = null,
    val ssh: SshEvents? = null,
    val connEvents: ConnEvents? = null,
) {
    fun merge(new: Data) = Data(
        stats = new.stats ?: stats,
        host = new.host ?: host,
        alarms = new.alarms ?: alarms,
        sessions = new.sessions ?: sessions,
        history = new.history ?: history,
        containers = new.containers ?: containers,
        netSummary = new.netSummary ?: netSummary,
        netTopIps = new.netTopIps ?: netTopIps,
        netTopPorts = new.netTopPorts ?: netTopPorts,
        netConns = new.netConns ?: netConns,
        netEvents = new.netEvents ?: netEvents,
        netIpHistory = new.netIpHistory ?: netIpHistory,
        ssh = new.ssh ?: ssh,
        connEvents = new.connEvents ?: connEvents,
    )
}

@Serializable
private data class Me(val mode: String = "")

@Serializable
private data class ErrorBody(val error: String = "")

private class Response(val code: Int, val body: String, val conn: HttpURLConnection) {
    val ok get() = code in 200..299
    fun header(name: String): String? = conn.getHeaderField(name)
    fun headers(name: String): List<String> =
        conn.headerFields.entries.filter { it.key.equals(name, ignoreCase = true) }.flatMap { it.value }

    fun error(): ApiException {
        val msg = runCatching { json.decodeFromString<ErrorBody>(body).error }.getOrNull()
        return ApiException(msg?.ifBlank { null } ?: "error HTTP $code", unauthorized = code == 401, down = code >= 500)
    }
}

class Api(private val saved: Saved) {
    private val base = saved.url.trimEnd('/')

    private fun request(method: String, path: String, body: String? = null): Response {
        try {
            val c = URL(base + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            // A redirect here means a page, not the API (e.g. /login.html): treat it as an error.
            c.instanceFollowRedirects = false
            saved.token?.let { c.setRequestProperty("Cookie", "$COOKIE=$it") }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            return Response(code, text, c)
        } catch (e: IOException) {
            throw ApiException("no se pudo conectar: ${e.message ?: e.javaClass.simpleName}", down = true)
        }
    }

    private inline fun <reified T> get(path: String): T {
        val r = request("GET", path)
        if (!r.ok) throw r.error()
        return try {
            json.decodeFromString<T>(r.body)
        } catch (e: IllegalArgumentException) {
            throw ApiException("respuesta inválida de $path")
        }
    }

    fun access(): Access {
        val r = request("GET", "/api/auth/me")
        return when {
            // "basic" is unreachable today: basic auth gates /api/auth/me too.
            r.ok -> if (runCatching { json.decodeFromString<Me>(r.body).mode }.getOrNull() == "basic") Access.Basic else Access.Open
            r.code == 401 && r.header("WWW-Authenticate") != null -> Access.Basic
            r.code == 401 -> Access.NeedLogin
            else -> throw r.error()
        }
    }

    /** Returns the session token from the Set-Cookie header. */
    fun login(user: String, pass: String): String {
        val body = buildJsonObject {
            put("username", user)
            put("password", pass)
        }.toString()
        val r = request("POST", "/api/auth/login", body)
        if (!r.ok) throw r.error()
        return r.headers("Set-Cookie").firstNotNullOfOrNull(::cookieValue)
            ?: throw ApiException("el servidor no devolvió una sesión")
    }

    fun logout() {
        request("POST", "/api/auth/logout")
    }

    private fun history(path: String, vararg series: Pair<String, String>): List<Series> =
        series.map { (label, metric) -> label to get<History>("$path?metric=$metric&points=60").points }

    /** Everything [section] shows. /api/stats always goes first: it is also the session check. */
    fun fetch(section: Section, withHost: Boolean): Data {
        val stats = get<Stats>("/api/stats")
        val h = "/api/history"
        return when (section) {
            Section.General -> Data(
                stats = stats,
                host = if (withHost) get<HostInfo>("/api/host") else null,
                alarms = get<Alarms>("/api/alarms").alarms,
                sessions = get<UptimeSessions>("/api/uptime/sessions").sessions,
            )
            Section.Cpu -> Data(stats = stats, history = history(h, "Uso %" to "cpu.usage_pct"))
            Section.Ram -> Data(stats = stats, history = history(h, "En uso %" to "ram.used_pct"))
            Section.Disk -> Data(stats = stats, history = history(h, "En uso % (/)" to "disk.used_pct"))
            Section.Net -> Data(stats = stats, history = history(h, "Entrante" to "net.rx_bps", "Saliente" to "net.tx_bps"))
            Section.Conns -> Data(
                stats = stats,
                netSummary = get<NetSummary>("/api/network/summary"),
                history = history("/api/network/history", "Activas" to "active_total"),
                netTopIps = get<NetTop>("/api/network/top?by=ip&n=10").items,
                netTopPorts = get<NetTop>("/api/network/top?by=port&n=10").items,
                netConns = get<NetConnections>("/api/network/connections").connections,
                netEvents = get<NetEvents>("/api/network/events?n=50").events,
                netIpHistory = get<NetIpHistory>("/api/network/ip-history?n=100").entries,
            )
            Section.Containers -> Data(stats = stats, containers = get<Containers>("/api/containers").containers)
            Section.Security -> Data(
                stats = stats,
                ssh = get<SshEvents>("/api/security/ssh"),
                connEvents = get<ConnEvents>("/api/security/connections"),
            )
        }
    }
}

/** `godata_session=abc; Path=/; HttpOnly` -> `abc`. */
fun cookieValue(header: String): String? {
    val first = header.substringBefore(';').trim()
    if (!first.startsWith("$COOKIE=")) return null
    return first.removePrefix("$COOKIE=").ifEmpty { null }
}
