package com.example.signal.data.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** On-device OCR for shared images and images selected by the user. */
class ImageOcrHelper(context: Context) {
    private val appContext = context.applicationContext

    suspend fun extractTextFromUri(uri: Uri): String = try {
        val image = InputImage.fromFilePath(appContext, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text.trim())
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume("")
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.resume("")
                    }
            }
        } finally {
            recognizer.close()
        }
    } catch (_: Exception) {
        ""
    }
}
