package app.nova.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nova.chat.ChatEngine
import app.nova.data.MemoryItem
import app.nova.settings.Settings
import app.nova.ui.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(engine: ChatEngine, s: Settings) {
    val dao = engine.memoryDao
    val scope = rememberCoroutineScope()
    val all by dao.all().collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var editing by remember { mutableStateOf<MemoryItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MemoryItem?>(null) }

    val items = all.filter {
        (filter == "all" || it.kind == filter) && (query.isBlank() || it.text.contains(query, ignoreCase = true))
    }
    val fmt = remember(s.lang) { SimpleDateFormat("d MMM yyyy HH:mm", if (s.lang == "th") Locale("th", "TH") else Locale.ENGLISH) }

    Scaffold(
        containerColor = NovaColors.Bg,
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, containerColor = NovaColors.Purple, contentColor = androidx.compose.ui.graphics.Color(0xFF1A1030)) {
                Icon(Icons.Rounded.Add, t("เพิ่ม", "Add"))
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Text(t("ความจำ", "Memory"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (all.isNotEmpty()) IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Rounded.DeleteSweep, t("ล้างทั้งหมด", "Clear all"), tint = NovaColors.Danger)
                }
            }
            Text(
                t("NOVA จะจำเฉพาะสิ่งที่คุณอนุญาตเท่านั้น คุณแก้ไขหรือลบได้ทุกเมื่อ", "NOVA only remembers what you approve. You can edit or delete anything."),
                color = NovaColors.Muted, style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(t("ค้นหาความจำ", "Search memories")) },
                shape = RoundedCornerShape(16.dp)
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NovaChip(filter == "all", t("ทั้งหมด", "All")) { filter = "all" }
                NovaChip(filter == "fact", t("ข้อเท็จจริง", "Facts")) { filter = "fact" }
                NovaChip(filter == "summary", t("สรุป", "Summaries")) { filter = "summary" }
                NovaChip(filter == "note", t("โน้ต", "Notes")) { filter = "note" }
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Rounded.Memory, null, tint = NovaColors.Purple, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (all.isEmpty()) t("ยังไม่มีความจำ พิมพ์ \"จำไว้ว่า…\" ในแชท หรือกด + เพื่อเพิ่มเอง", "No memories yet. Type \"remember that…\" in chat, or tap + to add one.")
                            else t("ไม่พบรายการที่ตรงกัน", "Nothing matches your search"),
                            color = NovaColors.Muted
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                    items(items, key = { it.id }) { m ->
                        NovaCard(Modifier.fillMaxWidth()) {
                            Text(m.text)
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(kindLabel(m.kind, s.lang), color = NovaColors.Cyan, style = MaterialTheme.typography.labelSmall)
                                    Text(fmt.format(Date(m.createdAt)), color = NovaColors.Muted, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { editing = m }) { Icon(Icons.Rounded.Edit, t("แก้ไข", "Edit"), tint = NovaColors.Muted) }
                                IconButton(onClick = { deleting = m }) { Icon(Icons.Rounded.Delete, t("ลบ", "Delete"), tint = NovaColors.Danger) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) MemoryEditDialog(t("เพิ่มความจำ", "Add memory"), "", s.lang, { adding = false }) { text ->
        scope.launch { dao.insert(MemoryItem(text = text, kind = "fact")) }; adding = false
    }
    editing?.let { m ->
        MemoryEditDialog(t("แก้ไขความจำ", "Edit memory"), m.text, s.lang, { editing = null }) { text ->
            scope.launch { dao.update(m.copy(text = text)) }; editing = null
        }
    }
    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(t("ลบความจำนี้?", "Delete this memory?")) },
            text = { Text(m.text, maxLines = 4) },
            confirmButton = { TextButton(onClick = { scope.launch { dao.delete(m) }; deleting = null }) { Text(t("ลบ", "Delete"), color = NovaColors.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(t("ยกเลิก", "Cancel")) } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(t("ล้างความจำทั้งหมด?", "Clear all memories?")) },
            text = { Text(t("ลบแล้วกู้คืนไม่ได้", "This can't be undone.")) },
            confirmButton = { TextButton(onClick = { scope.launch { dao.clear() }; confirmClear = false }) { Text(t("ล้างทั้งหมด", "Clear all"), color = NovaColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(t("ยกเลิก", "Cancel")) } }
        )
    }
}

private fun kindLabel(kind: String, lang: String) = when (kind) {
    "summary" -> if (lang == "th") "สรุปบทสนทนา" else "Conversation summary"
    "note" -> if (lang == "th") "โน้ต" else "Note"
    else -> if (lang == "th") "ข้อเท็จจริง" else "Fact"
}

@Composable
private fun MemoryEditDialog(title: String, initial: String, lang: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Text(
                    if (lang == "th") "อย่าบันทึกรหัสผ่านหรือข้อมูลการเงิน" else "Don't store passwords or financial details.",
                    color = NovaColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }, enabled = text.isNotBlank()) { Text(if (lang == "th") "บันทึก" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (lang == "th") "ยกเลิก" else "Cancel") } }
    )
}
