package com.daylightcomputer.paste.ai

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * High-performance vector math utilities for on-device semantic similarity scoring
 * and SQLite BLOB serialization on the Daylight DC-1 (ARM64 Helio G99).
 */
object VectorUtils {

    /**
     * Compute cosine similarity between two float vectors.
     * Returns a float in [-1.0, 1.0], normalized.
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 0f

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in v1.indices) {
            val a = v1[i].toDouble()
            val b = v2[i].toDouble()
            dot += a * b
            normA += a * a
            normB += b * b
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 1e-9) {
            (dot / denominator).toFloat().coerceIn(-1.0f, 1.0f)
        } else {
            0f
        }
    }

    /**
     * Compute dot product between two float vectors.
     */
    fun dotProduct(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var sum = 0.0
        for (i in v1.indices) {
            sum += v1[i].toDouble() * v2[i].toDouble()
        }
        return sum.toFloat()
    }

    /**
     * Normalize a vector to unit L2 norm.
     */
    fun l2Normalize(v: FloatArray): FloatArray {
        var norm = 0.0
        for (f in v) {
            norm += f.toDouble() * f.toDouble()
        }
        val length = sqrt(norm)
        if (length < 1e-9) return v.clone()

        val normalized = FloatArray(v.size)
        for (i in v.indices) {
            normalized[i] = (v[i] / length).toFloat()
        }
        return normalized
    }

    /**
     * Serialize a FloatArray into a compact Little-Endian ByteArray for SQLite BLOB storage.
     */
    fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    /**
     * Deserialize a Little-Endian ByteArray from SQLite BLOB storage into a FloatArray.
     */
    fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        if (bytes.size % 4 != 0) return FloatArray(0)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        for (i in floats.indices) {
            floats[i] = buffer.float
        }
        return floats
    }

    /**
     * Lightweight English suffix stemmer.
     */
    fun stem(token: String): String {
        var t = token.lowercase()
        if (t.endsWith("sses")) t = t.substring(0, t.length - 2)
        else if (t.endsWith("ies")) t = t.substring(0, t.length - 3) + "y"
        else if (t.endsWith("ing") && t.length > 4) t = t.substring(0, t.length - 3)
        else if (t.endsWith("ed") && t.length > 3) t = t.substring(0, t.length - 2)
        else if (t.endsWith("es") && t.length > 3) t = t.substring(0, t.length - 2)
        else if (t.endsWith("s") && !t.endsWith("ss") && t.length > 2) t = t.substring(0, t.length - 1)
        return t
    }

    /**
     * Tokenize text into normalized lowercase word tokens, stripping punctuation and applying stemming.
     */
    fun tokenize(text: String, applyStemming: Boolean = true): List<String> {
        val raw = text.lowercase()
            .split(Regex("[^a-z0-9_]+"))
            .filter { it.isNotBlank() && it.length > 1 }
        return if (applyStemming) raw.map { stem(it) } else raw
    }
}
