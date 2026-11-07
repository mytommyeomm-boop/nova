package app.nova

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.nova.chat.ChatScreen
import app.nova.companion.CompanionScreen
import app.nova.memory.MemoryScreen
import app.nova.navigation.Dest
import app.nova.overlay.Overlay
import app.nova.overlay.OverlayGate
import app.nova.settings.SettingsScreen
import app.nova.skills.SkillsScreen
import app.nova.ui.*

class MainActivity : ComponentActivity() {

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NovaTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        container.engine.touch()
        // Returned from the system "display over other apps" screen?
        if (OverlayGate.pending && Overlay.canDraw(this)) {
            OverlayGate.pending = false
            ensureNotifPermission()
            Overlay.enable(this)
        } else if (container.settings.state.value.overlayEnabled && Overlay.canDraw(this)) {
            Overlay.start(this)
        }
    }

    @Composable
    private fun Root() {
        val s by container.settings.state.collectAsStateWithLifecycle()
        if (!s.loaded) {
            Box(Modifier.fillMaxSize().background(NovaColors.Bg))
            return
        }
        CompositionLocalProvider(LocalLang provides s.lang) {
            if (!s.onboarded) OnboardingScreen(s) else MainScaffold(s)
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun MainScaffold(s: app.nova.settings.Settings) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: Dest.COMPANION.route
        val imeVisible = WindowInsets.isImeVisible
        val engine = container.engine

        fun go(d: Dest) = nav.navigate(d.route) {
            popUpTo(Dest.COMPANION.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }

        Scaffold(
            containerColor = NovaColors.Bg,
            bottomBar = {
                if (!imeVisible) NavigationBar(containerColor = NovaColors.Surface) {
                    Dest.entries.forEach { d ->
                        NavigationBarItem(
                            selected = route == d.route,
                            onClick = { go(d) },
                            icon = { Icon(d.icon, null) },
                            label = { Text(if (s.lang == "th") d.th else d.en, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NovaColors.Cyan,
                                selectedTextColor = NovaColors.Cyan,
                                indicatorColor = NovaColors.Purple.copy(alpha = 0.22f),
                                unselectedIconColor = NovaColors.Muted,
                                unselectedTextColor = NovaColors.Muted
                            )
                        )
                    }
                }
            }
        ) { pad ->
            NavHost(nav, startDestination = Dest.COMPANION.route, modifier = Modifier.padding(pad)) {
                composable(Dest.COMPANION.route) {
                    CompanionScreen(engine, s, onOpenSettings = { go(Dest.SETTINGS) })
                }
                composable(Dest.CHAT.route) { ChatScreen(engine, s) }
                composable(Dest.MEMORY.route) { MemoryScreen(engine, s) }
                composable(Dest.SKILLS.route) { SkillsScreen(engine, s, onOpenChat = { go(Dest.CHAT) }) }
                composable(Dest.SETTINGS.route) { SettingsScreen(engine, s) }
            }
        }
    }
}
