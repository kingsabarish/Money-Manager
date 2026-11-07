package com.moneymanager.data.categorization

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface CloudGeminiClient {
    suspend fun generateContent(
        apiKey: String,
        prompt: String,
        systemInstruction: String? = null,
    ): Result<String>
}

@Singleton
class CloudGeminiClientImpl @Inject constructor() : CloudGeminiClient {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

    override suspend fun generateContent(
        apiKey: String,
        prompt: String,
        systemInstruction: String?,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmedKey = apiKey.trim()
                if (trimmedKey.isBlank()) {
                    throw IllegalArgumentException("Gemini API key is blank")
                }

                val candidateModels =
                    listOf(
                        "gemini-flash-lite-latest",
                        "gemini-2.5-flash-lite",
                        "gemini-flash-latest",
                        "gemini-3.8-flash",
                    )
                val requestJson =
                    JSONObject().apply {
                        val contentsArray =
                            JSONArray().apply {
                                put(
                                    JSONObject().apply {
                                        put(
                                            "parts",
                                            JSONArray().apply {
                                                put(JSONObject().apply { put("text", prompt) })
                                            },
                                        )
                                    },
                                )
                            }
                        put("contents", contentsArray)

                        systemInstruction?.takeIf { it.isNotBlank() }?.let { sys ->
                            put(
                                "systemInstruction",
                                JSONObject().apply {
                                    put(
                                        "parts",
                                        JSONArray().apply {
                                            put(JSONObject().apply { put("text", sys) })
                                        },
                                    )
                                },
                            )
                        }

                        put(
                            "generationConfig",
                            JSONObject().apply {
                                put("temperature", 0.1)
                                put("responseMimeType", "application/json")
                            },
                        )
                    }

                val requestBodyString = requestJson.toString()
                var lastException: Exception? = null

                for (model in candidateModels) {
                    val url =
                        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$trimmedKey"

                    val request =
                        Request.Builder()
                            .url(url)
                            .post(requestBodyString.toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()

                    try {
                        val response = client.newCall(request).execute()
                        val resultText =
                            response.use { resp ->
                                val bodyString = resp.body.string()
                                if (!resp.isSuccessful) {
                                    Log.w(
                                        "CloudGeminiClient",
                                        "Gemini API error ${resp.code} for model $model: $bodyString",
                                    )
                                    lastException =
                                        RuntimeException("Model $model returned HTTP ${resp.code}: $bodyString")
                                    return@use null // try next model in candidateModels
                                }

                                val responseJson = JSONObject(bodyString)
                                val candidates = responseJson.optJSONArray("candidates")
                                if (candidates == null || candidates.length() == 0) {
                                    throw RuntimeException("No candidates returned from Gemini API: $bodyString")
                                }

                                val content = candidates.getJSONObject(0).getJSONObject("content")
                                val parts = content.getJSONArray("parts")
                                if (parts.length() == 0) {
                                    throw RuntimeException("No content parts in Gemini API candidate: $bodyString")
                                }

                                parts.getJSONObject(0).getString("text")
                            }

                        if (resultText != null) {
                            Log.i("CloudGeminiClient", "Successfully received prediction using model $model")
                            return@runCatching resultText
                        }
                    } catch (e: Exception) {
                        lastException = e
                    }
                }



                throw lastException ?: RuntimeException("All candidate Gemini models failed")
            }
        }
}
