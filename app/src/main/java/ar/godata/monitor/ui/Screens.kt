package ar.godata.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ar.godata.monitor.AppViewModel
import ar.godata.monitor.RETRY_MS
import ar.godata.monitor.Screen
import ar.godata.monitor.lumo.AppTheme
import ar.godata.monitor.lumo.components.Button
import ar.godata.monitor.lumo.components.ButtonVariant
import ar.godata.monitor.lumo.components.Text
import ar.godata.monitor.lumo.components.progressindicators.CircularProgressIndicator
import ar.godata.monitor.lumo.components.textfield.OutlinedTextField

@Composable
fun App(vm: AppViewModel) {
    val c = AppTheme.colors
    // Blue-black base with a faint violet glow from the top-right corner, behind every screen.
    Box(
        Modifier.fillMaxSize().background(c.background).drawBehind {
            val center = Offset(size.width * 0.9f, 0f)
            val radius = size.width * 1.1f
            drawCircle(Brush.radialGradient(listOf(c.primary.copy(alpha = 0.16f), Color.Transparent), center, radius), radius, center)
        },
    ) {
        when (vm.screen) {
            Screen.Dashboard -> Dashboard(vm)
            Screen.Connecting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    LogoBadge(64.dp)
                    CircularProgressIndicator()
                    Note("Conectando con ${vm.saved.url}…")
                }
            }
            Screen.Offline -> Form("") { OfflineContent(vm) }
            Screen.Url -> Form(vm.error) { UrlForm(vm) }
            Screen.Login -> Form(vm.error) { LoginForm(vm) }
        }
    }
}

/** Centered column for the URL and login forms, kept above the keyboard. */
@Composable
private fun Form(error: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        LogoBadge(64.dp)
        Column {
            Text("go-data", style = AppTheme.typography.h1, color = AppTheme.colors.text)
            Note("Monitor del servidor")
        }
        Spacer(Modifier.height(4.dp))
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                content()
                if (error.isNotEmpty()) Note(error, AppTheme.colors.error)
            }
        }
    }
}

@Composable
private fun UrlForm(vm: AppViewModel) {
    OutlinedTextField(
        value = vm.url,
        onValueChange = { vm.url = it },
        label = { Text("URL del servidor") },
        placeholder = { Text("http://192.168.0.10:19245") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { vm.submitUrl() }),
        modifier = Modifier.fillMaxWidth(),
    )
    if (vm.url.trim().startsWith("http://")) {
        Note("Sin HTTPS, la contraseña y la sesión viajan en texto plano.", AppTheme.colors.warning)
    }
    Button(text = "Conectar", loading = vm.busy, enabled = !vm.busy, onClick = { vm.submitUrl() }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun LoginForm(vm: AppViewModel) {
    var show by remember { mutableStateOf(false) }
    Note(vm.saved.url)
    OutlinedTextField(
        value = vm.user,
        onValueChange = { vm.user = it },
        label = { Text("Usuario") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = vm.pass,
        onValueChange = { vm.pass = it },
        label = { Text("Contraseña") },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Button(text = if (show) "Ocultar" else "Ver", variant = ButtonVariant.PrimaryGhost, onClick = { show = !show })
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.login() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(text = "Entrar", loading = vm.busy, enabled = !vm.busy, onClick = { vm.login() }, modifier = Modifier.fillMaxWidth())
    Button(text = "Cambiar servidor", variant = ButtonVariant.PrimaryGhost, onClick = { vm.changeServer() }, modifier = Modifier.fillMaxWidth())
}

/**
 * The server does not answer: say so and offer to wait (we retry on our own), change server or log out.
 * Used full screen at startup and inside the dashboard when polling keeps failing.
 */
@Composable
fun OfflineContent(vm: AppViewModel) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(Ic.Alert, c.error)
        Spacer(Modifier.width(14.dp))
        Column {
            Text("El servidor no responde", style = AppTheme.typography.h3, color = c.text)
            Note(vm.saved.url)
        }
    }
    if (vm.error.isNotEmpty()) Note(vm.error, c.error)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Ic.Refresh, muted(), 16.dp)
        Spacer(Modifier.width(8.dp))
        Note("Reintentamos solos cada ${RETRY_MS / 1000} s. Podés esperar, cambiar de servidor o salir.")
    }
    Button(text = "Reintentar ahora", loading = vm.busy, enabled = !vm.busy, onClick = { vm.retryNow() }, modifier = Modifier.fillMaxWidth())
    Button(text = "Cambiar servidor", variant = ButtonVariant.PrimaryOutlined, onClick = { vm.changeServer() }, modifier = Modifier.fillMaxWidth())
    if (vm.saved.token != null) {
        Button(text = "Salir", variant = ButtonVariant.DestructiveOutlined, onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth())
    }
}
