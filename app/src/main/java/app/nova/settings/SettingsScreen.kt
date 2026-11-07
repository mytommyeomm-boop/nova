package app.nova.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.nova.MainActivity
import app.nova.chat.ChatEngine
import app.nova.container
import app.nova.overlay.Overlay
import app.nova.overlay.OverlayGate
import app.nova.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(engine: ChatEngine, s: Settings) {
    val ctx = LocalContext.current
    val store = ctx.container.settings
    val scope = rememberCoroutineScope()
    val keyPresent by store.keyPresent.collectAsState()

    var keyText by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var keyMsg by remember { mutableStateOf<String?>(null) }
    var model by remember(s.model) { mutableStateOf(s.model) }
    var baseUrl by remember(s.baseUrl) { mutableStateOf(s.baseUrl) }
    var persona by remember(s.persona) { mutableStateOf(s.persona) }
    var askOverlay by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmClearChat by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val json = engine.exportJson()
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            msg = if (s.lang == "th") "ส่งออกแล้ว" else "Exported"
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text(t("ตั้งค่า", "Settings"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
        msg?.let { Text(it, color = NovaColors.Cyan, style = MaterialTheme.typography.labelMedium) }

        // ---------- AI ----------
        SectionLabel(t("AI", "AI"))
        NovaCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NovaChip(s.provider == "gemini", "Gemini") { store.save { it[Keys.provider] = "gemini" } }
                NovaChip(s.provider == "openai", t("OpenAI-compatible", "OpenAI-compatible")) { store.save { it[Keys.provider] = "openai" } }
            }
            OutlinedTextField(
                model, { model = it }, label = { Text(t("โมเดล", "Model")) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            if (model != s.model) TextButton(onClick = { store.save { it[Keys.model] = model.trim() } }) { Text(t("บันทึกโมเดล", "Save model")) }
            if (s.provider == "openai") {
                OutlinedTextField(
                    baseUrl, { baseUrl = it }, label = { Text("Base URL") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
                if (baseUrl != s.baseUrl) TextButton(onClick = { store.save { it[Keys.baseUrl] = baseUrl.trim() } }) { Text(t("บันทึก URL", "Save URL")) }
                Text(
                    t("ใช้เซิร์ฟเวอร์ในเครื่องได้ เช่น http://127.0.0.1:8080/v1 (อนุญาต http เฉพาะ localhost)", "Local servers work too, e.g. http://127.0.0.1:8080/v1 (plain http only for localhost)."),
                    color = NovaColors.Muted, style = MaterialTheme.typography.labelSmall
                )
            }
            OutlinedTextField(
                keyText, { keyText = it }, label = { Text("API key") }, singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = keyText.isNotBlank(), onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { store.setApiKey(keyText) }
                        keyMsg = if (ok) t2(s, "บันทึกคีย์แบบเข้ารหัสแล้ว", "Key saved (encrypted)") else t2(s, "เก็บคีย์อย่างปลอดภัยไม่ได้บนเครื่องนี้", "Secure storage unavailable on this device")
                        if (ok) keyText = ""
                    }
                }) { Text(t("บันทึกคีย์", "Save key")) }
                if (keyPresent) TextButton(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { store.setApiKey("") }; keyMsg = t2(s, "ลบคีย์แล้ว", "Key removed") }
                }) { Text(t("ลบคีย์", "Remove key"), color = NovaColors.Danger) }
            }
            Text(
                keyMsg ?: if (keyPresent) t("มีคีย์บันทึกอยู่แล้ว", "A key is stored") else t("ยังไม่มีคีย์", "No key stored"),
                color = NovaColors.Muted, style = MaterialTheme.typography.labelMedium
            )
            Text(
                t("คีย์ถูกเก็บแบบเข้ารหัสบนเครื่องคุณเท่านั้น ข้อความที่คุยกับ NOVA จะถูกส่งไปให้ผู้ให้บริการคลาวด์ที่คุณเลือกประมวลผล",
                    "Your key is stored encrypted on this device only. Messages you send to NOVA are processed by the cloud provider you choose."),
                color = NovaColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp)
            )
        }

        // ---------- personality ----------
        SectionLabel(t("บุคลิกของ NOVA", "NOVA's personality"))
        NovaCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                persona, { persona = it }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                label = { Text(t("system prompt (ว่าง = ค่าเริ่มต้น)", "System prompt (empty = default)")) }
            )
            Row {
                Button(enabled = persona != s.persona, onClick = { store.save { it[Keys.persona] = persona } }) { Text(t("บันทึก", "Save")) }
                if (s.persona.isNotBlank()) TextButton(onClick = { persona = ""; store.save { it[Keys.persona] = "" } }) { Text(t("รีเซ็ต", "Reset")) }
            }
        }

        // ---------- character ----------
        SectionLabel(t("ตัวละคร", "Character"))
        NovaCard(Modifier.fillMaxWidth()) {
            SliderRow(t("ขนาดตัวละคร", "Character size"), s.charScale, 0.5f..1f) { v -> store.save { it[Keys.charScale] = v } }
            SwitchRow(t("แอนิเมชัน", "Animations"), t("ปิดเพื่อประหยัดแบตเตอรี่", "Turn off to save battery"), s.animations) { v -> store.save { it[Keys.animations] = v } }
        }

        // ---------- voice ----------
        SectionLabel(t("เสียง (บิปสไตล์เกม)", "Voice (game-style blips)"))
        NovaCard(Modifier.fillMaxWidth()) {
            SwitchRow(t("เปิดเสียงบิป", "Blip voice"), null, s.blipOn) { v -> store.save { it[Keys.blipOn] = v } }
            Text(t("โทนเสียง", "Tone"), color = NovaColors.Muted, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                NovaChip(s.blipVoice == 0, t("ทุ้ม", "Deep")) { store.save { it[Keys.blipVoice] = 0 } }
                NovaChip(s.blipVoice == 1, t("กลาง", "Mid")) { store.save { it[Keys.blipVoice] = 1 } }
                NovaChip(s.blipVoice == 2, t("แหลม", "Bright")) { store.save { it[Keys.blipVoice] = 2 } }
            }
            SliderRow(t("ความดัง", "Volume"), s.blipVolume, 0f..1f) { v -> store.save { it[Keys.blipVolume] = v } }
            SliderRow(t("ความเร็วการพูด", "Speech speed"), s.textSpeed, 0.5f..2.5f) { v -> store.save { it[Keys.textSpeed] = v } }
            OutlinedButton(onClick = { engine.replay(t2(s, "ทดสอบเสียงของ NOVA", "Testing NOVA's voice")) }) { Text(t("ทดสอบเสียง", "Test voice")) }
        }

        // ---------- response speed ----------
        SectionLabel(t("ความเร็วการตอบ", "Response speed"))
        NovaCard(Modifier.fillMaxWidth()) {
            SwitchRow(t("แสดงข้อความทันที", "Instant text"), t("ไม่พิมพ์ทีละตัวอักษร (ไม่ลดคุณภาพคำตอบ)", "Skip the typewriter effect (does not change answer quality)"), s.instantText) { v -> store.save { it[Keys.instantText] = v } }
            Text(t("ความยาวคำตอบ", "Response length"), color = NovaColors.Muted, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                NovaChip(s.maxTokens <= 250, t("สั้นมาก", "Very short")) { store.save { it[Keys.maxTokens] = 250 } }
                NovaChip(s.maxTokens in 251..700, t("ปกติ", "Normal")) { store.save { it[Keys.maxTokens] = 600 } }
                NovaChip(s.maxTokens > 700, t("ยาว", "Long")) { store.save { it[Keys.maxTokens] = 1200 } }
            }
            Text(t("ข้อความย้อนหลังที่ส่งไปด้วย", "Past messages sent with each request"), color = NovaColors.Muted, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                NovaChip(s.historyLimit <= 6, "6") { store.save { it[Keys.historyLimit] = 6 } }
                NovaChip(s.historyLimit in 7..16, "12") { store.save { it[Keys.historyLimit] = 12 } }
                NovaChip(s.historyLimit > 16, "24") { store.save { it[Keys.historyLimit] = 24 } }
            }
            SwitchRow(t("มอนิเตอร์ประสิทธิภาพ", "Performance monitor"), null, s.showPerf) { v -> store.save { it[Keys.showPerf] = v } }
            if (s.showPerf) {
                val perf by engine.perf.collectAsState()
                val ttft = if (perf.ttftMs >= 0) "${perf.ttftMs} ms" else "—"
                val total = if (perf.totalMs >= 0) "${perf.totalMs} ms" else "—"
                Text(
                    "status: ${perf.status}\nmodel: ${perf.provider} / ${perf.model}\nTTFT: $ttft\ntotal: $total\n~tokens: ${if (perf.chars > 0) perf.approxTokens else 0} (estimated)",
                    color = NovaColors.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ---------- language ----------
        SectionLabel(t("ภาษา", "Language"))
        NovaCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NovaChip(s.lang == "th", "ไทย") { store.save { it[Keys.lang] = "th" } }
                NovaChip(s.lang == "en", "English") { store.save { it[Keys.lang] = "en" } }
            }
            Text(t("ใช้กับหน้าจอ การตอบของ NOVA และการฟังเสียง", "Applies to the UI, NOVA's replies and speech recognition."), color = NovaColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
        }

        // ---------- floating ----------
        SectionLabel(t("ตัวละครลอย", "Floating companion"))
        NovaCard(Modifier.fillMaxWidth()) {
            SwitchRow(
                t("เปิดโหมดลอย", "Enable floating mode"),
                t("ต้องอนุญาต \"แสดงทับแอปอื่น\" ปิดได้ทุกเมื่อ", "Needs \"Display over other apps\". Turn off any time."),
                s.overlayEnabled
            ) { on ->
                if (!on) Overlay.disable(ctx)
                else if (Overlay.canDraw(ctx)) { (ctx as? MainActivity)?.ensureNotifPermission(); Overlay.enable(ctx) }
                else askOverlay = true
            }
            SliderRow(t("ขนาด", "Size"), s.overlaySize, 0.4f..1.2f) { v -> store.save { it[Keys.overlaySize] = v } }
            SwitchRow(t("เกาะขอบจอ", "Snap to screen edge"), null, s.overlaySnap) { v -> store.save { it[Keys.overlaySnap] = v } }
            Text(t("แตะ 1 ครั้ง = โต้ตอบ · แตะ 2 ครั้ง = แผงแชทด่วน · ลากเพื่อย้าย", "Tap = interact · double-tap = quick chat · drag to move"), color = NovaColors.Muted, style = MaterialTheme.typography.labelSmall)
        }

        // ---------- notifications ----------
        SectionLabel(t("การแจ้งเตือน", "Notifications"))
        NovaCard(Modifier.fillMaxWidth()) {
            SwitchRow(t("แจ้งเตือนเตือนความจำ", "Reminder notifications"), null, s.remindNotif) { v -> store.save { it[Keys.remindNotif] = v } }
            TextButton(onClick = {
                ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
            }) { Text(t("เปิดการตั้งค่าแจ้งเตือนของระบบ", "Open system notification settings")) }
        }

        // ---------- memory & privacy ----------
        SectionLabel(t("ความจำและความเป็นส่วนตัว", "Memory & privacy"))
        NovaCard(Modifier.fillMaxWidth()) {
            SwitchRow(t("ให้ NOVA ใช้ความจำตอนตอบ", "Let NOVA use memories in replies"), t("ส่งความจำที่ไม่ใช่โน้ตไปกับข้อความด้วย", "Non-note memories are sent along with your messages"), s.memoryEnabled) { v -> store.save { it[Keys.memoryEnabled] = v } }
            Text(t("ไมโครโฟนทำงานเมื่อคุณกดปุ่มไมค์เท่านั้น NOVA ไม่แอบฟัง ไม่อ่านหน้าจอ และไม่ควบคุมแอปอื่น", "The mic only listens after you tap the mic button. NOVA never listens in the background, reads your screen or controls other apps."), color = NovaColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 6.dp))
            OutlinedButton(onClick = { export.launch("nova-export.json") }) { Text(t("ส่งออกข้อมูล (JSON)", "Export data (JSON)")) }
            TextButton(onClick = { confirmClearChat = true }) { Text(t("ล้างประวัติแชท", "Clear chat history")) }
            TextButton(onClick = { confirmWipe = true }) { Text(t("ลบข้อมูลทั้งหมดและรีเซ็ตแอป", "Delete all data and reset app"), color = NovaColors.Danger) }
        }

        // ---------- about ----------
        SectionLabel(t("เกี่ยวกับ", "About"))
        NovaCard(Modifier.fillMaxWidth()) {
            Text("NOVA 0.1.0-beta", style = MaterialTheme.typography.titleSmall)
            Text(t("ตัวละครและเสียงเป็นงานต้นฉบับ สร้างด้วยโค้ด ไม่ได้ใช้ภาพหรือเสียงจากผลงานของผู้อื่น", "Character and voice are original and generated in code; no third-party art or audio is used."), color = NovaColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (askOverlay) {
        AlertDialog(
            onDismissRequest = { askOverlay = false },
            title = { Text(t("เปิดโหมดลอย", "Enable floating mode")) },
            text = { Text(t("Android จะพาไปหน้า \"แสดงทับแอปอื่น\" เปิดให้ NOVA แล้วกลับมาที่แอป", "Android will open the \"Display over other apps\" page. Allow NOVA, then come back.")) },
            confirmButton = { TextButton(onClick = { askOverlay = false; OverlayGate.pending = true; ctx.startActivity(Overlay.permissionIntent(ctx)) }) { Text(t("ไปที่ตั้งค่า", "Open settings")) } },
            dismissButton = { TextButton(onClick = { askOverlay = false }) { Text(t("ยกเลิก", "Cancel")) } }
        )
    }
    if (confirmClearChat) {
        AlertDialog(
            onDismissRequest = { confirmClearChat = false },
            title = { Text(t("ล้างประวัติแชท?", "Clear chat history?")) },
            confirmButton = { TextButton(onClick = { engine.clearChat(); confirmClearChat = false }) { Text(t("ล้าง", "Clear"), color = NovaColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmClearChat = false }) { Text(t("ยกเลิก", "Cancel")) } }
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(t("ลบข้อมูลทั้งหมด?", "Delete all data?")) },
            text = { Text(t("ลบแชท ความจำ การตั้งค่า และ API key ทั้งหมด แล้วกลับไปหน้าแนะนำ", "Deletes chats, memories, settings and your API key, then returns to onboarding.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch {
                        Overlay.stop(ctx)
                        engine.wipe()
                        store.resetAll()
                    }
                }) { Text(t("ลบทั้งหมด", "Delete everything"), color = NovaColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(t("ยกเลิก", "Cancel")) } }
        )
    }
}

private fun t2(s: Settings, th: String, en: String) = if (s.lang == "th") th else en

@Composable
private fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title)
            if (desc != null) Text(desc, color = NovaColors.Muted, style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onDone: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text(title, color = NovaColors.Muted, style = MaterialTheme.typography.labelMedium)
        Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { onDone(v) }, valueRange = range)
    }
}
