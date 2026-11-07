package app.nova.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Dest(val route: String, val th: String, val en: String, val icon: ImageVector) {
    COMPANION("companion", "คอมแพนเนียน", "Companion", Icons.Rounded.Face),
    CHAT("chat", "แชท", "Chat", Icons.Rounded.Chat),
    MEMORY("memory", "ความจำ", "Memory", Icons.Rounded.Memory),
    SKILLS("skills", "สกิล", "Skills", Icons.Rounded.Bolt),
    SETTINGS("settings", "ตั้งค่า", "Settings", Icons.Rounded.Settings)
}
