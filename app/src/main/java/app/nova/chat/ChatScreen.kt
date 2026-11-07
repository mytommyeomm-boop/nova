package app.nova.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nova.companion.Phase
import app.nova.container
import app.nova.data.MessageItem
import app.nova.permissions.rememberMic
import app.nova.settings.Keys
import app.nova.settings.Settings
import app.nova.ui.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(engine: ChatEngine, s: Settings) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val msgs by engine.messages.collectAsStateWithLifecycle()
    val shown by engine.shown.collectAsStateWithLifecycle()
    val generating by engine.generating.collectAsStateWithLifecycle()
    val phase by engine.phase.collectAsStateWithLifecycle()
    val error by engine.error.collectAsStateWithLifecycle()
    val proposal by engine.proposal.collectAsStateWithLifecycle()
    val partial by engine.partial.collectAsStateWithLifecycle()
    val clip = LocalClipboardManager.current

    var input by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var summarizing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val mic = rememberMic(engine, s.lang) { engine.send(it) }
    val list = rememberLazyListState()
    val busy = generating || phase == Phase.TALKING

    LaunchedEffect(msgs.size, shown.length, generating) {
        val n = msgs.size + if (generating) 1 else 0
        if (n > 0) list.scrollToItem(n - 1)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        // top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("แชท", "Chat"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { ctx.container.settings.save { it[Keys.blipOn] = !s.blipOn } }) {
                Icon(if (s.blipOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, t("เปิด/ปิดเสียง", "Mute"), tint = NovaColors.Cyan)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(t("สรุปแชทลงความจำ", "Summarize chat to memory")) },
                        leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                        onClick = {
                            menu = false
                            summarizing = true
                            scope.launch {
                                toast = try {
                                    if (engine.summarizeToMemory()) t2(s, "บันทึกสรุปแล้ว", "Summary saved") else t2(s, "ยังคุยไม่พอให้สรุป", "Not enough conversation yet")
                                } catch (e: Exception) { engine.friendly(e) }
                                summarizing = false
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(t("ล้างการสนทนา", "Clear conversation")) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) },
                        onClick = { menu = false; confirmClear = true }
                    )
                }
            }
        }

        // messages
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (msgs.isEmpty() && !generating) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Chat, null, tint = NovaColors.Purple, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(t("ยังไม่มีบทสนทนา ลองพิมพ์ทักทาย NOVA ดูสิ", "No messages yet. Say hi to NOVA!"), color = NovaColors.Muted)
                }
            }
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(msgs, key = { it.id }) { m ->
                    MessageRow(
                        m, isLast = m == msgs.lastOrNull(), busy = busy,
                        onCopy = { clip.setText(AnnotatedString(m.text)); toast = t2(s, "คัดลอกแล้ว", "Copied") },
                        onRegenerate = { engine.regenerate() },
                        onReplay = { engine.replay(m.text) },
                        onDelete = { engine.deleteMessage(m.id) }
                    )
                }
                if (generating) item(key = "live") {
                    ChatBubble(mine = false) {
                        if (shown.isEmpty()) Text(t("กำลังคิด…", "Thinking…"), color = NovaColors.Cyan)
                        else Text(shown, color = NovaColors.Text)
                    }
                }
            }
        }

        proposal?.let { p ->
            NovaCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), accent = NovaColors.Cyan) {
                Text(t("ให้ NOVA จำสิ่งนี้ไหม?", "Let NOVA remember this?"), style = MaterialTheme.typography.labelLarge, color = NovaColors.Cyan)
                Text(p, Modifier.padding(vertical = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { engine.confirmProposal() }) { Text(t("บันทึก", "Save")) }
                    TextButton(onClick = { engine.dismissProposal() }) { Text(t("ไม่ต้อง", "No thanks")) }
                }
            }
        }

        error?.let { e ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(NovaColors.Danger.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(e, Modifier.weight(1f), color = NovaColors.Danger, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { engine.retryLast() }) { Text(t("ลองใหม่", "Retry")) }
                IconButton(onClick = { engine.clearError() }) { Icon(Icons.Rounded.Close, null, tint = NovaColors.Muted) }
            }
        }
        if (phase == Phase.LISTENING) {
            Text(
                "🎤 " + partial.ifBlank { t("กำลังฟัง…", "Listening…") },
                Modifier.padding(horizontal = 16.dp, vertical = 2.dp), color = NovaColors.Cyan, style = MaterialTheme.typography.bodySmall
            )
        }
        toast?.let {
            LaunchedEffect(it) { kotlinx.coroutines.delay(1800); toast = null }
            Text(it, Modifier.padding(horizontal = 16.dp), color = NovaColors.Muted, style = MaterialTheme.typography.labelSmall)
        }
        if (summarizing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))

        // input
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { mic.toggle() }) {
                Icon(if (mic.listening) Icons.Rounded.Stop else Icons.Rounded.Mic, t("พูด", "Voice"), tint = if (mic.listening) NovaColors.Danger else NovaColors.Cyan)
            }
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), maxLines = 4,
                placeholder = { Text(t("พิมพ์ข้อความ…", "Type a message…")) }, shape = RoundedCornerShape(20.dp)
            )
            if (busy) {
                IconButton(onClick = { engine.stop() }) { Icon(Icons.Rounded.Stop, t("หยุด", "Stop"), tint = NovaColors.Danger) }
            } else {
                IconButton(onClick = { engine.send(input); input = "" }, enabled = input.isNotBlank()) {
                    Icon(Icons.Rounded.Send, t("ส่ง", "Send"), tint = if (input.isNotBlank()) NovaColors.Purple else NovaColors.Muted)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(t("ล้างการสนทนา?", "Clear conversation?")) },
            text = { Text(t("ข้อความทั้งหมดในแชทจะถูกลบ (ความจำไม่ถูกลบ)", "All chat messages will be deleted (memories are kept).")) },
            confirmButton = { TextButton(onClick = { engine.clearChat(); confirmClear = false }) { Text(t("ล้าง", "Clear"), color = NovaColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(t("ยกเลิก", "Cancel")) } }
        )
    }
}

private fun t2(s: Settings, th: String, en: String) = if (s.lang == "th") th else en

@Composable
private fun ChatBubble(mine: Boolean, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        val shape = RoundedCornerShape(18.dp, 18.dp, if (mine) 4.dp else 18.dp, if (mine) 18.dp else 4.dp)
        Box(
            Modifier.widthIn(max = 300.dp).then(
                if (mine) Modifier.background(Brush.linearGradient(listOf(Color(0xFF7C5CE0), Color(0xFF3B82F6))), shape)
                else Modifier.background(NovaColors.Surface2, shape).border(1.dp, NovaColors.Cyan.copy(alpha = 0.25f), shape)
            ).padding(horizontal = 14.dp, vertical = 10.dp)
        ) { content() }
    }
}

@Composable
private fun MessageRow(
    m: MessageItem, isLast: Boolean, busy: Boolean,
    onCopy: () -> Unit, onRegenerate: () -> Unit, onReplay: () -> Unit, onDelete: () -> Unit
) {
    val mine = m.role == "user"
    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        ChatBubble(mine) { Text(m.text, color = if (mine) Color.White else NovaColors.Text) }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            SmallAction(Icons.Rounded.ContentCopy, onCopy)
            if (!mine) SmallAction(Icons.Rounded.VolumeUp, onReplay, enabled = !busy)
            if (!mine && isLast) SmallAction(Icons.Rounded.Refresh, onRegenerate, enabled = !busy)
            SmallAction(Icons.Rounded.Delete, onDelete)
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
        Icon(icon, null, tint = NovaColors.Muted, modifier = Modifier.size(16.dp))
    }
}
