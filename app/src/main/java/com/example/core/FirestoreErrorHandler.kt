package com.example.core

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }
    val currentUser = auth?.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

object ProfanityFilter {
    private val bannedWords = listOf(
        "fuck", "shit", "bitch", "asshole", "bastard", "dick", "cunt", "slur", "hackersell"
    )

    fun sanitize(input: String): String {
        var result = input.trim()
        for (word in bannedWords) {
            val regex = Regex("\\b$word\\b", RegexOption.IGNORE_CASE)
            result = regex.replace(result, "*".repeat(word.length))
        }
        return result
    }

    fun containsProfanity(input: String): Boolean {
        val lower = input.lowercase()
        return bannedWords.any { Regex("\\b$it\\b").containsMatchIn(lower) }
    }
}

object YouTubeParser {
    private val patterns = listOf(
        Regex("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/shorts/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})"),
        Regex("^([a-zA-Z0-9_-]{11})$")
    )

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

    fun thumbnailUrl(videoId: String): String =
        "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
}
