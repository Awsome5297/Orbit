package com.ai.assistance.mediagen.provider

import com.ai.assistance.mediagen.model.MediaGenerationModel
import com.ai.assistance.mediagen.model.MediaGenerationRequest
import com.ai.assistance.mediagen.model.MediaGenerationTask

interface MediaGenerationProvider<S : MediaGenerationProviderSetting> {
    suspend fun create(
        setting: S,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationTask>

    suspend fun query(
        setting: S,
        model: MediaGenerationModel,
        taskId: String,
    ): Result<MediaGenerationTask>
}
