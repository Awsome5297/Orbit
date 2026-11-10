package com.ai.assistance.mediagen.provider

import com.ai.assistance.mediagen.model.MediaGenerationModel
import com.ai.assistance.mediagen.model.MediaGenerationRequest
import com.ai.assistance.mediagen.model.MediaGenerationTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.ai.assistance.mediagen.provider.providers.openai.OpenAIMediaGenerationProvider

class MediaGenerationManager(
    private val openAI: OpenAIMediaGenerationProvider,
) {
    suspend fun create(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationTask> = when (setting) {
        is MediaGenerationProviderSetting.OpenAI -> openAI.create(setting, model, request)
        else -> Result.failure(
            UnsupportedOperationException("${setting.name} media generation is not implemented yet")
        )
    }

    suspend fun query(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        taskId: String,
    ): Result<MediaGenerationTask> = when (setting) {
        is MediaGenerationProviderSetting.OpenAI -> openAI.query(setting, model, taskId)
        else -> Result.failure(
            UnsupportedOperationException("${setting.name} media generation is not implemented yet")
        )
    }

    fun generate(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Flow<MediaGenerationTask> = flow {
        val task = create(setting, model, request).getOrThrow()
        emit(task)
    }
}
