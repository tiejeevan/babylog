package com.example.service

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiCaregiverService {

    suspend fun askGeminiCaregiver(
        userQuestion: String,
        babyName: String,
        babyAgeMonths: Double,
        lastFeedingSummary: String,
        lastSleepSummary: String,
        patternSummary: String = ""
    ): String = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val response = callGeminiRestApi(
                    apiKey = apiKey,
                    userQuestion = userQuestion,
                    babyName = babyName,
                    babyAgeMonths = babyAgeMonths,
                    lastFeedingSummary = lastFeedingSummary,
                    lastSleepSummary = lastSleepSummary,
                    patternSummary = patternSummary
                )
                if (response.isNotBlank()) return@withContext response
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback intelligent pediatric knowledge engine when key is not provided or offline
        return@withContext generateLocalPediatricAdvice(
            question = userQuestion,
            babyName = babyName,
            ageMonths = babyAgeMonths,
            patternSummary = patternSummary
        )
    }

    private fun callGeminiRestApi(
        apiKey: String,
        userQuestion: String,
        babyName: String,
        babyAgeMonths: Double,
        lastFeedingSummary: String,
        lastSleepSummary: String,
        patternSummary: String
    ): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val ageLabel = if (babyAgeMonths < 1.0) {
            "%.0f".format(babyAgeMonths * 30.4375) + " days"
        } else {
            "%.1f".format(babyAgeMonths) + " months"
        }

        val patternBlock = if (patternSummary.isNotBlank()) {
            " Habit patterns: $patternSummary"
        } else {
            ""
        }

        val systemPrompt = "You are BabyCare Live AI, a gentle, highly knowledgeable pediatric nurse & infant care specialist assistant. " +
                "You assist parents with $babyName (age $ageLabel old). " +
                "Current baby status context: Feeding ($lastFeedingSummary), Sleep ($lastSleepSummary).$patternBlock " +
                "Provide concise, warm, actionable, and safety-focused guidance. Keep answers under 180 words with clear bullet points. " +
                "When habit pattern data is available, reference it briefly to personalize advice."

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "$systemPrompt\n\nParent Question: $userQuestion"))
                    })
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(jsonBody.toString())
            writer.flush()
        }

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(responseText)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text", "")
                }
            }
        }
        return ""
    }

    private fun generateLocalPediatricAdvice(
        question: String,
        babyName: String,
        ageMonths: Double,
        patternSummary: String
    ): String {
        val ageLabel = if (ageMonths < 1.0) {
            "${(ageMonths * 30.4375).toInt()} days"
        } else {
            "${"%.1f".format(ageMonths)} months"
        }
        val patternNote = if (patternSummary.isNotBlank()) {
            "\n• **Your logged patterns**: $patternSummary"
        } else {
            ""
        }
        val q = question.lowercase()
        return when {
            q.contains("feed") || q.contains("milk") || q.contains("formula") || q.contains("breast") ||
                q.contains("pattern") || q.contains("habit") || q.contains("routine") -> {
                "🍼 **Feeding Guidance for $babyName ($ageLabel)**:\n" +
                        "• **Frequency**: At this age, infants typically feed every 2.5 to 3.5 hours (approx. 6–8 feedings per day).\n" +
                        "• **Volume**: Expect around 120ml–180ml (4–6 oz) per bottle feeding if formula or expressed milk.\n" +
                        "• **Hunger Cues**: Look for rooting, lip smacking, bringing hands to mouth before crying begins." +
                        patternNote
            }
            q.contains("sleep") || q.contains("nap") || q.contains("wake") || q.contains("night") -> {
                "😴 **Sleep & Wake Windows for $babyName**:\n" +
                        "• **Wake Windows**: At $ageLabel, watch comfortable wake windows between naps.\n" +
                        "• **Sleep Cues**: Rubbing eyes, turning away from lights/sounds, or quiet yawning mean it's time for quiet wind-down.\n" +
                        "• **Safe Sleep**: Always place $babyName on back in a clean crib with a firm mattress and no loose blankets." +
                        patternNote
            }
            q.contains("diaper") || q.contains("poop") || q.contains("stool") || q.contains("rash") -> {
                "👶 **Diaper & Health Check**:\n" +
                        "• **Expected Wet Diapers**: Aim for 6+ wet diapers per 24 hours as a sign of proper hydration.\n" +
                        "• **Stool Colors**: Mustard yellow or brownish-green soft stools are normal. Contact pediatrician if pale chalky white, red, or dark black.\n" +
                        "• **Rash Care**: Apply zinc oxide barrier cream liberally at each diaper change." +
                        patternNote
            }
            q.contains("fever") || q.contains("temp") || q.contains("sick") || q.contains("medicine") -> {
                "🌡️ **Pediatric Temperature & Fever Rules**:\n" +
                        "• **Normal Range**: Rectal/temporal temperature between 36.5°C – 37.5°C (97.7°F – 99.5°F).\n" +
                        "• **Fever Threshold**: Rectal temperature of 38.0°C (100.4°F) or higher is considered a fever.\n" +
                        "• **Urgent**: If $babyName is under 3 months with a fever >= 38.0°C, seek immediate emergency medical care."
            }
            q.contains("tummy time") || q.contains("milestone") || q.contains("growth") || q.contains("roll") -> {
                "🌟 **Development & Tummy Time**:\n" +
                        "• **Tummy Time**: Aim for 3–5 minute sessions, 3 to 4 times a day while awake and supervised.\n" +
                        "• **Benefits**: Builds neck, shoulder, and core muscles needed for rolling over and sitting up."
            }
            else -> {
                "💡 **BabyCare AI Caregiver Insight for $babyName ($ageLabel)**:\n" +
                        "• Keep routines consistent between all caregivers (Mom, Dad, and family).\n" +
                        "• Track feeding volumes and sleep durations daily to catch subtle changes early.\n" +
                        "• Feel free to log a quick note or milestone whenever $babyName tries something new today!" +
                        patternNote
            }
        }
    }
}
