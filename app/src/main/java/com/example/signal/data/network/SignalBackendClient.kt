package com.example.signal.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object SignalBackendClient {

    private const val BASE_URL =
        "http://10.208.123.102:8000/"


    // =========================================================
    // LONG TIMEOUT HTTP CLIENT
    //
    // YouTube videos can take time because:
    //
    // 1. yt-dlp downloads the video
    // 2. FFmpeg may process it
    // 3. Video uploads to Gemini
    // 4. Gemini processes the video
    // 5. Gemini generates analysis
    //
    // Default OkHttp timeouts are too short.
    // =========================================================

    private val okHttpClient =
        OkHttpClient.Builder()

            // Time allowed to establish connection
            .connectTimeout(
                60,
                TimeUnit.SECONDS
            )

            // Time allowed to send request
            .writeTimeout(
                10,
                TimeUnit.MINUTES
            )

            // IMPORTANT:
            // Time allowed while waiting for FastAPI response
            .readTimeout(
                10,
                TimeUnit.MINUTES
            )

            // Overall network call timeout
            .callTimeout(
                10,
                TimeUnit.MINUTES
            )

            .build()


    val api: SignalBackendApi =
        Retrofit.Builder()

            .baseUrl(
                BASE_URL
            )

            .client(
                okHttpClient
            )

            .addConverterFactory(
                GsonConverterFactory.create()
            )

            .build()

            .create(
                SignalBackendApi::class.java
            )
}
