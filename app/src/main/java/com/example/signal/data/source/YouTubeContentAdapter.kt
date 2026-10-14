package com.example.signal.data.source

import com.example.signal.data.ai.SignalAiService

class YouTubeContentAdapter(
    private val aiService: SignalAiService
) : ContentAdapter {

    override suspend fun analyze(
        url: String
    ): ContentResult {

        val isShort =
            url.contains(
                "/shorts/",
                ignoreCase = true
            )

        val sourceContext =
            if (isShort) {

                "YouTube Short. Analyze the video or page context available from the shared URL."

            } else {

                "YouTube video. Analyze the video or page context available from the shared URL."
            }

        val result =
            aiService.understandContent(
                content = url,
                sourceContext = sourceContext
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
