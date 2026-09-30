package com.daylightcomputer.paste.ai

import com.daylightcomputer.paste.data.DaylightClip

/**
 * Result of a semantic similarity evaluation against a clipboard item.
 */
data class ScoredClip(
    val clip: DaylightClip,
    val score: Float,               // Similarity score in [0.0, 1.0]
    val lexicalScore: Float = 0f,   // FTS/Exact substring match component
    val vectorScore: Float = 0f,    // Cosine similarity component
    val matchSnippet: String? = null,
    val explanation: String? = null
)

/**
 * Interface for pluggable semantic search providers on SolOS.
 */
interface SemanticSearchProvider {
    val id: String
    val displayName: String
    val isAvailable: Boolean

    /**
     * Compute an embedding vector for the given text.
     * Returns null if model is offline or uninitialized.
     */
    suspend fun generateEmbedding(text: String): FloatArray?

    /**
     * Rank a list of candidate clips against a natural language query.
     */
    suspend fun rankClips(
        query: String,
        candidates: List<DaylightClip>,
        topK: Int = 50
    ): List<ScoredClip>
}
