package app.nova.chat

import android.app.Application
import app.nova.ai.*
import app.nova.companion.Mood
import app.nova.companion.Phase
import app.nova.data.MemoryItem
import app.nova.data.MessageItem
import app.nova.data.NovaDb
import app.nova.settings.SettingsStore
import app.nova.tr
import app.nova.voice.BlipVoice
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class Bubble(val text: String, val ts: Long)

/** Real measurements for the last request. ttftMs/totalMs are -1 until known. Tokens are estimated (chars/4) unless the provider reports them. */
data class PerfStats(
    val startMs: Long = 0,
    val ttftMs: Long = -1,
    val totalMs: Long = -1,
    val chars: Int = 0,
    val status: String = "idle", // idle | connecting | streaming | ok | error | cancelled
    val provider: String = "",
    val model: String = ""
) {
    val approxTokens: Int get() = (chars + 3) / 4
}

/**
 * Shared "view-model" for the whole app. Both the Compose screens and the floating overlay observe it,
 * so NOVA's mood, speech bubble and conversation stay in sync everywhere.
 *
 * Replies are revealed character by character with a blip sound (Undertale-style voice).
 */
class ChatEngine(
    private val app: Application,
    private val db: NovaDb,
    private val settings: SettingsStore,
    private val blip: BlipVoice
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val messages: StateFlow<List<MessageItem>> =
        db.messages().all().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _mood = MutableStateFlow(Mood.IDLE)
    val mood = _mood.asStateFlow()
    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase = _phase.asStateFlow()
    private val _shown = MutableStateFlow("")
    val shown = _shown.asStateFlow()
    private val _generating = MutableStateFlow(false)
    val generating = _generating.asStateFlow()
    private val _bubble = MutableStateFlow<Bubble?>(null)
    val bubble = _bubble.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _proposal = MutableStateFlow<String?>(null)
    val proposal = _proposal.asStateFlow()
    private val _perf = MutableStateFlow(PerfStats())
    val perf = _perf.asStateFlow()
    private val _partial = MutableStateFlow("")
    val partial = _partial.asStateFlow()

    private var job: Job? = null
    private var replayJob: Job? = null
    private var lastInteraction = System.currentTimeMillis()
    private var moodToken = 0

    private fun lang() = settings.state.value.lang

    init {
        // Falls asleep after a while without interaction.
        scope.launch {
            while (true) {
                delay(10_000)
                if (_phase.value == Phase.IDLE && _mood.value == Mood.IDLE &&
                    System.currentTimeMillis() - lastInteraction > 90_000
                ) _mood.value = Mood.SLEEPY
            }
        }
    }

    fun touch() {
        lastInteraction = System.currentTimeMillis()
        if (_mood.value == Mood.SLEEPY && _phase.value == Phase.IDLE) _mood.value = Mood.IDLE
    }

    private fun holdMood(m: Mood, ms: Long = 7000) {
        moodToken++
        val tk = moodToken
        _mood.value = m
        scope.launch {
            delay(ms)
            if (tk == moodToken && _phase.value == Phase.IDLE) _mood.value = Mood.IDLE
        }
    }

    fun setListening(on: Boolean) {
        if (on) { touch(); _phase.value = Phase.LISTENING }
        else if (_phase.value == Phase.LISTENING) { _phase.value = Phase.IDLE; _partial.value = "" }
    }

    fun setPartial(t: String) { _partial.value = t }
    fun reportError(msg: String) { _error.value = msg }
    fun clearError() { _error.value = null }

    /** Tap interaction on the character. */
    fun poke() {
        touch()
        if (_generating.value) return
        val lang = lang()
        val options = listOf(
            Mood.HAPPY to ("เอ๊ะ! จั๊กจี้นะ~" to "Hehe, that tickles~"),
            Mood.SURPRISED to ("ว้าย! ตกใจหมด!" to "Eep! You startled me!"),
            Mood.SHY to ("อะ..อย่าจิ้มสิ…" to "D-don't poke me…"),
            Mood.CONFUSED to ("หืม? มีอะไรเหรอ?" to "Hm? Need something?")
        )
        val (m, line) = options.random()
        holdMood(m)
        val text = tr(lang, line.first, line.second)
        _bubble.value = Bubble(text, System.currentTimeMillis())
        replay(text, m)
    }

    fun stop() {
        job?.cancel()
        replayJob?.cancel()
        if (_phase.value == Phase.TALKING) _phase.value = Phase.IDLE
    }

    /** Re-plays a message using the blip voice (no cloud, no TTS engine required). */
    fun replay(text: String, mood: Mood = _mood.value) {
        if (_generating.value) return
        replayJob?.cancel()
        replayJob = scope.launch {
            _phase.value = Phase.TALKING
            try {
                val s = settings.state.value
                for (c in text.take(240)) {
                    if (isBlipChar(c)) blip.blip(c, mood)
                    delay(charDelay(s.textSpeed, c, mood))
                }
            } finally {
                _phase.value = Phase.IDLE
            }
        }
    }

    // ---------- sending ----------

    private val rememberRe = Regex("""(?:จำไว้ว่า|จำไว้นะว่า|remember that|please remember)\s*(.+)""", RegexOption.IGNORE_CASE)
    private val sensitiveRe = Regex("""(password|passcode|\bpin\b|cvv|otp|รหัส|บัตรเครดิต|เลขบัตร|\b\d{12,19}\b)""", RegexOption.IGNORE_CASE)

    private fun detectMemoryProposal(text: String) {
        val m = rememberRe.find(text) ?: return
        val fact = m.groupValues[1].trim()
        if (fact.isEmpty()) return
        if (sensitiveRe.containsMatchIn(fact)) {
            _error.value = tr(lang(), "NOVA จะไม่จำข้อมูลที่ดูเป็นความลับ เช่น รหัสผ่านหรือเลขบัตร", "NOVA won't store anything that looks sensitive, like passwords or card numbers.")
        } else {
            _proposal.value = fact
        }
    }

    fun confirmProposal() {
        val t = _proposal.value ?: return
        _proposal.value = null
        scope.launch { db.memories().insert(MemoryItem(text = t, kind = "fact")) }
    }

    fun dismissProposal() { _proposal.value = null }

    /** Claims the single generation slot synchronously, so two quick taps can never start two requests. */
    private fun claim(): Boolean = _generating.compareAndSet(false, true)

    private fun launchGeneration(pre: suspend () -> Unit = {}) {
        job = scope.launch {
            try {
                pre()
                generate()
            } finally {
                // generate() resets this itself; this covers cancellation/failure before it starts.
                _generating.value = false
            }
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || !claim()) return
        touch()
        _error.value = null
        replayJob?.cancel()
        detectMemoryProposal(t)
        launchGeneration { db.messages().insert(MessageItem(role = "user", text = t)) }
    }

    fun regenerate() {
        if (!claim()) return
        _error.value = null
        launchGeneration {
            val last = db.messages().last(1).firstOrNull()
            if (last != null && last.role == "assistant") db.messages().delete(last.id)
        }
    }

    fun retryLast() {
        if (!claim()) return
        _error.value = null
        launchGeneration()
    }

    fun deleteMessage(id: Long) { scope.launch { db.messages().delete(id) } }
    fun clearChat() { stop(); scope.launch { db.messages().clear() } }

    private fun isBlipChar(c: Char) = !(c.isWhitespace() || c in ".,!?;:…-()\"'[]")

    private fun charDelay(speed: Float, c: Char, mood: Mood): Long {
        var base = 45f / speed.coerceIn(0.4f, 3f)
        if (mood == Mood.SLEEPY) base *= 1.6f
        if (mood == Mood.SURPRISED) base *= 0.8f
        val extra = when (c) { ',', ';' -> 110f; '.', '!', '?', '\n' -> 220f; else -> 0f }
        return (base + extra).toLong()
    }

    private suspend fun config(): AiConfig {
        val s = settings.state.value
        val key = withContext(Dispatchers.IO) { settings.apiKey() }
        if (s.provider == "gemini" && key.isBlank())
            throw AiException(tr(s.lang, "ยังไม่ได้ใส่ API key — ไปที่ ตั้งค่า > AI", "No API key yet — go to Settings > AI"))
        return AiConfig(s.provider, s.model, s.baseUrl, key)
    }

    /** Keeps the newest messages that fit a character budget, so a few long messages cannot bloat every request. */
    private fun trimHistory(h: List<ChatMsg>, budget: Int = 6000): List<ChatMsg> {
        var used = 0
        val out = ArrayDeque<ChatMsg>()
        for (m in h.asReversed()) {
            if (out.isNotEmpty() && used + m.text.length > budget) break
            out.addFirst(m); used += m.text.length
        }
        // providers require the conversation to start with a user turn
        while (out.isNotEmpty() && out.first().role != "user") out.removeFirst()
        return out.toList()
    }

    private suspend fun buildSystem(): String {
        val s = settings.state.value
        val langName = if (s.lang == "th") "Thai" else "English"
        val persona = s.persona.ifBlank {
            "You are NOVA, a small cat-eared AI companion who lives on the user's phone. " +
                "You are warm, playful and a little shy. Keep replies short (1-3 sentences) unless the user asks for detail. " +
                "Never claim abilities you do not have: you cannot control other apps, read the screen or act outside the app's built-in skills."
        }
        val sb = StringBuilder(persona)
        sb.append("\nReply in $langName unless the user writes in another language.")
        sb.append("\nResponse length: ").append(when (s.maxTokens) {
            in 0..250 -> "very short, 1-2 sentences."
            in 251..700 -> "short, usually 1-3 sentences."
            else -> "as long as the question needs, but stay focused."
        })
        sb.append("\nBegin EVERY reply with exactly one emotion tag in square brackets, chosen from: ")
        sb.append("[idle] [happy] [sad] [sleepy] [surprised] [confused] [angry] [shy]. Then write the reply.")
        if (s.memoryEnabled) {
            val mem = db.memories().allNow().filter { it.kind != "note" }.take(20)
            if (mem.isNotEmpty()) {
                sb.append("\nThings the user asked you to remember:\n")
                mem.forEach { sb.append("- ").append(it.text).append('\n') }
            }
        }
        return sb.toString()
    }

    fun friendly(e: Throwable): String {
        val l = lang()
        return when (e) {
            is UnknownHostException -> tr(l, "ไม่มีอินเทอร์เน็ต ลองใหม่เมื่อออนไลน์", "No internet connection. Try again when you're online.")
            is SocketTimeoutException -> tr(l, "การเชื่อมต่อหมดเวลา", "The request timed out.")
            is AiException -> when (e.code) {
                503, 500, 502, 504 -> tr(l, "เซิร์ฟเวอร์ AI กำลังมีคนใช้เยอะ ลองแล้วหลายครั้งแต่ยังไม่ได้ รอสักครู่แล้วกด \"ลองใหม่\" นะ", "The AI server is busy right now. I retried a few times; wait a moment and tap Retry.")
                429 -> tr(l, "ใช้งานเกินโควตาชั่วคราว รอสักครู่แล้วลองใหม่", "Rate limit reached. Wait a bit and retry.")
                404 -> tr(l, "ไม่พบโมเดลนี้ ไปที่ ตั้งค่า > โมเดล แล้วเปลี่ยนชื่อโมเดล", "Model not found. Change it in Settings > Model.")
                400, 401, 403 -> tr(l, "API key หรือโมเดลไม่ถูกต้อง ตรวจสอบที่ ตั้งค่า > AI", "Invalid API key or model. Check Settings > AI.")
                else -> e.message ?: "AI error"
            }
            is IOException -> tr(l, "เครือข่ายมีปัญหา: ", "Network problem: ") + (e.message ?: "")
            else -> e.message ?: "Error"
        }
    }

    private suspend fun generate() = coroutineScope {
        _generating.value = true
        _phase.value = Phase.THINKING
        _shown.value = ""
        val s = settings.state.value

        val t0 = System.currentTimeMillis()
        var firstTokenAt = -1L
        val backlog = java.util.concurrent.atomic.AtomicInteger(0)
        _perf.value = PerfStats(startMs = t0, status = "connecting", provider = s.provider, model = s.model)

        val pending = Channel<Char>(Channel.UNLIMITED)
        val full = StringBuilder()
        var err: Throwable? = null
        var completed = false

        val revealer = launch {
            for (c in pending) {
                _phase.value = Phase.TALKING
                _shown.value = _shown.value + c
                val behind = backlog.decrementAndGet()
                // The typewriter effect must never make the user wait longer than the network did:
                // the further behind it is, the faster it catches up (and it skips blips when far behind).
                if (s.instantText || behind > 200) continue
                if (isBlipChar(c) && behind < 60) blip.blip(c, _mood.value)
                val d = charDelay(s.textSpeed, c, _mood.value)
                delay(if (behind > 80) d / 6 else if (behind > 30) d / 3 else d)
            }
        }

        try {
            val cfg = try { config() } catch (e: AiException) { err = e; null }
            if (cfg != null) {
                val history = trimHistory(db.messages().last(s.historyLimit.coerceIn(2, 40)).reversed().map { ChatMsg(it.role, it.text) })
                val system = buildSystem()
                val provider = AiFactory.create(cfg)

                val head = StringBuilder()
                var tagDone = false
                fun emit(text: String) {
                    full.append(text)
                    backlog.addAndGet(text.length)
                    for (c in text) pending.trySend(c)
                }
                fun feed(d: String) {
                    if (tagDone) { emit(d); return }
                    head.append(d)
                    val h = head.toString().trimStart()
                    if (h.isEmpty()) return
                    if (h[0] != '[') { tagDone = true; emit(head.toString()); return }
                    val end = h.indexOf(']')
                    if (end >= 0) {
                        tagDone = true
                        Mood.fromTag(h.substring(1, end))?.let { _mood.value = it }
                        emit(h.substring(end + 1).trimStart())
                    } else if (h.length > 14) {
                        tagDone = true
                        emit(head.toString())
                    }
                }

                var gotAny = false
                var attempt = 0
                while (true) {
                    try {
                        provider.stream(system, history, s.maxTokens).collect {
                            if (firstTokenAt < 0) {
                                firstTokenAt = System.currentTimeMillis()
                                _perf.value = _perf.value.copy(ttftMs = firstTokenAt - t0, status = "streaming")
                            }
                            gotAny = true; feed(it)
                        }
                        if (!tagDone && head.isNotEmpty()) { tagDone = true; emit(head.toString()) }
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val retryable = e is IOException || (e is AiException && e.retryable)
                        if (!gotAny && attempt < 2 && retryable) { attempt++; delay(800L * attempt); continue } // short backoff: 0.8s, 1.6s
                        err = e
                        break
                    }
                }
            }
            pending.close()
            revealer.join()
            completed = true
        } finally {
            withContext(NonCancellable) {
                pending.close()
                revealer.cancel()
                val finalText = (if (completed) full.toString() else _shown.value).trim()
                if (finalText.isNotEmpty()) db.messages().insert(MessageItem(role = "assistant", text = finalText))
                _shown.value = ""
                _generating.value = false
                _phase.value = Phase.IDLE
                if (finalText.isNotEmpty()) _bubble.value = Bubble(finalText.take(90), System.currentTimeMillis())
                holdMood(_mood.value)
                err?.let { _error.value = friendly(it) }
                _perf.value = _perf.value.copy(
                    totalMs = System.currentTimeMillis() - t0,
                    chars = full.length,
                    status = if (err != null) "error" else if (!completed) "cancelled" else "ok"
                )
            }
        }
        Unit
    }

    // ---------- helpers used by Skills / Memory ----------

    suspend fun oneShot(system: String, user: String): String {
        val cfg = config()
        val sb = StringBuilder()
        AiFactory.create(cfg).stream(system, listOf(ChatMsg("user", user)), 600).collect { sb.append(it) }
        return sb.toString().trim()
    }

    suspend fun summarizeToMemory(): Boolean {
        val msgs = db.messages().last(30).reversed()
        if (msgs.size < 2) return false
        val convo = msgs.joinToString("\n") { (if (it.role == "user") "User: " else "NOVA: ") + it.text }
        val lang = if (lang() == "th") "Thai" else "English"
        val summary = oneShot(
            "Summarize the conversation in 2-3 short sentences in $lang. Keep only useful, non-sensitive facts and preferences. " +
                "Never include passwords, financial or other sensitive personal data. Output only the summary.",
            convo
        )
        if (summary.isBlank()) return false
        db.memories().insert(MemoryItem(text = summary, kind = "summary"))
        return true
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "NOVA")
        root.put("exportedAt", System.currentTimeMillis())
        val mem = JSONArray()
        db.memories().allNow().forEach {
            mem.put(JSONObject().put("kind", it.kind).put("text", it.text).put("createdAt", it.createdAt))
        }
        val msg = JSONArray()
        db.messages().allNow().forEach {
            msg.put(JSONObject().put("role", it.role).put("text", it.text).put("createdAt", it.createdAt))
        }
        root.put("memories", mem).put("messages", msg)
        root.toString(2)
    }

    suspend fun wipe() = withContext(Dispatchers.IO) {
        db.clearAllTables()
    }

    val memoryDao get() = db.memories()
}
