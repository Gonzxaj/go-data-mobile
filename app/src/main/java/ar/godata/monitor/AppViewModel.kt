package ar.godata.monitor

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val POLL_MS = 3_000L

enum class Screen { Connecting, Url, Login, Dashboard, Offline }

/** Wait between automatic reconnection attempts while the server is down. */
const val RETRY_MS = 5_000L

/** All app state. HTTP runs on Dispatchers.IO; state is only touched on the main thread. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    // shortcut: token in app-private SharedPreferences, unencrypted; move to Android Keystore if the threat model needs it.
    private val prefs = app.getSharedPreferences("server", Context.MODE_PRIVATE)

    var saved by mutableStateOf(load(app))
        private set
    var screen by mutableStateOf(if (saved.url.isEmpty()) Screen.Url else Screen.Connecting)
        private set
    var url by mutableStateOf(saved.url)
    var user by mutableStateOf("")
    var pass by mutableStateOf("")
    var error by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var section by mutableStateOf(Section.General)
    var data by mutableStateOf(Data())
        private set

    /** The server stopped answering while on the dashboard; polling keeps retrying. */
    var offline by mutableStateOf(false)
        private set

    /** False while the activity is stopped: polling pauses. */
    var visible by mutableStateOf(true)

    private var poller: Job? = null
    private var retry: Job? = null

    init {
        if (saved.url.isNotEmpty()) connect()
    }

    private fun load(app: Application): Saved {
        prefs.getString("url", null)?.let { return Saved(it, prefs.getString("token", null)) }
        // The old CreamUI build kept the server in files/server.json; carry it over once.
        val old = File(app.filesDir, "server.json")
        val migrated = runCatching { json.decodeFromString<Saved>(old.readText()) }.getOrNull() ?: return Saved()
        prefs.edit().putString("url", migrated.url).putString("token", migrated.token).apply()
        old.delete()
        return migrated
    }

    private fun save(s: Saved) {
        prefs.edit().putString("url", s.url).putString("token", s.token).apply()
        saved = s
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun fail(to: Screen, msg: String) {
        busy = false
        error = msg
        screen = to
    }

    /** Asks the server which auth mode it uses, with the saved token if any. */
    private fun connect() {
        retry?.cancel()
        busy = true
        error = ""
        viewModelScope.launch {
            try {
                when (io { Api(saved).access() }) {
                    Access.Open -> startPolling()
                    Access.NeedLogin -> {
                        if (saved.token != null) save(saved.copy(token = null)) // expired or revoked
                        screen = Screen.Login
                    }
                    Access.Basic -> fail(Screen.Url, "El servidor usa AUTH_MODE=basic, todavía no soportado.")
                }
            } catch (e: ApiException) {
                if (e.down) {
                    // Server unreachable: offer to wait, change server or log out; retry on our own meanwhile.
                    fail(Screen.Offline, e.message ?: "error")
                    retry = viewModelScope.launch {
                        delay(RETRY_MS)
                        snapshotFlow { visible }.first { it }
                        if (screen == Screen.Offline) connect()
                    }
                } else {
                    fail(Screen.Url, e.message ?: "error")
                }
            } finally {
                busy = false
            }
        }
    }

    /** "Reintentar ahora": reconnects from the offline screen, or restarts polling on the dashboard. */
    fun retryNow() {
        if (screen == Screen.Dashboard) startPolling() else connect()
    }

    fun submitUrl() {
        val u = url.trim().trimEnd('/')
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            error = "La URL tiene que empezar con http:// o https://"
            return
        }
        if (saved.url != u) save(Saved(u, null))
        connect()
    }

    fun login() {
        if (user.isBlank() || pass.isEmpty()) {
            error = "Completá usuario y contraseña."
            return
        }
        busy = true
        error = ""
        viewModelScope.launch {
            try {
                val token = io { Api(saved).login(user, pass) }
                pass = ""
                save(saved.copy(token = token))
                startPolling()
            } catch (e: ApiException) {
                fail(Screen.Login, e.message ?: "error")
            } finally {
                busy = false
            }
        }
    }

    private fun startPolling() {
        screen = Screen.Dashboard
        poller?.cancel()
        poller = viewModelScope.launch {
            var haveHost = false // static info: once per poller
            var fails = 0
            // Restarts the loop right away on a section change; idles while in background.
            snapshotFlow { section to visible }.collectLatest { (s, isVisible) ->
                if (!isVisible) return@collectLatest
                while (true) {
                    try {
                        val d = io { Api(saved).fetch(s, !haveHost && s == Section.General) }
                        haveHost = haveHost || d.host != null
                        data = data.merge(d)
                        error = ""
                        fails = 0
                        offline = false
                    } catch (e: ApiException) {
                        if (e.unauthorized) {
                            save(saved.copy(token = null))
                            fail(Screen.Login, "La sesión venció, volvé a entrar.")
                            poller?.cancel()
                            return@collectLatest
                        }
                        Log.w("godata", "poll ${s.label}: ${e.message}")
                        error = e.message ?: "error" // keep the last data, retry next tick
                        // One lost poll is a hiccup; two in a row (~6 s) means the server is down.
                        if (e.down && ++fails >= 2) offline = true
                    }
                    delay(POLL_MS)
                }
            }
        }
    }

    fun logout() {
        poller?.cancel()
        retry?.cancel()
        offline = false
        val s = saved
        viewModelScope.launch { runCatching { io { Api(s).logout() } } } // best effort: the token is dropped locally anyway
        save(saved.copy(token = null))
        data = Data()
        error = ""
        screen = Screen.Login
    }

    fun changeServer() {
        poller?.cancel()
        retry?.cancel()
        offline = false
        data = Data() // other server, other host info
        url = saved.url
        error = ""
        screen = Screen.Url
    }
}
