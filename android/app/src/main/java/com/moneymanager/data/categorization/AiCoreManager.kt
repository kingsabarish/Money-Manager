package com.moneymanager.data.categorization

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

interface AiCoreManager {
    suspend fun isAiCoreSupported(): Boolean
    suspend fun generateContent(prompt: String): String
}

@Singleton
class AiCoreManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AiCoreManager {

    private val mutex = Mutex()
    private var generativeModel: GenerativeModel? = null

    override suspend fun isAiCoreSupported(): Boolean {
        return try {
            val model = ensureClient()
            val status = model.checkStatus()
            Log.i("AiCoreManager", "ML Kit GenAI checkStatus: $status")
            when (status) {
                FeatureStatus.AVAILABLE -> true
                FeatureStatus.DOWNLOADABLE -> {
                    try {
                        model.download().collect()
                    } catch (e: Exception) {
                        Log.w("AiCoreManager", "Trigger download failed", e)
                    }
                    false
                }
                FeatureStatus.DOWNLOADING -> false
                else -> false
            }
        } catch (e: Throwable) {
            Log.w("AiCoreManager", "AICore / ML Kit Prompt API not supported: ${e.message}")
            false
        }
    }

    private suspend fun ensureClient(): GenerativeModel = mutex.withLock {
        generativeModel?.let { return it }
        val model = Generation.getClient()
        generativeModel = model
        return model
    }

    override suspend fun generateContent(prompt: String): String {
        val model = ensureClient()
        val status = model.checkStatus()
        if (status != FeatureStatus.AVAILABLE) {
            if (status == FeatureStatus.DOWNLOADABLE) {
                model.download().collect()
            }
            throw IllegalStateException("Gemini Nano is not ready (status: $status)")
        }
        val response = model.generateContent(prompt)
        return response.candidates.firstOrNull()?.text ?: ""
    }
}
