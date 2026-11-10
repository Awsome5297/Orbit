package com.ai.assistance.mediagen.provider

import com.ai.assistance.mediagen.model.MediaGenerationModel
import com.ai.assistance.mediagen.model.MediaGenerationRequest
import com.ai.assistance.mediagen.model.MediaGenerationTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.ai.assistance.mediagen.provider.providers.aliyun.AliyunMediaGenerationProvider
import com.ai.assistance.mediagen.provider.providers.openai.OpenAIMediaGenerationProvider
import com.ai.assistance.mediagen.provider.providers.openrouter.OpenRouterMediaGenerationProvider

class MediaGenerationManager(
    private val openAI: OpenAIMediaGenerationProvider,
    private val aliyun: AliyunMediaGenerationProvider,
    private val openRouter: OpenRouterMediaGenerationProvider,
) {
    suspend fun create(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationTask> = when (setting) {
        is MediaGenerationProviderSetting.OpenAI -> openAI.create(setting, model, request)
        is MediaGenerationProviderSetting.Aliyun -> aliyun.create(setting, model, request)
        is MediaGenerationProviderSetting.OpenRouter -> openRouter.create(setting, model, request)
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
        is MediaGenerationProviderSetting.Aliyun -> aliyun.query(setting, model, taskId)
        is MediaGenerationProviderSetting.OpenRouter -> openRouter.query(setting, model, taskId)
        else -> Result.failure(
            UnsupportedOperationException("${setting.name} media generation is not implemented yet")
        )
    }

    fun generate(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Flow<MediaGenerationTask> = flow {
        var task = create(setting, model, request).getOrThrow()
        emit(task)
        while (!task.isTerminal) {
            delay(5_000)
            task = query(setting, model, task.id).getOrThrow()
            emit(task)
        }
    }
}
