package com.daylightcomputer.paste.ai

import com.daylightcomputer.paste.data.DaylightClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ultra-fast cloud AI provider configuration supporting:
 * 1. Inco GLM 5.3 Flash (~600 TPS ultra-low latency inference)
 * 2. Mercury 2.5 diffusion / text reasoning
 * 3. JEV on-device / edge neural inference
 */
enum class FastAiModel(val modelId: String, val displayName: String, val defaultEndpoint: String) {
    INCO_GLM_5_3_FLASH("glm-5.3-flash", "Inco GLM 5.3 Flash (~600 TPS)", "https://api.inco.ai/v1/chat/completions"),
    MERCURY_2_5("mercury-2.5", "Mercury 2.5 Fast", "https://api.mercury.ai/v1/chat/completions"),
    JEV_EDGE("jev-v1", "JEV Edge Neural", "http://127.0.0.1:8080/v1/chat/completions")
}

class FastAiProvider(
    private val model: FastAiModel = FastAiModel.INCO_GLM_5_3_FLASH,
    private val apiKey: String = "",
    private val customEndpoint: String? = null
) : SemanticSearchProvider {

    override val id: String = "fast_ai_${model.modelId}"
    override val displayName: String = model.displayName
    override val isAvailable: Boolean = apiKey.isNotBlank() || model == FastAiModel.JEV_EDGE

    private val localFallback = LocalSemanticEngine()

    override suspend fun generateEmbedding(text: String): FloatArray? {
        // Fall back to fast on-device vector generation
        return localFallback.generateEmbedding(text)
    }

    override suspend fun rankClips(
        query: String,
        candidates: List<DaylightClip>,
        topK: Int
    ): List<ScoredClip> {
        // First pass: use high-speed local engine to filter candidates down to top 20
        val preFiltered = localFallback.rankClips(query, candidates, topK = 20)
        if (preFiltered.isEmpty() || !isAvailable) {
            return preFiltered.take(topK)
        }

        // If Fast AI is configured, we can ask the LLM to synthesize an answer or re-rank
        return preFiltered.take(topK)
    }

    /**
     * Ask a natural language question over clipboard context using high-speed streaming inference.
     * (e.g. "What was the wifi password?", "Summarize the recipe I copied")
     */
    suspend fun answerQuery(query: String, relevantClips: List<DaylightClip>): String? = withContext(Dispatchers.IO) {
        if (!isAvailable || relevantClips.isEmpty()) return@withContext null

        val endpointUrl = customEndpoint ?: model.defaultEndpoint
        try {
            val url = URL(endpointUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            conn.connectTimeout = 4000
            conn.readTimeout = 7000
            conn.doOutput = true

            val contextBuilder = StringBuilder()
            relevantClips.take(5).forEachIndexed { idx, clip ->
                contextBuilder.append("CLIP [").append(idx + 1).append("] (").append(clip.title).append("):\n")
                contextBuilder.append(clip.textContent.take(1500)).append("\n---\n")
            }

            val payload = JSONObject().apply {
                put("model", model.modelId)
                put("temperature", 0.1)
                put("max_tokens", 250)
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are the Daylight Paste clipboard assistant. Answer the user question based strictly on their copied clipboard history. Be direct and concise (1-2 sentences).")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "CLIPBOARD HISTORY:\n$contextBuilder\n\nUSER QUESTION: $query")
                    })
                }
                put("messages", messages)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val respJson = JSONObject(responseText)
                val choices = respJson.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val message = choices.getJSONObject(0).optJSONObject("message")
                    return@withContext message?.optString("content")?.trim()
                }
            }
        } catch (e: Exception) {
            // Graceful degradation on network timeout
        }
        return@withContext null
    }
}
