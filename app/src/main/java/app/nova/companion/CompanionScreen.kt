package app.nova.companion

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nova.MainActivity
import app.nova.chat.ChatEngine
import app.nova.overlay.Overlay
import app.nova.overlay.OverlayGate
import app.nova.permissions.rememberMic
import app.nova.settings.Keys
import app.nova.settings.Settings
import app.nova.container
import app.nova.ui.*
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionScreen(engine: ChatEngine, s: Settings, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val mood by engine.mood.collectAsStateWithLifecycle()
    val phase by engine.phase.collectAsStateWithLifecycle()
    val shown by engine.shown.collectAsStateWithLifecycle()
    val generating by engine.generating.collectAsStateWithLifecycle()
    val bubble by engine.bubble.collectAsStateWithLifecycle()
    val error by engine.error.collectAsStateWithLifecycle()
    val partial by engine.partial.collectAsStateWithLifecycle()

    var talkOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }
    var askOverlay by remember { mutableStateOf(false) }
    val mic = rememberMic(engine, s.lang) { engine.send(it) }

    val status = when (phase) {
        Phase.LISTENING -> t("กำลังฟัง…", "Listening…")
        Phase.THINKING -> t("กำลังคิด…", "Thinking…")
        Phase.TALKING -> t("กำลังพูด…", "Talking…")
        Phase.IDLE -> t("ออนไลน์", "Online")
    }
    val bubbleText = when {
        phase == Phase.LISTENING && partial.isNotBlank() -> partial
        generating -> shown.ifEmpty { "…" }
        else -> bubble?.text ?: t("สวัสดี! แตะที่ฉันได้นะ", "Hi! You can tap me.")
    }

    Box(Modifier.fillMaxSize()) {
        Particles(s.animations)
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.statusBarsPadding().height(8.dp))
            // header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NOVA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = NovaColors.Text)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(NovaColors.Cyan, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(status, color = NovaColors.Cyan, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50), color = NovaColors.Surface2,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovaColors.Purple.copy(alpha = 0.5f))
                ) {
                    Text(
                        t("อารมณ์: ", "Mood: ") + mood.label(s.lang),
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = NovaColors.Text, style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // speech bubble
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp, max = 110.dp)
                    .background(NovaColors.Surface, RoundedCornerShape(18.dp))
                    .border(1.dp, NovaColors.Purple.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(bubbleText.takeLast(160), color = NovaColors.Text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            // character
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                CharacterView(
                    mood = mood, phase = phase, animate = s.animations,
                    modifier = Modifier.fillMaxHeight(s.charScale.coerceIn(0.5f, 1f)),
                    onTap = { engine.poke() }
                )
            }

            error?.let {
                Text(it, color = NovaColors.Danger, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 6.dp))
            }

            if (talkOpen) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), singleLine = true,
                        placeholder = { Text(t("พิมพ์คุยกับ NOVA…", "Talk to NOVA…")) }
                    )
                    IconButton(
                        onClick = { engine.send(input); input = "" },
                        enabled = input.isNotBlank() && !generating
                    ) { Icon(Icons.Rounded.Send, null, tint = NovaColors.Purple) }
                }
            }

            // controls
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Control(Icons.Rounded.ChatBubble, t("คุย", "Talk"), talkOpen) { talkOpen = !talkOpen }
                Control(if (mic.listening) Icons.Rounded.Stop else Icons.Rounded.Mic, t("ไมค์", "Mic"), mic.listening) { mic.toggle() }
                Control(Icons.Rounded.TouchApp, t("โต้ตอบ", "Poke"), false) { engine.poke() }
                Control(Icons.Rounded.KeyboardArrowDown, t("ย่อลอย", "Float"), false) {
                    if (Overlay.canDraw(ctx)) {
                        (ctx as? MainActivity)?.ensureNotifPermission()
                        Overlay.enable(ctx)
                        (ctx as? Activity)?.moveTaskToBack(true)
                    } else askOverlay = true
                }
                Control(Icons.Rounded.Tune, t("ด่วน", "Quick"), false) { sheet = true }
            }
        }
    }

    if (askOverlay) {
        AlertDialog(
            onDismissRequest = { askOverlay = false },
            title = { Text(t("เปิดโหมดลอย", "Enable floating mode")) },
            text = {
                Text(
                    t(
                        "NOVA ต้องใช้สิทธิ์ \"แสดงทับแอปอื่น\" เพื่อลอยเหนือแอปอื่น ระบบจะพาไปหน้าตั้งค่าของ Android คุณปิดได้ทุกเมื่อ",
                        "NOVA needs the \"Display over other apps\" permission to float above other apps. Android will open its settings page. You can turn it off any time."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askOverlay = false
                    OverlayGate.pending = true
                    ctx.startActivity(Overlay.permissionIntent(ctx))
                }) { Text(t("ไปที่ตั้งค่า", "Open settings")) }
            },
            dismissButton = { TextButton(onClick = { askOverlay = false }) { Text(t("ยกเลิก", "Cancel")) } }
        )
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = NovaColors.Surface) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text(t("ตั้งค่าด่วน", "Quick settings"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                QuickSwitch(t("เสียงบิป", "Blip voice"), s.blipOn) { v -> ctx.container.settings.save { it[Keys.blipOn] = v } }
                QuickSwitch(t("แอนิเมชัน", "Animations"), s.animations) { v -> ctx.container.settings.save { it[Keys.animations] = v } }
                Text(t("ขนาดตัวละคร", "Character size"), color = NovaColors.Muted, style = MaterialTheme.typography.labelMedium)
                var size by remember(s.charScale) { mutableFloatStateOf(s.charScale) }
                Slider(
                    value = size, onValueChange = { size = it },
                    onValueChangeFinished = { ctx.container.settings.save { it[Keys.charScale] = size } },
                    valueRange = 0.5f..1f
                )
                TextButton(onClick = { sheet = false; onOpenSettings() }) { Text(t("ตั้งค่าทั้งหมด", "All settings")) }
            }
        }
    }
}

@Composable
private fun QuickSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Control(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick, modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) NovaColors.Purple else NovaColors.Surface2,
                contentColor = if (active) Color(0xFF1A1030) else NovaColors.Text
            )
        ) { Icon(icon, label) }
        Text(label, color = NovaColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Particles(animate: Boolean) {
    val inf = rememberInfiniteTransition(label = "particles")
    val p = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(22000, easing = LinearEasing)), label = "pp")
    val seeds = remember { List(26) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            Brush.radialGradient(
                listOf(NovaColors.Purple.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(size.width / 2f, size.height * 0.55f), radius = size.width * 0.75f
            ),
            radius = size.width * 0.75f, center = Offset(size.width / 2f, size.height * 0.55f)
        )
        val ph = if (animate) p.value else 0f
        seeds.forEachIndexed { i, (x, y, z) ->
            val yy = (((y - ph * (0.3f + z * 0.7f)) % 1f) + 1f) % 1f
            drawCircle(
                (if (i % 2 == 0) NovaColors.Cyan else NovaColors.Purple).copy(alpha = 0.10f + 0.25f * z),
                1.5f + 3f * z, Offset(x * size.width, yy * size.height)
            )
        }
    }
}
