package com.example.services

import android.util.Log
import com.example.BuildConfig
import com.example.data.models.Flashcard
import com.example.data.models.KeyPointItem
import com.example.data.models.NoteSummary
import com.example.data.models.QuizQuestion
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val apiKey: String
        get() {
            val key = BuildConfig.GEMINI_API_KEY
            return if (key.isNull_or_blank() || key == "MY_GEMINI_API_KEY" || key == "null") "" else key
        }

    fun isApiKeyConfigured(): Boolean = apiKey.isNotBlank()

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }

    private suspend fun executeGeminiRequest(jsonBody: JSONObject, preferredModel: String = "gemini-3.6-flash"): String = withContext(Dispatchers.IO) {
        if (!isApiKeyConfigured()) {
            throw IllegalStateException("Gemini API Key is not configured. Please set GEMINI_API_KEY in the Secrets panel in AI Studio.")
        }

        val modelsToTry = listOf(preferredModel, "gemini-3.5-flash", "gemini-flash-latest").distinct()
        var lastException: Exception? = null

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toString().toRequestBody(mediaType)

        for (modelName in modelsToTry) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val sanitizedUrl = url.replace(apiKey, "REDACTED")
                        Log.e("GeminiService", "API Error (${response.code}) on $sanitizedUrl")
                        
                        val apiErrorMessage = try {
                            JSONObject(responseString).getJSONObject("error").getString("message")
                        } catch (e: Exception) {
                            "HTTP ${response.code}: API error"
                        }

                        if (response.code == 400 && apiErrorMessage.contains("API_KEY_INVALID", ignoreCase = true)) {
                            throw Exception("Invalid Gemini API Key. Please verify your GEMINI_API_KEY in Secrets.")
                        } else if (response.code == 429) {
                            throw Exception("Gemini API rate limit reached. Please try again shortly.")
                        } else if (response.code == 404 || apiErrorMessage.contains("not found", ignoreCase = true) || apiErrorMessage.contains("no longer available", ignoreCase = true)) {
                            // Try next model fallback
                            lastException = Exception(apiErrorMessage)
                            return@use
                        } else {
                            throw Exception(apiErrorMessage)
                        }
                    }

                    val jsonResponse = JSONObject(responseString)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).optString("text", "")
                        }
                    }
                    throw Exception("Empty response received from Gemini model.")
                }
            } catch (e: Exception) {
                if (e.message?.contains("rate limit", ignoreCase = true) == true || 
                    e.message?.contains("Invalid Gemini API Key", ignoreCase = true) == true) {
                    throw e
                }
                lastException = e
            }
        }

        throw lastException ?: Exception("Failed to communicate with Gemini API.")
    }

    private suspend fun callGeminiApi(prompt: String, jsonMode: Boolean = false): String {
        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val generationConfig = JSONObject().apply {
                put("temperature", 0.3)
                if (jsonMode) {
                    put("responseMimeType", "application/json")
                }
            }
            put("generationConfig", generationConfig)
        }

        return executeGeminiRequest(jsonBody)
    }

    // 1. Generate Summary
    suspend fun generateSummary(noteText: String): Result<NoteSummary> = runCatching {
        val prompt = """
            You are an expert study assistant. Analyze the provided study note text and generate a faithful, structured summary grounded STRICTLY in the provided note text.
            
            STRICT GROUNDING & ACCURACY RULES:
            1. SOURCE GROUNDING ONLY: Use ONLY facts, definitions, benefits, and applications explicitly mentioned in the note text. Do NOT introduce external concepts, companies, payment apps, protocols, technologies (e.g. do NOT invent NFC, QR codes, Venmo, Zelle, Paytm, Razorpay, Stripe, SBI YONO, Samsung Pay, biometric authentication, tokenization, or encryption), or general world knowledge not present in the note.
            2. NO EXTRAPOLATION: Do not "complete" or expand the notes beyond what was extracted.
            3. PRESERVE LOGICAL STRUCTURE: Preserve the natural reading order and hierarchy (headings, definitions, lists of uses).
            4. PROPORTIONALITY: Keep the number of takeaways (typically 2 to 4) and breakdown items (typically 2 to 4) proportional to the length and content of the note.
            
            REQUIRED JSON SCHEMA:
            {
              "conciseSummary": "A clean 2-3 sentence overview capturing the core message of the note.",
              "keyTakeaways": [
                {
                  "title": "Specific concept name from the note",
                  "explanation": "Clear, concise explanation grounded entirely in the note text."
                }
              ],
              "detailedBreakdown": [
                {
                  "title": "Topic or Section Title",
                  "explanation": "Thorough breakdown of this section derived directly from the note."
                }
              ]
            }

            Output strictly a valid JSON object matching the schema above.

            Note text:
            \"\"\"
            $noteText
            \"\"\"
        """.trimIndent()

        val rawJson = callGeminiApi(prompt, jsonMode = true)
        val adapter = moshi.adapter(NoteSummary::class.java)
        adapter.fromJson(rawJson) ?: NoteSummary(conciseSummary = rawJson)
    }

    // 2. Generate Key Points & Concepts
    suspend fun generateKeyPoints(noteText: String): Result<List<KeyPointItem>> = runCatching {
        val prompt = """
            You are an expert study assistant. Extract the essential key concepts, core definitions, and primary applications grounded STRICTLY in the provided study note text.

            STRICT GROUNDING RULES:
            1. SOURCE GROUNDING ONLY: Use ONLY facts, definitions, benefits, and applications explicitly mentioned in the note text. Do NOT introduce external concepts, companies, payment apps, protocols, technologies (e.g. do NOT invent NFC, QR codes, Venmo, Zelle, Paytm, Razorpay, Stripe, SBI YONO, Samsung Pay, biometric authentication, tokenization, or encryption), or general world knowledge not present in the note.
            2. NO EXTRAPOLATION: Do not expand or complete the notes with outside information.
            3. STRUCTURE: Each key point MUST be a separate structured object with:
               - "title": A concise, accurate concept name or topic from the note (e.g. "Mobile Payment System", "Digital Payment Solution", "Common Uses"). Avoid generic numbers or vague single words.
               - "explanation": A clear, concise explanation answering what this concept means or does according to the note.
               - "category": One of "Concept", "Definition", "Fact", or "Application".
            4. PROPORTIONALITY: Generate a concise set of key points covering the main concepts (typically 2 to 5 points proportional to note length). Do not output the entire text as a single point.

            Output strictly a valid JSON array of objects with the keys: "title", "explanation", "category".

            Note text:
            \"\"\"
            $noteText
            \"\"\"
        """.trimIndent()

        val rawJson = callGeminiApi(prompt, jsonMode = true)
        val listType = Types.newParameterizedType(List::class.java, KeyPointItem::class.java)
        val adapter = moshi.adapter<List<KeyPointItem>>(listType)
        
        val parsedList: List<KeyPointItem>? = try {
            adapter.fromJson(rawJson)
        } catch (e: Exception) {
            try {
                val jsonObject = org.json.JSONObject(rawJson)
                val keyPointsArray = jsonObject.optJSONArray("keyPoints") ?: jsonObject.optJSONArray("items")
                if (keyPointsArray != null) {
                    adapter.fromJson(keyPointsArray.toString())
                } else null
            } catch (ex: Exception) {
                null
            }
        }
        
        parsedList ?: emptyList()
    }

    // 3. Generate Quizzes (MCQ + Short Answer)
    suspend fun generateQuiz(noteText: String): Result<List<QuizQuestion>> = runCatching {
        val prompt = """
            You are an expert study assistant creating an interactive practice quiz based STRICTLY on the provided study note text.
            
            STRICT GROUNDING RULES:
            1. SOURCE GROUNDING ONLY: Every question, correct answer, and explanation MUST be based strictly and directly on facts, definitions, processes, applications, or concepts that appear in the provided note text.
            2. NO EXTERNAL KNOWLEDGE: Do NOT introduce external concepts, companies, payment apps, protocols, technologies (e.g. do NOT invent NFC, QR codes, Venmo, Zelle, Paytm, Razorpay, Stripe, SBI YONO, Samsung Pay, biometric authentication, tokenization, or encryption if not in the note), or general world knowledge.
            3. DYNAMIC QUESTIONS & ACCURATE OPTIONS:
               - "id": unique string identifier (e.g. "q1", "q2", "q3", "q4")
               - "question": A clear, specific question testing an important fact, definition, benefit, or application from the note.
               - "options": An array of EXACTLY 4 distinct, concise string choices [Option A, Option B, Option C, Option D].
                 * Options must be clean, concise phrases or sentences. Do NOT copy entire paragraphs into an option.
               - "correctAnswerIndex": The EXACT 0-based integer index (0, 1, 2, or 3) indicating which option in "options" is the single correct answer supported by the note text.
                 * Index 0 = Option A
                 * Index 1 = Option B
                 * Index 2 = Option C
                 * Index 3 = Option D
               - "sampleAnswer": ""
               - "explanation": A concise explanation citing the note text explaining why the correct option is right.
               - "isMultipleChoice": true
            4. SYNCHRONIZATION: The option at `options[correctAnswerIndex]` MUST be the single correct answer. Distractors must be plausible alternatives that are incorrect according to the note.
            5. DYNAMIC QUANTITY: Generate an appropriate number of questions proportional to the length and depth of the note (typically 2-3 for short notes, 3-5 for medium notes). Do not force a fixed count.

            Output strictly a valid JSON array of objects conforming to the schema above.

            Note text:
            \"\"\"
            $noteText
            \"\"\"
        """.trimIndent()


        val rawJson = callGeminiApi(prompt, jsonMode = true)
        val listType = Types.newParameterizedType(List::class.java, QuizQuestion::class.java)
        val adapter = moshi.adapter<List<QuizQuestion>>(listType)
        
        val parsedList: List<QuizQuestion>? = try {
            adapter.fromJson(rawJson)
        } catch (e: Exception) {
            try {
                val jsonObject = org.json.JSONObject(rawJson)
                val quizArray = jsonObject.optJSONArray("quizzes") 
                    ?: jsonObject.optJSONArray("quiz")
                    ?: jsonObject.optJSONArray("questions")
                    ?: jsonObject.optJSONArray("items")
                if (quizArray != null) {
                    adapter.fromJson(quizArray.toString())
                } else null
            } catch (ex: Exception) {
                null
            }
        }
        
        val rawQuestions = parsedList ?: emptyList()
        rawQuestions.map { q ->
            if (q.isMultipleChoice && q.options.isNotEmpty()) {
                val validIndex = if (q.correctAnswerIndex in q.options.indices) q.correctAnswerIndex else 0
                q.copy(correctAnswerIndex = validIndex)
            } else {
                q
            }
        }
    }

    // 4. Generate Flashcards
    suspend fun generateFlashcards(noteText: String): Result<List<Flashcard>> = runCatching {
        val prompt = """
            You are an expert study assistant. Create a deck of active-recall study flashcards based STRICTLY on the provided study note text.

            STRICT GROUNDING RULES:
            1. SOURCE GROUNDING ONLY: Every flashcard MUST test concepts, facts, definitions, benefits, and applications explicitly contained in the provided note text. Do NOT introduce external concepts, companies, payment apps, protocols, technologies (e.g. do NOT invent NFC, QR codes, Venmo, Zelle, Paytm, Razorpay, Stripe, SBI YONO, Samsung Pay, biometric authentication, tokenization, or encryption if not in the note), or general world knowledge.
            2. STRUCTURE: Each flashcard MUST be a separate structured object:
               - "id": unique string ID (e.g. "f1", "f2", "f3", "f4")
               - "front": A clear, concise question, concept name, or prompt testing ONE specific fact from the note (e.g. "What is a Mobile Payment System?", "What traditional payment methods are eliminated?", "What are common applications of mobile payments?").
               - "back": A clear, concise answer directly and specifically addressing the front question, fully supported by the note text. Do NOT copy the entire note or unrelated sections into the back.
               - "topic": A concise category or subtopic name from the note (e.g. "Definition", "Benefits", "Applications", "Key Concept").
            3. DYNAMIC QUANTITY: Generate an appropriate number of flashcards proportional to the content depth (typically 2-3 for short notes, 4-6 for medium notes). Do not output duplicate cards.

            Output strictly a valid JSON array of objects with the keys: "id", "front", "back", "topic".

            Note text:
            \"\"\"
            $noteText
            \"\"\"
        """.trimIndent()

        val rawJson = callGeminiApi(prompt, jsonMode = true)
        val listType = Types.newParameterizedType(List::class.java, Flashcard::class.java)
        val adapter = moshi.adapter<List<Flashcard>>(listType)
        
        val parsedList: List<Flashcard>? = try {
            adapter.fromJson(rawJson)
        } catch (e: Exception) {
            try {
                val jsonObject = org.json.JSONObject(rawJson)
                val cardsArray = jsonObject.optJSONArray("flashcards") 
                    ?: jsonObject.optJSONArray("cards")
                    ?: jsonObject.optJSONArray("items")
                if (cardsArray != null) {
                    adapter.fromJson(cardsArray.toString())
                } else null
            } catch (ex: Exception) {
                null
            }
        }
        
        parsedList ?: emptyList()
    }

    // 5. Ask AI Q&A Chat about notes
    suspend fun askAiAboutNote(
        noteText: String,
        userQuestion: String,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): Result<String> = runCatching {
        val jsonBody = JSONObject().apply {
            // System instruction to ground the tutor in the current note
            val systemInstructionObj = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put(
                            "text",
                            """
                            You are NoteSnap AI, an expert, empathetic study tutor assisting a student with their notes.
                            
                            STUDY NOTE CONTEXT:
                            \"\"\"
                            $noteText
                            \"\"\"
                            
                            INSTRUCTIONS:
                            - Answer the student's questions clearly, accurately, and concisely.
                            - Base your answers primarily on the provided study note context.
                            - If the student asks for explanations, simplifications, analogies, or real-world examples related to their note, provide them helpfully while keeping the core facts faithful to the note.
                            - Use bold text, bullet points, or clean formatting to make the answer easy to study.
                            """.trimIndent()
                        )
                    })
                }
                put("parts", partsArray)
            }
            put("systemInstruction", systemInstructionObj)

            val contentsArray = JSONArray().apply {
                // Add conversation history turns
                for ((sender, message) in chatHistory) {
                    if (message.isNotBlank()) {
                        val role = if (sender.equals("user", ignoreCase = true)) "user" else "model"
                        val turnObj = JSONObject().apply {
                            put("role", role)
                            val parts = JSONArray().apply {
                                put(JSONObject().apply { put("text", message) })
                            }
                            put("parts", parts)
                        }
                        put(turnObj)
                    }
                }

                // Add current user question
                val currentQuestionObj = JSONObject().apply {
                    put("role", "user")
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", userQuestion) })
                    }
                    put("parts", parts)
                }
                put(currentQuestionObj)
            }
            put("contents", contentsArray)

            val generationConfig = JSONObject().apply {
                put("temperature", 0.5)
            }
            put("generationConfig", generationConfig)
        }

        executeGeminiRequest(jsonBody, preferredModel = "gemini-3.6-flash")
    }
}
