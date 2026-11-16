package ar.godata.monitor

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ar.godata.monitor.lumo.AppTheme
import ar.godata.monitor.ui.App

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark: light system-bar icons regardless of the system theme.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent { AppTheme { App(vm) } }
    }

    // Polling pauses while the app is in the background.
    override fun onStart() {
        super.onStart()
        vm.visible = true
    }

    override fun onStop() {
        super.onStop()
        vm.visible = false
    }
}
