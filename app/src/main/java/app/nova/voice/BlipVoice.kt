package app.nova.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import app.nova.companion.Mood
import app.nova.settings.SettingsStore
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Undertale-style "blip" voice: a short synthesized square-wave beep played once per revealed character.
 * The sound is generated in code (no copyrighted audio). Pitch varies per character and per mood.
 */
class BlipVoice(ctx: Context, private val settings: SettingsStore) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private var soundId = 0
    private var loaded = false

    init {
        pool.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
        val f = File(ctx.cacheDir, "blip_v1.wav")
        if (!f.exists()) f.writeBytes(makeWav())
        soundId = pool.load(f.absolutePath, 1)
    }

    fun blip(c: Char, mood: Mood) {
        val s = settings.state.value
        if (!s.blipOn || !loaded) return
        val base = when (s.blipVoice) { 0 -> 0.75f; 2 -> 1.4f; else -> 1.0f }
        val moodMul = when (mood) {
            Mood.HAPPY -> 1.15f; Mood.SAD -> 0.85f; Mood.SLEEPY -> 0.75f
            Mood.SURPRISED -> 1.25f; Mood.ANGRY -> 0.9f; Mood.SHY -> 1.1f; else -> 1.0f
        }
        val jitter = 0.9f + (c.code % 17) / 100f * 1.2f
        val rate = (base * moodMul * jitter).coerceIn(0.5f, 2.0f)
        val v = s.blipVolume.coerceIn(0f, 1f)
        pool.play(soundId, v, v, 1, 0, rate)
    }

    fun release() = pool.release()

    private fun makeWav(): ByteArray {
        val sr = 22050
        val n = (sr * 0.055).toInt()
        val pcm = ShortArray(n)
        val period = sr / 420.0
        for (i in 0 until n) {
            val square = if ((i % period) < period / 2) 1.0 else -1.0
            val env = if (i < n * 0.65) 1.0 else (n - i) / (n * 0.35)
            pcm[i] = (square * env * 0.35 * Short.MAX_VALUE).toInt().toShort()
        }
        val data = n * 2
        val bb = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + data); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1)
        bb.putInt(sr); bb.putInt(sr * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(data)
        pcm.forEach { bb.putShort(it) }
        return bb.array()
    }
}
