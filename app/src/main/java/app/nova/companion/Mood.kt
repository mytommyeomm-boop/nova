package app.nova.companion

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
