# NOVA (beta 0.1.0) — Android AI companion

Native Android app (Kotlin, Jetpack Compose, Material 3, Room, DataStore, OkHttp).
Original cat-eared vector character, Undertale-style blip voice, memory you control, floating overlay companion.

> **Status: beta, written without access to an Android SDK, so it has NOT been compiled or run yet.**
> The first CI build may show a few compile errors. Send the log and they get fixed.

## Build the APK (GitHub Actions — no Android Studio needed)
1. Create a GitHub repo and push this folder (the `.github/workflows/build-apk.yml` file must be included).
2. Open the **Actions** tab → *Build NOVA debug APK* → wait for the green check.
3. Download the `nova-debug-apk` artifact, unzip, install `app-debug.apk` (allow "install unknown apps").

Or in Android Studio: *File > Open* this folder, let Gradle sync, press Run.

## Configure an AI provider
Settings > AI.
- **Gemini**: get a key at https://aistudio.google.com/apikey, choose *Gemini*, model e.g. `gemini-2.5-flash`, paste key, *Save key*.
- **OpenAI-compatible**: choose it, set *Base URL* (default `https://api.openai.com/v1`), model name, key.
  Local servers work too (e.g. `http://127.0.0.1:8080/v1`; plain http is allowed only for localhost/127.0.0.1/10.0.2.2).
The key is stored with EncryptedSharedPreferences. Messages are sent to the provider you choose.

## Test the floating companion
1. Settings > Floating companion > turn on *Enable floating mode* (or Companion screen > "Float" button).
2. Android opens "Display over other apps": allow NOVA, return to the app.
3. NOVA appears over other apps. Drag = move, tap = react, double-tap = quick chat panel.
4. Notification actions: Hide / Show / Stop. Turn off any time in Settings.

## Voice
- NOVA speaks with synthesized "blip" sounds as text appears (no copyrighted audio). Tone/volume/speed in Settings > Voice.
- Voice input uses Android SpeechRecognizer (Thai/English), only after you tap the mic.
- Android TextToSpeech is intentionally not included in this beta.

## Known limitations (beta)
- No on-device AI model. (Point the OpenAI-compatible URL at a local server if you run one.)
- Accessibility / screen reading is not implemented.
- Character art is a first-pass vector prototype of the supplied design sheet, not final art. Skins are palettes in `companion/Mood.kt`.
- Floating quick-chat panel (keyboard inside an overlay) needs real-device testing; some OEMs behave differently.
- Reminders use inexact alarms and may fire a little late. Timers open the system Clock app.
- Overlay does not restart after reboot; open the app once.
- Single `ChatEngine` acts as the shared view-model instead of per-screen ViewModels.
