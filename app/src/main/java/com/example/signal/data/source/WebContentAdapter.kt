package com.example.signal.data.source

import com.example.signal.data.ai.SignalAiService

class WebContentAdapter(
    private val aiService: SignalAiService
) : ContentAdapter {

    override suspend fun analyze(
        url: String
    ): ContentResult {

        val result =
            aiService.understandContent(
                content = url,
                sourceContext = "Web page or general shared URL"
            )

        return ContentResult(
            title = result.title,
            summary = result.summary,
            keywords = result.keywords,
            category = result.category,
            targetLocation = result.targetLocation,
            targetLatitude = result.targetLatitude,
            targetLongitude = result.targetLongitude,
            source = result.source
        )
    }
}
