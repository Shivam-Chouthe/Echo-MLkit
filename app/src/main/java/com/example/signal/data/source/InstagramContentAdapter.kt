package com.example.signal.data.source

import com.example.signal.data.ai.SignalAiService

class InstagramContentAdapter(
    private val aiService: SignalAiService
) : ContentAdapter {

    override suspend fun analyze(
        url: String
    ): ContentResult {

        val isReel =
            url.contains(
                "/reel/",
                ignoreCase = true
            )

        val sourceContext =
            if (isReel) {

                "Instagram Reel. Analyze any accessible Reel or page context from this shared URL."

            } else {

                "Instagram post or profile content. Analyze any accessible content from this shared URL."
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
            source = result.source
        )
    }
}
