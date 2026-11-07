package app.nova.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.nova.*
import app.nova.companion.CharacterView
import app.nova.settings.Keys
import app.nova.ui.LocalLang
import app.nova.ui.NovaColors
import app.nova.ui.NovaTheme
import kotlinx.coroutines.delay

/** Helpers used by the UI to start/stop the floating companion. */
object Overlay {
    fun canDraw(ctx: Context) = Settings.canDrawOverlays(ctx)
    fun permissionIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun start(ctx: Context) {
        if (!canDraw(ctx)) return
        ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
    }

    fun stop(ctx: Context) {
        ctx.stopService(Intent(ctx, OverlayService::class.java))
    }

    fun enable(ctx: Context) {
        ctx.container.settings.save { it[Keys.overlayEnabled] = true }
        start(ctx)
    }

    fun disable(ctx: Context) {
        ctx.container.settings.save { it[Keys.overlayEnabled] = false }
        stop(ctx)
    }
}

/** Set when the user was sent to system settings to grant "display over other apps". */
object OverlayGate {
    var pending = false
}

private class ServiceOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val reg = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = reg
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store
    fun create() { ssc.performAttach(); ssc.performRestore(null); reg.currentState = Lifecycle.State.CREATED }
    fun resume() { reg.currentState = Lifecycle.State.RESUMED }
    fun destroy() { reg.currentState = Lifecycle.State.DESTROYED; store.clear() }
}

class OverlayService : Service() {
    companion object {
        const val ACTION_HIDE = "nova.HIDE"
        const val ACTION_SHOW = "nova.SHOW"
        const val ACTION_STOP = "nova.STOP"
    }

    private lateinit var wm: WindowManager
    private val owner = ServiceOwner()
    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var hidden = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        owner.create()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        if (!Overlay.canDraw(this)) { stopSelf(); return }
        addBubble()
        owner.resume()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> setHidden(true)
            ACTION_SHOW -> setHidden(false)
            ACTION_STOP -> {
                Overlay.disable(this)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removePanel()
        bubbleView?.let { runCatching { wm.removeView(it) } }
        bubbleView = null
        owner.destroy()
        super.onDestroy()
    }

    private fun lang() = container.settings.state.value.lang

    private fun startAsForeground() {
        val l = lang()
        val open = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE
        )
        fun act(a: String, label: String, code: Int) = NotificationCompat.Action(
            0, label,
            PendingIntent.getService(this, code, Intent(this, OverlayService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE)
        )
        val n: Notification = NotificationCompat.Builder(this, Channels.OVERLAY)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("NOVA")
            .setContentText(tr(l, "คอมแพนเนียนลอยกำลังทำงาน", "Floating companion is running"))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(act(ACTION_HIDE, tr(l, "ซ่อน", "Hide"), 1))
            .addAction(act(ACTION_SHOW, tr(l, "แสดง", "Show"), 2))
            .addAction(act(ACTION_STOP, tr(l, "ปิด", "Stop"), 3))
            .build()
        ServiceCompat.startForeground(
            this, 1, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    private fun setHidden(h: Boolean) {
        hidden = h
        bubbleView?.visibility = if (h) View.GONE else View.VISIBLE
        if (h) removePanel()
    }

    private fun attach(v: ComposeView) {
        v.setViewTreeLifecycleOwner(owner)
        v.setViewTreeViewModelStoreOwner(owner)
        v.setViewTreeSavedStateRegistryOwner(owner)
    }

    private fun addBubble() {
        val s = container.settings.state.value
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = s.overlayX
            y = s.overlayY
        }
        bubbleLp = lp
        val v = ComposeView(this)
        attach(v)
        v.setContent {
            val st by container.settings.state.collectAsState()
            CompositionLocalProvider(LocalLang provides st.lang) {
                NovaTheme {
                    OverlayCharacter(
                        onDrag = { dx, dy ->
                            lp.x = (lp.x + dx).toInt().coerceAtLeast(0)
                            lp.y = (lp.y + dy).toInt().coerceAtLeast(0)
                            runCatching { wm.updateViewLayout(v, lp) }
                        },
                        onDragEnd = { snapAndSave(v, lp) },
                        onDoubleTap = { togglePanel() }
                    )
                }
            }
        }
        wm.addView(v, lp)
        bubbleView = v
    }

    private fun snapAndSave(v: View, lp: WindowManager.LayoutParams) {
        val s = container.settings.state.value
        if (s.overlaySnap) {
            val screenW = resources.displayMetrics.widthPixels
            val w = v.width
            lp.x = if (lp.x + w / 2 < screenW / 2) 0 else (screenW - w).coerceAtLeast(0)
            runCatching { wm.updateViewLayout(v, lp) }
        }
        val x = lp.x
        val y = lp.y
        container.settings.save { it[Keys.overlayX] = x; it[Keys.overlayY] = y }
    }

    private fun togglePanel() {
        if (panelView != null) removePanel() else addPanel()
    }

    private fun removePanel() {
        panelView?.let { runCatching { wm.removeView(it) } }
        panelView = null
    }

    private fun addPanel() {
        val lp = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 24
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        val v = ComposeView(this)
        attach(v)
        v.setContent {
            val st by container.settings.state.collectAsState()
            CompositionLocalProvider(LocalLang provides st.lang) {
                NovaTheme { QuickPanel(onClose = { removePanel() }) }
            }
        }
        wm.addView(v, lp)
        panelView = v
    }

    @Composable
    private fun OverlayCharacter(onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit, onDoubleTap: () -> Unit) {
        val engine = container.engine
        val st by container.settings.state.collectAsState()
        val mood by engine.mood.collectAsState()
        val phase by engine.phase.collectAsState()
        val shown by engine.shown.collectAsState()
        val generating by engine.generating.collectAsState()
        val bubble by engine.bubble.collectAsState()
        var showBubble by remember { mutableStateOf(false) }
        LaunchedEffect(bubble) {
            if (bubble != null) { showBubble = true; delay(5000); showBubble = false }
        }
        val text = if (generating) shown.takeLast(80) else if (showBubble) bubble?.text.orEmpty() else ""

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CharacterView(
                mood = mood, phase = phase, skinIndex = st.skin, compact = st.overlayCompact, animate = st.animations,
                modifier = Modifier
                    .width((200 * st.overlaySize).dp)
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = onDragEnd) { change, drag ->
                            change.consume()
                            onDrag(drag.x, drag.y)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { engine.poke() }, onDoubleTap = { onDoubleTap() })
                    }
            )
            if (text.isNotEmpty()) {
                Box(
                    Modifier
                        .widthIn(max = 220.dp)
                        .background(NovaColors.Surface, RoundedCornerShape(14.dp))
                        .border(1.dp, NovaColors.Purple.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text, color = NovaColors.Text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @Composable
    private fun QuickPanel(onClose: () -> Unit) {
        val engine = container.engine
        val msgs by engine.messages.collectAsState()
        val shown by engine.shown.collectAsState()
        val generating by engine.generating.collectAsState()
        val error by engine.error.collectAsState()
        var input by remember { mutableStateOf("") }
        val last = msgs.takeLast(4)

        Column(
            Modifier
                .background(NovaColors.Surface, RoundedCornerShape(20.dp))
                .border(1.dp, NovaColors.Cyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NOVA", color = NovaColors.Cyan, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = NovaColors.Muted) }
            }
            LazyColumn(Modifier.heightIn(max = 180.dp)) {
                items(last) { m ->
                    val mine = m.role == "user"
                    Text(
                        (if (mine) "› " else "") + m.text,
                        color = if (mine) NovaColors.Purple else NovaColors.Text,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
                if (generating) item {
                    Text(shown.ifEmpty { "…" }, color = NovaColors.Text, style = MaterialTheme.typography.bodySmall)
                }
            }
            error?.let { Text(it, color = NovaColors.Danger, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text(tr(lang(), "พิมพ์ถึง NOVA…", "Message NOVA…")) }
                )
                IconButton(onClick = { engine.send(input); input = "" }, enabled = input.isNotBlank() && !generating) {
                    Icon(Icons.Rounded.Send, null, tint = NovaColors.Purple)
                }
            }
        }
    }
}
