package com.example.signal.data.source

interface ContentAdapter {

    suspend fun analyze(
        url: String
    ): ContentResult
}

data class ContentResult(
    val title: String,
    val summary: String,
    val keywords: String,
    val category: String,
    val targetLocation: String,
    val targetLatitude: Double? = null,
    val targetLongitude: Double? = null,
    val source: com.example.signal.data.ai.AiSource
)
