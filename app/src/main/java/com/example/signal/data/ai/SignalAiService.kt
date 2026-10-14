package com.example.signal.data.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.signal.data.network.AnalyzeRequest
import com.example.signal.data.network.SignalBackendClient
import com.example.signal.data.network.SocialAnalyzeRequest
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate


class SignalAiService {

    private val firebaseModel: GenerativeModel =
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).generativeModel(
            modelName = "gemini-3.7-flash",
            tools = listOf(
                Tool.urlContext()
            )
        )


    suspend fun understandContent(
        content: String,
        sourceContext: String = "General web content"
    ): AiResult {


        val normalizedContent =
            content.trim()


        val isInstagram =
            normalizedContent.contains(
                "instagram.com",
                ignoreCase = true
            ) ||
                    normalizedContent.contains(
                        "instagr.am",
                        ignoreCase = true
                    )


        val isYoutube =
            normalizedContent.contains(
                "youtube.com",
                ignoreCase = true
            ) ||
                    normalizedContent.contains(
                        "youtu.be",
                        ignoreCase = true
                    )


        val isFacebook =
            normalizedContent.contains(
                "facebook.com",
                ignoreCase = true
            ) ||
                    normalizedContent.contains(
                        "fb.watch",
                        ignoreCase = true
                    )


        val isX =
            normalizedContent.contains(
                "x.com",
                ignoreCase = true
            ) ||
                    normalizedContent.contains(
                        "twitter.com",
                        ignoreCase = true
                    )


        val isSocialVideo =
            isInstagram ||
                    isYoutube ||
                    isFacebook ||
                    isX


        // =====================================================
        // SOCIAL VIDEO ANALYSIS
        // =====================================================

        if (isSocialVideo) {

            Log.d(
                "SignalAI",
                "================================="
            )

            Log.d(
                "SignalAI",
                "SOCIAL VIDEO DETECTED"
            )

            Log.d(
                "SignalAI",
                "URL: $normalizedContent"
            )

            Log.d(
                "SignalAI",
                "Calling /analyze-social"
            )

            Log.d(
                "SignalAI",
                "================================="
            )


            try {

                val response =
                    withTimeout(600_000) {

                        SignalBackendClient
                            .api
                            .analyzeSocialContent(

                                SocialAnalyzeRequest(
                                    url = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
                                        .find(normalizedContent)?.value?.trimEnd('.', ',', ')', ']')
                                        ?: normalizedContent,
                                    capture_date = LocalDate.now().toString(),
                                    shared_text = "$sourceContext\n$normalizedContent"
                                )
                            )
                    }


                Log.d(
                    "SignalAI",
                    "================================="
                )

                Log.d(
                    "SignalAI",
                    "SOCIAL RESPONSE RECEIVED"
                )

                Log.d(
                    "SignalAI",
                    "TITLE: ${response.title}"
                )

                Log.d(
                    "SignalAI",
                    "SUMMARY: ${response.summary}"
                )

                Log.d(
                    "SignalAI",
                    "================================="
                )


                return AiResult(

                    title =
                        response.title,

                    summary =
                        response.summary,

                    keywords =
                        response.keywords,

                    category =
                        response.category,

                    targetLocation =
                        response.target_location,

                    targetLatitude = response.target_latitude,

                    targetLongitude = response.target_longitude,

                    reminderDate = response.reminder_date,

                    reminderTime = response.reminder_time,

                    detectedUrl = response.detected_url,

                    source =
                        AiSource.BACKEND
                )


            } catch (e: Exception) {

                Log.e(
                    "SignalAI",
                    "================================="
                )

                Log.e(
                    "SignalAI",
                    "SOCIAL ANALYSIS FAILED"
                )

                Log.e(
                    "SignalAI",
                    "Error: ${e.javaClass.simpleName}"
                )

                Log.e(
                    "SignalAI",
                    "Message: ${e.message}"
                )

                Log.e(
                    "SignalAI",
                    "================================="
                )

                // IMPORTANT:
                // Do NOT silently fall back to /analyze.
                // If social analysis fails, we need to see the
                // real error.

                throw e
            }
        }


        // =====================================================
        // NORMAL FASTAPI ANALYSIS
        // =====================================================

        try {

            Log.d(
                "SignalAI",
                "Calling normal /analyze endpoint"
            )


            val response =
                withTimeout(600_000) {

                    SignalBackendClient
                        .api
                        .analyzeContent(

                            AnalyzeRequest(
                                content = "$sourceContext\n$normalizedContent",
                                capture_date = LocalDate.now().toString()
                            )
                        )
                }


            Log.d(
                "SignalAI",
                "NORMAL BACKEND RESPONSE RECEIVED"
            )


            return AiResult(

                title =
                    response.title,

                summary =
                    response.summary,

                keywords =
                    response.keywords,

                category =
                    response.category,

                targetLocation =
                    response.target_location,

                reminderDate = response.reminder_date,

                reminderTime = response.reminder_time,

                detectedUrl = response.detected_url,

                source =
                    AiSource.BACKEND
            )


        } catch (e: Exception) {

            Log.e(
                "SignalAI",
                "Normal backend failed: ${e.javaClass.simpleName}: ${e.message}"
            )
        }


        // =====================================================
        // FIREBASE FALLBACK
        // =====================================================

        try {

            val isUrl = Regex("https?://", RegexOption.IGNORE_CASE).containsMatchIn(normalizedContent)


            val prompt =
                if (isUrl) {

                    """
                    You are Signal, an AI personal memory assistant.

                    Analyze the content available at this URL.

                    URL:
                    $normalizedContent

                    Return exactly in this format:

                    TITLE:
                    SUMMARY:
                    KEYWORDS:
                    CATEGORY:
                    TARGET_LOCATION:
                    REMINDER_DATE:
                    REMINDER_TIME:
                    DETECTED_URL:

                    Rules:
                    - TITLE: short useful title
                    - SUMMARY: maximum 2 sentences
                    - KEYWORDS: 5 to 10 useful keywords separated by commas
                    - CATEGORY: choose one useful category
                    - TARGET_LOCATION: identify a real-world place if clearly mentioned
                    - Otherwise write NONE
                    - REMINDER_DATE: explicit future actionable date as YYYY-MM-DD, otherwise NONE; exclude publication dates.
                    - REMINDER_TIME: explicit local event time as 24-hour HH:MM, otherwise NONE; do not infer.
                    - DETECTED_URL: external website URL explicitly in the shared content; exclude the shared reel/short URL; otherwise NONE.
                    - Today's local date: ${LocalDate.now()}
                    """.trimIndent()

                } else {

                    """
                    You are Signal, an AI personal memory assistant.

                    Analyze the following content.

                    Return exactly in this format:

                    TITLE:
                    SUMMARY:
                    KEYWORDS:
                    CATEGORY:
                    TARGET_LOCATION:
                    REMINDER_DATE:
                    REMINDER_TIME:
                    DETECTED_URL:

                    Rules:
                    - TITLE: short useful title
                    - SUMMARY: maximum 2 sentences
                    - KEYWORDS: 5 to 10 useful keywords separated by commas
                    - CATEGORY: choose one useful category
                    - TARGET_LOCATION: identify a real-world place if clearly mentioned
                    - Otherwise write NONE
                    - REMINDER_DATE: explicit future actionable date as YYYY-MM-DD, otherwise NONE; exclude publication dates.
                    - REMINDER_TIME: explicit local event time as 24-hour HH:MM, otherwise NONE; do not infer.
                    - DETECTED_URL: external website URL explicitly in the shared content; otherwise NONE.
                    - Today's local date: ${LocalDate.now()}

                    CONTENT:

                    $normalizedContent
                    """.trimIndent()
                }


            val response =
                withTimeout(600_000) {

                    firebaseModel
                        .generateContent(
                            prompt
                        )
                }


            return parseResponse(
                response.text ?: ""
            ).copy(
                source =
                    AiSource.FIREBASE
            )


        } catch (e: Exception) {

            Log.e(
                "SignalAI",
                "Firebase failed: ${e.javaClass.simpleName}: ${e.message}"
            )

            throw e
        }
    }


    suspend fun analyzeImage(context: Context, uri: Uri, ocrText: String): AiResult {
        return try {
            val resolver = context.contentResolver
            val imageBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Unable to read shared image")
            require(imageBytes.isNotEmpty()) { "Shared image is empty" }
            require(imageBytes.size <= 20 * 1024 * 1024) { "Image exceeds 20 MB" }

            val imagePart = MultipartBody.Part.createFormData(
                "file",
                uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "shared-image",
                imageBytes.toRequestBody((resolver.getType(uri) ?: "image/jpeg").toMediaType())
            )
            val ocrPart = ocrText.toRequestBody("text/plain".toMediaType())
            val captureDatePart = LocalDate.now().toString().toRequestBody("text/plain".toMediaType())
            val response = withTimeout(600_000) {
                SignalBackendClient.api.analyzeImage(imagePart, ocrPart, captureDatePart)
            }
            response.toAiResult(AiSource.BACKEND)
        } catch (error: Exception) {
            Log.e("SignalAI", "Gemini image analysis failed; falling back to OCR text", error)
            understandContent(ocrText.ifBlank { "Shared image with no readable OCR text" })
        }
    }

    private fun com.example.signal.data.network.AnalyzeResponse.toAiResult(source: AiSource) = AiResult(
        title = title,
        summary = summary,
        keywords = keywords,
        category = category,
        targetLocation = target_location,
        targetLatitude = target_latitude,
        targetLongitude = target_longitude,
        reminderDate = reminder_date,
        reminderTime = reminder_time,
        detectedUrl = detected_url,
        source = source,
    )

    private fun parseResponse(
        response: String
    ): AiResult {


        fun extract(
            label: String
        ): String {


            val marker =
                "$label:"


            val start =
                response.indexOf(
                    marker
                )


            if (start == -1) {

                return ""
            }


            val valueStart =
                start + marker.length


            val nextLine =
                response.indexOf(
                    "\n",
                    valueStart
                )


            return if (
                nextLine == -1
            ) {

                response
                    .substring(
                        valueStart
                    )
                    .trim()

            } else {

                response
                    .substring(
                        valueStart,
                        nextLine
                    )
                    .trim()
            }
        }


        return AiResult(

            title =
                extract("TITLE"),

            summary =
                extract("SUMMARY"),

            keywords =
                extract("KEYWORDS"),

            category =
                extract("CATEGORY"),

            targetLocation =
                extract("TARGET_LOCATION"),

            reminderDate = extract("REMINDER_DATE").takeIf {
                runCatching { LocalDate.parse(it) }.isSuccess
            },

            detectedUrl = extract("DETECTED_URL").takeIf {
                it.startsWith("https://") || it.startsWith("http://")
            },

            source =
                AiSource.FIREBASE
        )
    }
}


data class AiResult(

    val title: String,

    val summary: String,

    val keywords: String,

    val category: String,

    val targetLocation: String,

    val targetLatitude: Double? = null,

    val targetLongitude: Double? = null,

    val reminderDate: String? = null,

    val reminderTime: String? = null,

    val detectedUrl: String? = null,

    val source: AiSource
)


enum class AiSource {

    BACKEND,

    FIREBASE
}
