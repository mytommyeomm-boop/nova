package app.nova.companion

import androidx.compose.ui.graphics.Color

enum class Mood(val th: String, val en: String) {
    IDLE("ปกติ", "Calm"),
    HAPPY("ดีใจ", "Happy"),
    SAD("งอน", "Sulky"),
    SLEEPY("ง่วง", "Sleepy"),
    SURPRISED("ตกใจ", "Surprised"),
    CONFUSED("สงสัย", "Confused"),
    ANGRY("โกรธ", "Angry"),
    SHY("เขิน", "Shy");

    fun label(lang: String) = if (lang == "th") th else en

    companion object {
        fun fromTag(tag: String): Mood? {
            val t = tag.trim().lowercase()
            return when (t) {
                "calm", "neutral" -> IDLE
                "sulky" -> SAD
                "embarrassed" -> SHY
                else -> entries.firstOrNull { it.name.lowercase() == t }
            }
        }
    }
}

/** What NOVA is doing right now (separate from the facial expression). */
enum class Phase { IDLE, LISTENING, THINKING, TALKING }

/** Skins are plain palettes, so adding a new one is a one-line change. */
data class Skin(val th: String, val en: String, val main: Color, val dark: Color, val light: Color) {
    fun label(lang: String) = if (lang == "th") th else en
}

val Skins = listOf(
    Skin("ฮู้ดม่วง", "Violet hoodie", Color(0xFF8E78C8), Color(0xFF6E5AA8), Color(0xFFCFC3EC)),
    Skin("ฮู้ดฟ้านีออน", "Neon cyan hoodie", Color(0xFF3FA7C4), Color(0xFF2A7F99), Color(0xFFB5E6F2)),
    Skin("ฮู้ดชมพู", "Pink hoodie", Color(0xFFD87FB3), Color(0xFFA95C8A), Color(0xFFF3C6DD))
)
