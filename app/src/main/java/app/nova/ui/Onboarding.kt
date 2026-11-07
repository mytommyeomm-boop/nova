package app.nova.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nova.companion.CharacterView
import app.nova.companion.Mood
import app.nova.companion.Phase
import app.nova.container
import app.nova.settings.Keys
import app.nova.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(s: Settings) {
    val ctx = LocalContext.current
    val store = ctx.container.settings
    val engine = ctx.container.engine
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var poked by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    val mood by engine.mood.collectAsStateWithLifecycle()
    val last = 5

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(NovaColors.Bg, androidx.compose.ui.graphics.Color(0xFF1A1230))))
            .statusBarsPadding().navigationBarsPadding().imePadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(progress = { (step + 1) / (last + 1f) }, Modifier.fillMaxWidth(), color = NovaColors.Cyan)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(230.dp), contentAlignment = Alignment.Center) {
            CharacterView(
                mood = if (step == 0 && !poked) Mood.HAPPY else if (step == last && !poked) Mood.IDLE else mood,
                phase = Phase.IDLE, modifier = Modifier.fillMaxHeight(),
                onTap = if (step == last) ({ poked = true; engine.poke() }) else null
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            when (step) {
                0 -> {
                    Text("NOVA", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = NovaColors.Cyan)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        t("ฉันคือ NOVA เพื่อนตัวเล็กที่อาศัยอยู่ในมือถือของคุณ คุยกับฉันได้ ฉันจะจำสิ่งที่คุณอนุญาต และช่วยงานเล็ก ๆ ได้",
                            "I'm NOVA, a small companion living in your phone. Talk to me, I'll remember what you approve, and I can help with little tasks."),
                        textAlign = TextAlign.Center, color = NovaColors.Text
                    )
                }
                1 -> {
                    Text(t("พบกับ NOVA", "Meet NOVA"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        t("NOVA มีสีหน้า 9 แบบ ขยับตัวตามจังหวะเสียงบิปตอนพูด และเปลี่ยนอารมณ์ตามบทสนทนา",
                            "NOVA has 9 expressions, bounces along with its blip voice when talking, and changes mood with the conversation."),
                        textAlign = TextAlign.Center, color = NovaColors.Text
                    )
                }
                2 -> {
                    Text(t("ภาษาของ NOVA", "NOVA's language"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NovaChip(s.lang == "th", "ไทย") { store.save { it[Keys.lang] = "th" } }
                        NovaChip(s.lang == "en", "English") { store.save { it[Keys.lang] = "en" } }
                    }
                }
                3 -> {
                    Text(t("เชื่อมต่อ AI", "Connect an AI"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NovaChip(s.provider == "gemini", "Gemini") { store.save { it[Keys.provider] = "gemini"; it[Keys.model] = "gemini-3.8-flash" } }
                        NovaChip(s.provider == "openai", "OpenAI-compatible") { store.save { it[Keys.provider] = "openai"; it[Keys.model] = "gpt-4o-mini" } }
                    }
                    OutlinedTextField(
                        key, { key = it }, label = { Text("API key") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Text(
                        t("ข้ามได้ แล้วค่อยตั้งค่าทีหลัง ข้อความของคุณจะถูกส่งไปประมวลผลที่ผู้ให้บริการคลาวด์ที่คุณเลือก คีย์เก็บแบบเข้ารหัสในเครื่อง",
                            "You can skip and set this up later. Your messages are processed by the cloud provider you choose. The key is stored encrypted on this device."),
                        color = NovaColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)
                    )
                }
                4 -> {
                    Text(t("สิทธิ์ที่เป็นทางเลือก", "Optional permissions"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        t("• ไมโครโฟน: ขอตอนคุณกดปุ่มไมค์ครั้งแรก ไม่แอบฟัง\n• แสดงทับแอปอื่น: ขอเมื่อคุณเปิดโหมดลอยเท่านั้น\n• การแจ้งเตือน: ขอเมื่อใช้เตือนความจำหรือโหมดลอย\n\nตอนนี้ยังไม่ขอสิทธิ์ใด ๆ",
                            "• Microphone: asked the first time you tap the mic. No background listening.\n• Display over other apps: asked only when you enable floating mode.\n• Notifications: asked when you use reminders or floating mode.\n\nNothing is requested right now."),
                        color = NovaColors.Text
                    )
                }
                else -> {
                    Text(t("ลองแตะ NOVA ดูสิ!", "Try tapping NOVA!"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (poked) t("เยี่ยม! เจอกันในหน้าหลักนะ", "Nice! See you on the main screen.")
                        else t("แตะที่ตัวละครด้านบนเพื่อทักทาย", "Tap the character above to say hi."),
                        color = NovaColors.Muted
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text(t("ย้อนกลับ", "Back")) } else Spacer(Modifier.width(1.dp))
            Button(enabled = step != last || poked, onClick = {
                if (step == 3 && key.isNotBlank()) scope.launch { withContext(Dispatchers.IO) { store.setApiKey(key) } }
                if (step < last) step++ else store.save { it[Keys.onboarded] = true }
            }) { Text(if (step == last) t("เริ่มเลย", "Start") else if (step == 3 && key.isBlank()) t("ข้าม", "Skip") else t("ถัดไป", "Next")) }
        }
    }
}
