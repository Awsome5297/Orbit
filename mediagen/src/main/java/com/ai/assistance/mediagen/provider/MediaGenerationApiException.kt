package com.ai.assistance.mediagen.provider

class MediaGenerationApiException(
    val provider: String,
    val statusCode: Int,
    val code: String?,
    message: String,
) : Exception(message)
