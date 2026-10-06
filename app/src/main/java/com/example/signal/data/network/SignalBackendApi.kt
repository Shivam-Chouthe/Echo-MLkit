package com.example.signal.data.network

import retrofit2.http.Body
import retrofit2.http.POST


data class AnalyzeRequest(
    val content: String
)


data class SocialAnalyzeRequest(
    val url: String
)


data class AnalyzeResponse(
    val title: String,
    val summary: String,
    val keywords: String,
    val category: String,
    val target_location: String,
    val target_latitude: Double? = null,
    val target_longitude: Double? = null
)


interface SignalBackendApi {

    @POST("analyze")
    suspend fun analyzeContent(
        @Body request: AnalyzeRequest
    ): AnalyzeResponse


    @POST("analyze-social")
    suspend fun analyzeSocialContent(
        @Body request: SocialAnalyzeRequest
    ): AnalyzeResponse
}
