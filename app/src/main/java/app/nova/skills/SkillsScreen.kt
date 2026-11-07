package app.nova.skills

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.nova.MainActivity
import app.nova.chat.ChatEngine
import app.nova.data.MemoryItem
import app.nova.settings.Settings
import app.nova.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class Skill(val id: String, val icon: ImageVector, val th: String, val en: String, val dTh: String, val dEn: String, val ai: Boolean = false)

private val SKILLS = listOf(
    Skill("timer", Icons.Rounded.Timer, "ตั้งเวลา", "Timer", "เปิดแอปนาฬิกาพร้อมตั้งเวลาให้", "Opens your Clock app with a timer ready"),
    Skill("reminder", Icons.Rounded.Alarm, "เตือนความจำ", "Reminder", "แจ้งเตือนในอีกกี่นาที", "Notify you after a number of minutes"),
    Skill("note", Icons.Rounded.StickyNote2, "จดโน้ต", "Save note", "บันทึกโน้ตไว้ในหน้าความจำ", "Save a note to the Memory tab"),
    Skill("openapp", Icons.Rounded.Apps, "เปิดแอป", "Open app", "เลือกแอปที่ติดตั้งแล้วเพื่อเปิด", "Pick an installed app to open"),
    Skill("openurl", Icons.Rounded.Public, "เปิดเว็บ", "Open website", "เปิดเว็บไซต์ในเบราว์เซอร์", "Open a website in your browser"),
    Skill("battery", Icons.Rounded.BatteryFull, "แบตเตอรี่", "Battery", "ดูระดับแบตเตอรี่", "Check the battery level"),
    Skill("datetime", Icons.Rounded.Schedule, "วันและเวลา", "Date & time", "ดูวันที่และเวลาตอนนี้", "Show the current date and time"),
    Skill("summarize", Icons.Rounded.Summarize, "สรุปข้อความ", "Summarize", "สรุปข้อความที่คุณวางให้", "Summarize text you paste", true),
    Skill("explain", Icons.Rounded.School, "อธิบายเรื่องยาก", "Explain", "อธิบายหัวข้อให้เข้าใจง่าย", "Explain a topic simply", true),
    Skill("ask", Icons.Rounded.Chat, "ถามเรื่องทั่วไป", "Everyday questions", "ไปที่แชทเพื่อถามได้เลย", "Jump to chat and ask anything"),
    Skill("ideas", Icons.Rounded.Lightbulb, "ช่วยคิดไอเดีย", "Generate ideas", "ระดมไอเดียตามโจทย์", "Brainstorm ideas for a prompt", true),
    Skill("translate", Icons.Rounded.Translate, "แปลภาษา", "Translate", "แปลไทย–อังกฤษ", "Thai–English translation", true),
    Skill("organize", Icons.Rounded.Sort, "จัดระเบียบโน้ต", "Organize notes", "จัดโน้ตให้เป็นหมวดหมู่", "Group notes into clean sections", true)
)

@Composable
fun SkillsScreen(engine: ChatEngine, s: Settings, onOpenChat: () -> Unit) {
    var active by remember { mutableStateOf<Skill?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Text(t("สกิล", "Skills"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
        Text(
            t("สกิลที่มีเครื่องหมาย ✦ ใช้ AI (ข้อความจะถูกส่งไปยังผู้ให้บริการที่คุณตั้งค่าไว้)", "Skills marked ✦ use AI (your text is sent to the provider you configured)."),
            color = NovaColors.Muted, style = MaterialTheme.typography.bodySmall
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
            items(SKILLS, key = { it.id }) { k ->
                NovaCard(Modifier.fillMaxWidth().clickable {
                    if (k.id == "ask") onOpenChat() else active = k
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(NovaColors.Purple.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(k.icon, null, tint = NovaColors.Purple)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text((if (s.lang == "th") k.th else k.en) + if (k.ai) "  ✦" else "", style = MaterialTheme.typography.titleSmall)
                            Text(if (s.lang == "th") k.dTh else k.dEn, color = NovaColors.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = NovaColors.Muted)
                    }
                }
            }
        }
    }
    active?.let { SkillDialog(it, engine, s) { active = null } }
}

@Composable
private fun SkillDialog(k: Skill, engine: ChatEngine, s: Settings, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val title = if (s.lang == "th") k.th else k.en
    val cancel = t("ยกเลิก", "Cancel")
    val close = t("ปิด", "Close")

    when (k.id) {
        "battery", "datetime" -> {
            var text by remember { mutableStateOf(if (k.id == "battery") Skills.battery(ctx, s.lang) else Skills.dateTime(s.lang)) }
            AlertDialog(
                onDismissRequest = onClose, title = { Text(title) }, text = { Text(text, style = MaterialTheme.typography.titleMedium) },
                confirmButton = { TextButton(onClick = onClose) { Text(close) } },
                dismissButton = {
                    TextButton(onClick = { text = if (k.id == "battery") Skills.battery(ctx, s.lang) else Skills.dateTime(s.lang) }) { Text(t("รีเฟรช", "Refresh")) }
                }
            )
        }
        "timer" -> {
            var min by remember { mutableStateOf("5") }
            var label by remember { mutableStateOf("") }
            var err by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = onClose, title = { Text(title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(min, { min = it.filter(Char::isDigit).take(4) }, label = { Text(t("นาที", "Minutes")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(label, { label = it }, label = { Text(t("ชื่อ (ไม่บังคับ)", "Label (optional)")) }, singleLine = true)
                        if (err) Text(t("เปิดแอปนาฬิกาไม่ได้", "Couldn't open a Clock app"), color = NovaColors.Danger)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val m = min.toIntOrNull() ?: 0
                        if (m > 0 && Skills.setTimer(ctx, m * 60, label)) onClose() else err = true
                    }) { Text(t("ตั้งเวลา", "Set timer")) }
                },
                dismissButton = { TextButton(onClick = onClose) { Text(cancel) } }
            )
        }
        "reminder" -> {
            var min by remember { mutableStateOf("10") }
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = onClose, title = { Text(title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(text, { text = it }, label = { Text(t("เตือนเรื่อง", "Remind me about")) })
                        OutlinedTextField(min, { min = it.filter(Char::isDigit).take(4) }, label = { Text(t("อีกกี่นาที", "In how many minutes")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text(t("การเตือนอาจช้าเล็กน้อยเมื่อเปิดโหมดประหยัดแบต", "May fire slightly late under battery saver."), color = NovaColors.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                },
                confirmButton = {
                    TextButton(enabled = text.isNotBlank() && (min.toIntOrNull() ?: 0) > 0, onClick = {
                        (ctx as? MainActivity)?.ensureNotifPermission()
                        Skills.scheduleReminder(ctx, text.trim(), min.toInt())
                        onClose()
                    }) { Text(t("สร้าง", "Create")) }
                },
                dismissButton = { TextButton(onClick = onClose) { Text(cancel) } }
            )
        }
        "note" -> {
            var text by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            AlertDialog(
                onDismissRequest = onClose, title = { Text(title) },
                text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text(t("โน้ต", "Note")) }) },
                confirmButton = {
                    TextButton(enabled = text.isNotBlank(), onClick = {
                        scope.launch { engine.memoryDao.insert(MemoryItem(text = text.trim(), kind = "note")) }
                        onClose()
                    }) { Text(t("บันทึก", "Save")) }
                },
                dismissButton = { TextButton(onClick = onClose) { Text(cancel) } }
            )
        }
        "openurl" -> {
            var url by remember { mutableStateOf("") }
            var confirm by remember { mutableStateOf(false) }
            if (!confirm) AlertDialog(
                onDismissRequest = onClose, title = { Text(title) },
                text = { OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("URL") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)) },
                confirmButton = { TextButton(enabled = url.isNotBlank(), onClick = { confirm = true }) { Text(t("ถัดไป", "Next")) } },
                dismissButton = { TextButton(onClick = onClose) { Text(cancel) } }
            ) else AlertDialog(
                onDismissRequest = onClose, title = { Text(t("ยืนยันการเปิดเว็บ", "Confirm")) },
                text = { Text(t("เปิด ", "Open ") + url.trim() + "?") },
                confirmButton = { TextButton(onClick = { Skills.openUrl(ctx, url); onClose() }) { Text(t("เปิด", "Open")) } },
                dismissButton = { TextButton(onClick = onClose) { Text(cancel) } }
            )
        }
        "openapp" -> {
            val apps = remember { Skills.installedApps(ctx) }
            var q by remember { mutableStateOf("") }
            var pick by remember { mutableStateOf<AppInfo?>(null) }
            val p = pick
            if (p == null) AlertDialog(
                onDismissRequest = onClose, title = { Text(title) },
                text = {
                    Column {
                        OutlinedTextField(q, { q = it }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth())
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(apps.filter { q.isBlank() || it.label.contains(q, true) }, key = { it.pkg }) { a ->
                                Text(a.label, Modifier.fillMaxWidth().clickable { pick = a }.padding(vertical = 12.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onClose) { Text(close) } }
            ) else AlertDialog(
                onDismissRequest = onClose, title = { Text(t("ยืนยันการเปิดแอป", "Confirm")) },
                text = { Text(t("เปิด ", "Open ") + p.label + "?") },
                confirmButton = { TextButton(onClick = { Skills.openApp(ctx, p.pkg); onClose() }) { Text(t("เปิด", "Open")) } },
                dismissButton = { TextButton(onClick = onClose) { Text(cancel) } }
            )
        }
        else -> AiSkillDialog(k, engine, s, title, onClose)
    }
}

@Composable
private fun AiSkillDialog(k: Skill, engine: ChatEngine, s: Settings, title: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var target by remember { mutableStateOf(if (s.lang == "th") "English" else "Thai") }
    var saved by remember { mutableStateOf(false) }
    val langName = if (s.lang == "th") "Thai" else "English"

    fun system() = when (k.id) {
        "summarize" -> "Summarize the user's text concisely in $langName. Use short bullet points when helpful. Output only the summary."
        "explain" -> "Explain the user's topic simply, as to a curious beginner, in $langName. Include one concrete example."
        "ideas" -> "Generate 8 creative, practical ideas for the user's request as a short numbered list in $langName."
        "translate" -> "Translate the user's text into $target. Output only the translation."
        else -> "Organize the user's notes into clear grouped sections with headings and bullet points. Keep their meaning and language. Output only the organized notes."
    }

    AlertDialog(
        onDismissRequest = { job?.cancel(); onClose() },
        title = { Text(title + "  ✦") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (k.id == "translate") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovaChip(target == "English", t("เป็นอังกฤษ", "To English")) { target = "English" }
                    NovaChip(target == "Thai", t("เป็นไทย", "To Thai")) { target = "Thai" }
                }
                OutlinedTextField(input, { input = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                    label = { Text(if (k.id == "explain" || k.id == "ideas") t("หัวข้อ / โจทย์", "Topic / prompt") else t("ข้อความ", "Text")) })
                if (k.id == "organize") TextButton(onClick = {
                    scope.launch { input = engine.memoryDao.allNow().filter { it.kind == "note" }.joinToString("\n") { "- " + it.text } }
                }) { Text(t("ใช้โน้ตที่บันทึกไว้", "Use my saved notes")) }
                if (running) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text(t("กำลังประมวลผล…", "Working…"), color = NovaColors.Muted)
                }
                error?.let { Text(it, color = NovaColors.Danger, style = MaterialTheme.typography.bodySmall) }
                if (result.isNotEmpty()) {
                    Surface(color = NovaColors.Surface2, shape = MaterialTheme.shapes.medium) {
                        Text(result, Modifier.padding(10.dp).heightIn(max = 240.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row {
                        TextButton(onClick = { clip.setText(AnnotatedString(result)) }) { Text(t("คัดลอก", "Copy")) }
                        TextButton(enabled = !saved, onClick = {
                            scope.launch { engine.memoryDao.insert(MemoryItem(text = result, kind = "note")); saved = true }
                        }) { Text(if (saved) t("บันทึกแล้ว", "Saved") else t("เก็บเป็นโน้ต", "Save as note")) }
                    }
                }
            }
        },
        confirmButton = {
            if (running) TextButton(onClick = { job?.cancel(); running = false }) { Text(t("หยุด", "Stop")) }
            else TextButton(enabled = input.isNotBlank(), onClick = {
                running = true; error = null; result = ""; saved = false
                job = scope.launch {
                    try { result = engine.oneShot(system(), input.trim()) }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { error = engine.friendly(e) }
                    finally { running = false }
                }
            }) { Text(t("เริ่ม", "Run")) }
        },
        dismissButton = { TextButton(onClick = { job?.cancel(); onClose() }) { Text(t("ปิด", "Close")) } }
    )
}
