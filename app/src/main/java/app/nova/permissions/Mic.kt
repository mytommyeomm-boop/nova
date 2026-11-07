package app.nova.permissions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.nova.chat.ChatEngine
import app.nova.tr
import app.nova.voice.SpeechInput

class MicHandle(val listening: Boolean, val toggle: () -> Unit)

/** Microphone is only used after the user taps the mic button, and permission is requested at that moment. */
@Composable
fun rememberMic(engine: ChatEngine, lang: String, onFinal: (String) -> Unit): MicHandle {
    val ctx = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(onFinal)
    val speech = remember(lang) {
        SpeechInput(
            ctx, lang,
            onState = { listening = it; engine.setListening(it) },
            onPartial = { engine.setPartial(it) },
            onFinal = { latest(it) },
            onError = { engine.reportError(tr(lang, "ฟังเสียงไม่สำเร็จ ($it)", "Couldn't recognize speech ($it)")) }
        )
    }
    DisposableEffect(speech) { onDispose { speech.destroy(); engine.setListening(false) } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) speech.start() else engine.reportError(tr(lang, "ไม่ได้รับสิทธิ์ไมโครโฟน", "Microphone permission denied"))
    }
    return MicHandle(listening) {
        if (listening) speech.cancel()
        else if (!speech.available()) engine.reportError(
            tr(lang, "เครื่องนี้ไม่มีบริการรู้จำเสียงพูด", "Speech recognition isn't available on this device")
        )
        else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.start()
        else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
