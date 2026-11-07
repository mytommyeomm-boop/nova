package app.nova.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMsg(val role: String, val text: String) // role: user | assistant

data class AiConfig(val provider: String, val model: String, val baseUrl: String, val apiKey: String)

class AiException(message: String, val retryable: Boolean = false, val code: Int = 0) : Exception(message)

/** Add new providers by implementing this interface and registering them in [AiFactory]. */
interface AiProvider {
    fun stream(system: String, history: List<ChatMsg>): Flow<String>
}

object AiFactory {
    fun create(cfg: AiConfig): AiProvider =
        if (cfg.provider == "gemini") GeminiProvider(cfg) else OpenAiProvider(cfg)
}

internal object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    val json = "application/json; charset=utf-8".toMediaType()
}

/** Streams a server-sent-events response. Cancelling the collector cancels the HTTP call. */
internal fun sseFlow(req: Request, parse: (String) -> String?): Flow<String> = callbackFlow {
    val call = Http.client.newCall(req)
    val job = launch(Dispatchers.IO) {
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty().take(300)
                    throw AiException("HTTP ${resp.code}: $body", resp.code == 429 || resp.code >= 500, resp.code)
                }
                val src = resp.body?.source() ?: throw AiException("Empty response", true)
                while (true) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val t = try { parse(data) } catch (e: Exception) { null }
                    if (!t.isNullOrEmpty()) trySend(t)
                }
            }
            close()
        } catch (e: Exception) {
            close(if (call.isCanceled()) null else e)
        }
    }
    awaitClose {
        call.cancel()
        job.cancel()
    }
}

class GeminiProvider(private val cfg: AiConfig) : AiProvider {
    override fun stream(system: String, history: List<ChatMsg>): Flow<String> {
        val contents = JSONArray()
        history.forEach {
            contents.put(
                JSONObject()
                    .put("role", if (it.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.text)))
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${cfg.model.trim()}:streamGenerateContent?alt=sse"
        val req = Request.Builder()
            .url(url)
            .header("x-goog-api-key", cfg.apiKey)
            .post(body.toString().toRequestBody(Http.json))
            .build()
        return sseFlow(req) { data ->
            val parts = JSONObject(data).optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val sb = StringBuilder()
            if (parts != null) for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
            sb.toString()
        }
    }
}

class OpenAiProvider(private val cfg: AiConfig) : AiProvider {
    override fun stream(system: String, history: List<ChatMsg>): Flow<String> {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        history.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.text)) }
        val body = JSONObject().put("model", cfg.model.trim()).put("messages", msgs).put("stream", true)
        val url = cfg.baseUrl.trim().trimEnd('/') + "/chat/completions"
        val rb = Request.Builder().url(url).post(body.toString().toRequestBody(Http.json))
        if (cfg.apiKey.isNotBlank()) rb.header("Authorization", "Bearer ${cfg.apiKey}")
        return sseFlow(rb.build()) { data ->
            JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("delta")?.optString("content", "")
        }
    }
}
