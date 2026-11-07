package app.nova.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private val Context.ds by preferencesDataStore("nova_settings")

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
    val remindNotif: Boolean = true
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
}

private fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        loaded = true,
        onboarded = this[Keys.onboarded] ?: d.onboarded,
        lang = this[Keys.lang] ?: d.lang,
        provider = this[Keys.provider] ?: d.provider,
        model = (this[Keys.model] ?: d.model).let { if (it.trim() == "gemini-2.5-flash") "gemini-3.8-flash" else it }, // migrate retired model
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
        remindNotif = this[Keys.remindNotif] ?: d.remindNotif
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
        setApiKey("")
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

    val keyPresent = MutableStateFlow(false)

    init {
        scope.launch { keyPresent.value = apiKey().isNotBlank() }
    }

    fun apiKey(): String = secure?.getString("api_key", "").orEmpty()

    /** Returns false if secure storage is unavailable (the key is never stored unencrypted). */
    fun setApiKey(v: String): Boolean {
        val p = secure ?: return false
        p.edit().putString("api_key", v.trim()).apply()
        keyPresent.value = v.isNotBlank()
        return true
    }
}
