
package com.example.signal.data.source

import com.example.signal.data.ai.SignalAiService

class SocialContentAdapter(
    private val aiService: SignalAiService,
    private val sourceName: String
) : ContentAdapter {

    override suspend fun analyze(
        url: String
    ): ContentResult {

        val result =
            aiService.understandContent(
                content = url,
                sourceContext = sourceName
            )

        return ContentResult(
            title = result.title,
            summary = result.summary,
            keywords = result.keywords,
            category = result.category,
            targetLocation = result.targetLocation,
            targetLatitude = result.targetLatitude,
            targetLongitude = result.targetLongitude,
            reminderDate = result.reminderDate,
            reminderTime = result.reminderTime,
            detectedUrl = result.detectedUrl,
            source = result.source
        )
    }
}
