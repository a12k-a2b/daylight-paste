package com.daylightcomputer.paste.ai

import android.content.Context
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchResult(
    val scoredClips: List<ScoredClip>,
    val aiAnswer: String? = null,
    val executionTimeMs: Long = 0,
    val isSemantic: Boolean = false
)

/**
 * Singleton orchestrator for semantic search on SolOS.
 */
class SemanticSearchManager private constructor(context: Context) {

    private val db = ClipDatabase.getInstance(context)
    val localEngine = LocalSemanticEngine()
    var activeProvider: SemanticSearchProvider = localEngine
    var fastAiProvider: FastAiProvider? = null

    companion object {
        @Volatile
        private var INSTANCE: SemanticSearchManager? = null

        fun getInstance(context: Context): SemanticSearchManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SemanticSearchManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Configure Fast AI inference (e.g. Inco GLM 5.3 Flash / Mercury 2.5).
     */
    fun configureFastAi(model: FastAiModel, apiKey: String, customEndpoint: String? = null) {
        val provider = FastAiProvider(model, apiKey, customEndpoint)
        fastAiProvider = provider
        activeProvider = provider
    }

    /**
     * Search clips using hybrid lexical + semantic vector ranking.
     */
    suspend fun search(
        query: String,
        filterType: String = "ALL",
        isSemanticMode: Boolean = true
    ): SearchResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val cleanQuery = query.trim()

        if (cleanQuery.isBlank()) {
            val normalClips = db.getClips(filterType = filterType, searchQuery = "")
            return@withContext SearchResult(
                scoredClips = normalClips.map { ScoredClip(it, 1.0f) },
                executionTimeMs = System.currentTimeMillis() - startTime,
                isSemantic = false
            )
        }

        // Get candidate clips from DB
        val candidates = db.getClips(filterType = filterType, searchQuery = "", limit = 500)

        if (!isSemanticMode) {
            // Standard substring search
            val results = db.getClips(filterType = filterType, searchQuery = cleanQuery)
            return@withContext SearchResult(
                scoredClips = results.map { ScoredClip(it, 1.0f) },
                executionTimeMs = System.currentTimeMillis() - startTime,
                isSemantic = false
            )
        }

        // Semantic ranking
        val ranked = activeProvider.rankClips(cleanQuery, candidates, topK = 50)

        // If the query looks like a question ("what is", "where is", "how do", "wifi password?"),
        // and Fast AI is available, attempt answering
        var aiAnswer: String? = null
        if (cleanQuery.endsWith("?") || cleanQuery.startsWith("what", ignoreCase = true) || cleanQuery.startsWith("find", ignoreCase = true)) {
            val fastAi = fastAiProvider
            if (fastAi != null && fastAi.isAvailable && ranked.isNotEmpty()) {
                aiAnswer = fastAi.answerQuery(cleanQuery, ranked.map { it.clip }.take(5))
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        return@withContext SearchResult(
            scoredClips = ranked,
            aiAnswer = aiAnswer,
            executionTimeMs = elapsed,
            isSemantic = true
        )
    }

    /**
     * Automatically compute and store embeddings for clips missing them.
     */
    suspend fun vectorizeMissingClips(limit: Int = 50) = withContext(Dispatchers.IO) {
        val unvectorized = db.getClipsNeedingEmbedding(limit)
        for (clip in unvectorized) {
            val textToEmbed = buildString {
                append(clip.title)
                append(" ")
                append(clip.textContent)
                if (!clip.summary.isNullOrBlank()) {
                    append(" ")
                    append(clip.summary)
                }
            }
            val vec = localEngine.generateEmbedding(textToEmbed)
            if (vec != null) {
                val bytes = VectorUtils.floatArrayToByteArray(vec)
                db.updateClipEmbedding(clip.id, bytes)
            }
        }
    }
}
