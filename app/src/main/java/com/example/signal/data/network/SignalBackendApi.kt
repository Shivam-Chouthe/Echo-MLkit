package com.example.signal.data.network

import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.POST
import okhttp3.MultipartBody
import okhttp3.RequestBody


data class AnalyzeRequest(
    val content: String,
    val capture_date: String? = null
)


data class SocialAnalyzeRequest(
    val url: String,
    val capture_date: String? = null,
    val shared_text: String? = null
)


data class AnalyzeResponse(
    val title: String,
    val summary: String,
    val keywords: String,
    val category: String,
    val target_location: String,
    val target_latitude: Double? = null,
    val target_longitude: Double? = null,
    val reminder_date: String? = null,
    val reminder_time: String? = null,
    val detected_url: String? = null
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

    @Multipart
    @POST("analyze-image")
    suspend fun analyzeImage(
        @Part file: MultipartBody.Part,
        @Part("ocr_text") ocrText: RequestBody,
        @Part("capture_date") captureDate: RequestBody
    ): AnalyzeResponse
}
