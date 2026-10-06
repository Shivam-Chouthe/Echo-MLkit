package com.example.signal.ui.capture

import com.example.signal.data.location.SignalGeofenceManager
import com.example.signal.data.ai.ImageOcrHelper
import androidx.activity.result.PickVisualMediaRequest
import com.example.signal.data.location.LocationGeocoder
import com.example.signal.data.ai.AiSource
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.signal.data.ai.SignalAiService
import com.example.signal.data.local.MemoryEntity
import com.example.signal.data.local.SignalDatabase
import com.example.signal.data.location.LocationHelper
import com.example.signal.data.source.ContentAdapterFactory
import com.example.signal.data.source.SourceDetector
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    sharedText: String? = null
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val locationHelper = remember {
        LocationHelper(context)
    }

    val locationGeocoder = remember {
        LocationGeocoder(context)
    }

    val geofenceManager = remember {
        SignalGeofenceManager(context)
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) {
            // Permission result received.
        }

    LaunchedEffect(Unit) {

        val fineLocationGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted && !coarseLocationGranted) {

            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    var url by remember {
        mutableStateOf(
            extractUrl(sharedText ?: "")
        )
    }

    var showResult by remember {
        mutableStateOf(!sharedText.isNullOrBlank())
    }

    var saved by remember {
        mutableStateOf(false)
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) coroutineScope.launch {
            val extracted = ImageOcrHelper(context).extractTextFromUri(uri)
            url = extracted.ifBlank { "Captured photo/screenshot without detectable text" }
            showResult = true
            saved = false
        }
    }

    var isAnalyzing by remember {
        mutableStateOf(false)
    }

    var aiTitle by remember {
        mutableStateOf("")
    }

    var aiSummary by remember {
        mutableStateOf("")
    }

    var aiKeywords by remember {
        mutableStateOf("")
    }

    var aiTargetLocation by remember {
        mutableStateOf("")
    }

    var aiSource by remember {
        mutableStateOf<AiSource?>(null)
    }

    var targetLatitude by remember {
        mutableStateOf<Double?>(null)
    }

    var targetLongitude by remember {
        mutableStateOf<Double?>(null)
    }

    var modelTargetLatitude by remember { mutableStateOf<Double?>(null) }
    var modelTargetLongitude by remember { mutableStateOf<Double?>(null) }

    var aiError by remember {
        mutableStateOf("")
    }

    LaunchedEffect(sharedText) {

        if (!sharedText.isNullOrBlank()) {

            url = extractUrl(sharedText)

            showResult = true
            saved = false
            isAnalyzing = false
            aiError = ""
            aiSource = null
            aiTitle = ""
            aiSummary = ""
            aiKeywords = ""
            aiTargetLocation = ""
            modelTargetLatitude = null
            modelTargetLongitude = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBack
            ) {

                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                text = "CAPTURE"
            )
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        OutlinedTextField(
            value = url,

            onValueChange = {

                url = it
                showResult = false
                saved = false
                aiError = ""
                aiSource = null
            },

            modifier = Modifier.fillMaxWidth(),

            label = {
                Text("Paste URL")
            },

            placeholder = {
                Text("Instagram Reel URL")
            }
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Button(
            onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ADD IMAGE / SCAN")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {

                showResult = url.isNotBlank()
                saved = false
                aiError = ""
            },

            modifier = Modifier.fillMaxWidth()
        ) {

            Text("DETECT CONTENT")
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        if (showResult) {

            Text(
                text = "CONTENT DETECTED"
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = SourceDetector
                    .detect(url)
                    .displayName
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = url
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Button(
                onClick = {

                    if (isAnalyzing || saved) {
                        return@Button
                    }

                    coroutineScope.launch {

                        isAnalyzing = true
                        aiError = ""

                        // ------------------------------------------------
                        // 1. GET CAPTURE LOCATION
                        // ------------------------------------------------

                        val currentLocation =
                            locationHelper.getCurrentLocation()

                        // ------------------------------------------------
                        // 2. TRY AI
                        // ------------------------------------------------

                        try {

                            val aiService =
                                SignalAiService()

                            val factory =
                                ContentAdapterFactory(
                                    aiService
                                )

                            val adapter =
                                factory.getAdapter(url)

                            val result =
                                adapter.analyze(url)

                            aiTitle =
                                result.title.ifBlank {
                                    "Captured content"
                                }

                            aiSummary =
                                result.summary

                            aiKeywords =
                                result.keywords

                            aiTargetLocation =
                                result.targetLocation

                            modelTargetLatitude = result.targetLatitude
                            modelTargetLongitude = result.targetLongitude

                            aiSource =
                                result.source

                        } catch (e: Exception) {

                            // ------------------------------------------------
                            // AI FAILED / QUOTA EXCEEDED
                            // ------------------------------------------------

                            aiError =
                                "AI unavailable. Saved using local fallback."

                            aiSource = null

                            aiTitle =
                                generateFallbackTitle(url)

                            aiSummary =
                                "Saved content from Signal."

                            aiKeywords =
                                generateFallbackKeywords(url)

                            aiTargetLocation = ""
                        }

                        // ------------------------------------------------
                        // 3. SAVE MEMORY REGARDLESS OF AI RESULT
                        // ------------------------------------------------
                        val geocodedTarget =
                            if (aiTargetLocation.isNotBlank()) {
                                locationGeocoder.findCoordinates(
                                    aiTargetLocation
                                )
                            } else {
                                null
                            }

                        targetLatitude =
                            modelTargetLatitude ?: geocodedTarget?.latitude

                        targetLongitude =
                            modelTargetLongitude ?: geocodedTarget?.longitude

                        val memory =
                            MemoryEntity(

                                title = aiTitle,

                                content = url,

                                sourceUrl = url,

                                sourceType =
                                    SourceDetector
                                        .detect(url)
                                        .displayName,

                                summary = aiSummary,

                                keywords = aiKeywords,

                                captureLocation =
                                    currentLocation
                                        ?.asString()
                                        ?: "",

                                targetLocation =
                                    aiTargetLocation,
                                            targetLatitude =
                                            targetLatitude,

                                targetLongitude =
                                    targetLongitude
                            )


                        val database =
                            SignalDatabase
                                .getDatabase(context)

                        val memoryId =
                            database
                                .memoryDao()
                                .insert(memory)

                        if (
                            targetLatitude != null &&
                            targetLongitude != null &&
                            locationHelper.hasLocationPermission()
                        ) {

                            try {

                                geofenceManager.addGeofence(
                                    memoryId = memoryId,
                                    latitude = targetLatitude!!,
                                    longitude = targetLongitude!!
                                )

                            } catch (_: SecurityException) {
                                // Location permission was not available.
                            }
                        }

                        saved = true

                        isAnalyzing = false
                    }
                },

                modifier = Modifier.fillMaxWidth(),

                enabled = !isAnalyzing && !saved
            ) {

                if (isAnalyzing) {

                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp)
                    )

                } else {

                    Text(
                        text =
                            if (saved) {
                                "SAVED TO SIGNAL ✓"
                            } else {
                                "UNDERSTAND & SAVE"
                            }
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            if (isAnalyzing) {

                Text(
                    text =
                        "Signal is understanding this..."
                )
            }

            if (aiError.isNotBlank()) {

                Text(
                    text = aiError
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
            }

            if (saved) {

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                Text(
                    text = "SIGNAL UNDERSTANDING"
                )

                if (aiSource != null) {

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "AI ENGINE"
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = when (aiSource) {
                            AiSource.BACKEND -> "FASTAPI + GEMINI"
                            AiSource.FIREBASE -> "FIREBASE AI + GEMINI"
                            null -> "LOCAL FALLBACK"
                        }
                    )
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "TITLE"
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = aiTitle
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "SUMMARY"
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text =
                        aiSummary.ifBlank {
                            "No summary generated."
                        }
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "KEYWORDS"
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text =
                        aiKeywords.ifBlank {
                            "No keywords generated."
                        }
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "TARGET LOCATION"
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text =
                        aiTargetLocation.ifBlank {
                            "Not detected"
                        }
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "CAPTURE LOCATION"
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text =
                        "Saved with your current location"
                )
            }
        }
    }
}

private fun generateFallbackTitle(
    text: String
): String {

    val cleaned =
        text
            .substringAfterLast("/")
            .substringBefore("?")
            .replace("-", " ")
            .replace("_", " ")
            .trim()

    return if (
        cleaned.isBlank() ||
        cleaned.startsWith("http")
    ) {
        "Saved memory"
    } else {
        cleaned
            .replaceFirstChar {
                it.uppercase()
            }
    }
}

private fun generateFallbackKeywords(
    text: String
): String {

    val words =
        text
            .replace(
                Regex("""https?://"""),
                ""
            )
            .replace(
                Regex("""[^A-Za-z0-9 ]"""),
                " "
            )
            .split(
                Regex("\\s+")
            )
            .filter {
                it.length >= 3
            }
            .distinct()
            .take(8)

    return words.joinToString(", ")
}

private fun extractUrl(
    text: String
): String {

    val regex =
        Regex(
            """https?://[^\s]+"""
        )

    return regex
        .find(text)
        ?.value
        ?.trim()
        ?: text.trim()
}
