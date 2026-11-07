package app.nova.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.ds by preferencesDataStore("nova_settings")

data class SavedKey(val id: String, val label: String, val hint: String)

data class Settings(
    val loaded: Boolean = false,
    val onboarded: Boolean = false,
    val lang: String = "th",
    val provider: String = "gemini", // gemini | openai
    val model: String = "gemini-3.8-flash",
    val baseUrl: String = "https://api.openai.com/v1",
    val persona: String = "",
    val skin: Int = 0,
    val charScale: Float = 1f,
    val animations: Boolean = true,
    val blipOn: Boolean = true,
    val blipVolume: Float = 0.6f,
    val blipVoice: Int = 1, // 0 deep, 1 mid, 2 bright
    val textSpeed: Float = 1f,
    val memoryEnabled: Boolean = true,
    val overlayEnabled: Boolean = false,
    val overlaySize: Float = 0.6f,
    val overlaySnap: Boolean = true,
    val overlayX: Int = 0,
    val overlayY: Int = 400,
    val remindNotif: Boolean = true,
    val historyLimit: Int = 12,
    val maxTokens: Int = 600,
    val instantText: Boolean = false,
    val showPerf: Boolean = false
)

object Keys {
    val onboarded = booleanPreferencesKey("onboarded")
    val lang = stringPreferencesKey("lang")
    val provider = stringPreferencesKey("provider")
    val model = stringPreferencesKey("model")
    val baseUrl = stringPreferencesKey("base_url")
    val persona = stringPreferencesKey("persona")
    val skin = intPreferencesKey("skin")
    val charScale = floatPreferencesKey("char_scale")
    val animations = booleanPreferencesKey("animations")
    val blipOn = booleanPreferencesKey("blip_on")
    val blipVolume = floatPreferencesKey("blip_volume")
    val blipVoice = intPreferencesKey("blip_voice")
    val textSpeed = floatPreferencesKey("text_speed")
    val memoryEnabled = booleanPreferencesKey("memory_enabled")
    val overlayEnabled = booleanPreferencesKey("overlay_enabled")
    val overlaySize = floatPreferencesKey("overlay_size")
    val overlaySnap = booleanPreferencesKey("overlay_snap")
    val overlayX = intPreferencesKey("overlay_x")
    val overlayY = intPreferencesKey("overlay_y")
    val remindNotif = booleanPreferencesKey("remind_notif")
    val historyLimit = intPreferencesKey("history_limit")
    val maxTokens = intPreferencesKey("max_tokens")
    val instantText = booleanPreferencesKey("instant_text")
    val showPerf = booleanPreferencesKey("show_perf")
}

private fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        loaded = true,
        onboarded = this[Keys.onboarded] ?: d.onboarded,
        lang = this[Keys.lang] ?: d.lang,
        provider = this[Keys.provider] ?: d.provider,
        model = this[Keys.model] ?: d.model,
        baseUrl = this[Keys.baseUrl] ?: d.baseUrl,
        persona = this[Keys.persona] ?: d.persona,
        skin = this[Keys.skin] ?: d.skin,
        charScale = this[Keys.charScale] ?: d.charScale,
        animations = this[Keys.animations] ?: d.animations,
        blipOn = this[Keys.blipOn] ?: d.blipOn,
        blipVolume = this[Keys.blipVolume] ?: d.blipVolume,
        blipVoice = this[Keys.blipVoice] ?: d.blipVoice,
        textSpeed = this[Keys.textSpeed] ?: d.textSpeed,
        memoryEnabled = this[Keys.memoryEnabled] ?: d.memoryEnabled,
        overlayEnabled = this[Keys.overlayEnabled] ?: d.overlayEnabled,
        overlaySize = this[Keys.overlaySize] ?: d.overlaySize,
        overlaySnap = this[Keys.overlaySnap] ?: d.overlaySnap,
        overlayX = this[Keys.overlayX] ?: d.overlayX,
        overlayY = this[Keys.overlayY] ?: d.overlayY,
        remindNotif = this[Keys.remindNotif] ?: d.remindNotif,
        historyLimit = this[Keys.historyLimit] ?: d.historyLimit,
        maxTokens = this[Keys.maxTokens] ?: d.maxTokens,
        instantText = this[Keys.instantText] ?: d.instantText,
        showPerf = this[Keys.showPerf] ?: d.showPerf
    )
}

class SettingsStore(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val state: StateFlow<Settings> = ctx.ds.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    fun save(block: (MutablePreferences) -> Unit) {
        scope.launch { ctx.ds.edit { block(it) } }
    }

    suspend fun resetAll() {
        ctx.ds.edit { it.clear() }
        deleteAllKeys()
    }

    // API key lives only in EncryptedSharedPreferences (Android Keystore backed).
    private val secure: SharedPreferences? by lazy {
        try {
            val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                ctx, "nova_secure", mk,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            null
        }
    }

    // ---------- API key vault ----------
    // Several keys can be saved (e.g. different Google projects) and one is "active". Everything is stored
    // only in EncryptedSharedPreferences; the UI only ever receives a masked hint, never the full key.

    private class Entry(val id: String, val label: String, val key: String)

    val keyPresent = MutableStateFlow(false)
    val keys = MutableStateFlow<List<SavedKey>>(emptyList())
    val activeKeyId = MutableStateFlow("")

    @Synchronized
    private fun loadEntries(): MutableList<Entry> {
        val p = secure ?: return mutableListOf()
        val out = mutableListOf<Entry>()
        try {
            val arr = JSONArray(p.getString("vault", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += Entry(o.getString("id"), o.getString("label"), o.getString("key"))
            }
        } catch (e: Exception) { /* corrupted vault: start empty rather than crash */ }
        // migrate the single key saved by earlier versions
        val legacy = p.getString("api_key", "").orEmpty()
        if (legacy.isNotBlank()) {
            if (out.none { it.key == legacy }) {
                val e = Entry(UUID.randomUUID().toString(), "Key 1", legacy)
                out += e
                if (p.getString("active", "").isNullOrEmpty()) p.edit().putString("active", e.id).apply()
            }
            p.edit().remove("api_key").apply()
            persist(out, p.getString("active", "").orEmpty())
        }
        return out
    }

    @Synchronized
    private fun persist(entries: List<Entry>, active: String) {
        val p = secure ?: return
        val arr = JSONArray()
        entries.forEach { arr.put(JSONObject().put("id", it.id).put("label", it.label).put("key", it.key)) }
        val act = if (entries.any { it.id == active }) active else entries.firstOrNull()?.id.orEmpty()
        p.edit().putString("vault", arr.toString()).putString("active", act).apply()
        keys.value = entries.map { SavedKey(it.id, it.label, "••••" + it.key.takeLast(4)) }
        activeKeyId.value = act
        keyPresent.value = entries.any { it.id == act && it.key.isNotBlank() }
    }

    init {
        scope.launch { val e = loadEntries(); persist(e, secure?.getString("active", "").orEmpty()) }
    }

    /** The currently selected key (empty if none). */
    @Synchronized
    fun apiKey(): String {
        val p = secure ?: return ""
        val act = p.getString("active", "").orEmpty()
        return loadEntries().firstOrNull { it.id == act }?.key.orEmpty()
    }

    /** Saves a key under [label] and makes it the active one. Returns false if secure storage is unavailable. */
    @Synchronized
    fun addKey(label: String, key: String): Boolean {
        if (secure == null) return false
        val k = key.trim()
        if (k.isEmpty()) return false
        val entries = loadEntries()
        val existing = entries.firstOrNull { it.key == k }
        val id = if (existing != null) existing.id else {
            val e = Entry(UUID.randomUUID().toString(), label.trim().ifBlank { "Key ${entries.size + 1}" }, k)
            entries += e
            e.id
        }
        persist(entries, id)
        return true
    }

    @Synchronized
    fun selectKey(id: String) {
        val entries = loadEntries()
        if (entries.any { it.id == id }) persist(entries, id)
    }

    @Synchronized
    fun deleteKey(id: String) {
        val entries = loadEntries()
        val act = secure?.getString("active", "").orEmpty()
        entries.removeAll { it.id == id }
        persist(entries, act)
    }

    @Synchronized
    fun deleteAllKeys() {
        persist(emptyList(), "")
    }

    /** Kept for onboarding: saves (and activates) a key, or removes the active one when [v] is blank. */
    fun setApiKey(v: String): Boolean =
        if (v.isBlank()) { activeKeyId.value.takeIf { it.isNotEmpty() }?.let { deleteKey(it) }; secure != null }
        else addKey("", v)
}
