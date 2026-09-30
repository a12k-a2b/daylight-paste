package com.daylightcomputer.paste.ai

import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SemanticSearchTest {

    @Test
    fun testCosineSimilarityIdenticalAndOrthogonal() {
        val v1 = floatArrayOf(1f, 0f, 0f)
        val v2 = floatArrayOf(1f, 0f, 0f)
        val v3 = floatArrayOf(0f, 1f, 0f)
        val v4 = floatArrayOf(-1f, 0f, 0f)

        // Identical vectors should have cosine similarity ~ 1.0
        val simIdentical = VectorUtils.cosineSimilarity(v1, v2)
        assertEquals(1.0f, simIdentical, 1e-4f)

        // Orthogonal vectors should have cosine similarity ~ 0.0
        val simOrthogonal = VectorUtils.cosineSimilarity(v1, v3)
        assertEquals(0.0f, simOrthogonal, 1e-4f)

        // Opposite vectors should have cosine similarity ~ -1.0
        val simOpposite = VectorUtils.cosineSimilarity(v1, v4)
        assertEquals(-1.0f, simOpposite, 1e-4f)
    }

    @Test
    fun testVectorSerializationRoundtrip() {
        val originalFloats = floatArrayOf(0.123f, -0.456f, 0.789f, 0.0f, 1.0f, -1.0f, 3.14159f)
        val bytes = VectorUtils.floatArrayToByteArray(originalFloats)

        assertEquals(originalFloats.size * 4, bytes.size)

        val reconstructed = VectorUtils.byteArrayToFloatArray(bytes)
        assertEquals(originalFloats.size, reconstructed.size)

        for (i in originalFloats.indices) {
            assertEquals(originalFloats[i], reconstructed[i], 1e-6f)
        }
    }

    @Test
    fun testLocalSemanticEngineRanking() = runBlocking {
        val engine = LocalSemanticEngine()

        val clipRecipe = DaylightClip(
            id = 1,
            textContent = "Sourdough bread recipe: mix 500g organic flour, 350ml water, 10g salt, and starter. Bake in dutch oven at 450F.",
            markdownContent = "# Sourdough Recipe\nMix 500g flour...",
            title = "Sourdough Bread Recipe",
            clipType = ClipType.TEXT,
            charCount = 100,
            wordCount = 20,
            sourcePackage = "com.google.chrome",
            isPinned = false,
            pinboard = "ALL",
            createdAt = 1000L
        )

        val clipWifi = DaylightClip(
            id = 2,
            textContent = "Guest router credentials: SSID is DaylightGuest, security WPA3, password is SolOSPaperAmber595.",
            markdownContent = "SSID: DaylightGuest\nPassword: SolOSPaperAmber595",
            title = "Office WiFi Password",
            clipType = ClipType.TEXT,
            charCount = 80,
            wordCount = 12,
            sourcePackage = "com.slack",
            isPinned = false,
            pinboard = "ALL",
            createdAt = 2000L
        )

        val clipCode = DaylightClip(
            id = 3,
            textContent = "fun fetchClips(): Flow<List<DaylightClip>> = flow { emit(db.getAll()) }.catch { e -> emit(emptyList()) }",
            markdownContent = "```kotlin\nfun fetchClips(): Flow<List<DaylightClip>>\n```",
            title = "Kotlin Coroutine Flow",
            clipType = ClipType.CODE,
            charCount = 110,
            wordCount = 15,
            sourcePackage = "com.github.mobile",
            isPinned = false,
            pinboard = "ALL",
            createdAt = 3000L
        )

        val candidates = listOf(clipRecipe, clipWifi, clipCode)

        // 1. Semantic query for wifi / internet access
        val wifiResults = engine.rankClips("how do I connect to the office internet?", candidates)
        assertTrue("Should return matches for wifi query", wifiResults.isNotEmpty())
        assertEquals("Top match should be WiFi clip", 2L, wifiResults.first().clip.id)

        // 2. Semantic query for cooking / baking
        val recipeResults = engine.rankClips("baking ingredients for bread", candidates)
        assertTrue("Should return matches for baking query", recipeResults.isNotEmpty())
        assertEquals("Top match should be Recipe clip", 1L, recipeResults.first().clip.id)

        // 3. Semantic query for software development / code
        val codeResults = engine.rankClips("asynchronous kotlin error handling", candidates)
        assertTrue("Should return matches for code query", codeResults.isNotEmpty())
        assertEquals("Top match should be Code clip", 3L, codeResults.first().clip.id)
    }

    @Test
    fun testVectorNormalization() {
        val unnormalized = floatArrayOf(3.0f, 4.0f) // Magnitude is 5.0
        val normalized = VectorUtils.l2Normalize(unnormalized)

        assertEquals(0.6f, normalized[0], 1e-4f)
        assertEquals(0.8f, normalized[1], 1e-4f)

        // Magnitude should now be exactly 1.0
        val mag = normalized[0] * normalized[0] + normalized[1] * normalized[1]
        assertEquals(1.0f, mag, 1e-4f)
    }
}
