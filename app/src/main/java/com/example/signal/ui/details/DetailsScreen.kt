package com.example.signal.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.signal.data.local.MemoryEntity
import com.example.signal.data.local.SignalDatabase
import com.example.signal.ui.theme.SignalBlue
import com.example.signal.ui.theme.SignalSurfaceSoft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button

class DetailsViewModel(
    private val database: SignalDatabase
) : ViewModel() {

    private val _memory = MutableStateFlow<MemoryEntity?>(null)

    val memory: StateFlow<MemoryEntity?> = _memory

    fun loadMemory(id: Long) {
        viewModelScope.launch {
            _memory.value = database.memoryDao().getMemoryById(id)
        }
    }
}

class DetailsViewModelFactory(
    private val database: SignalDatabase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        return DetailsViewModel(database) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    memoryId: Long,
    onBack: () -> Unit
) {

    val context = androidx.compose.ui.platform.LocalContext.current

    val database = remember {
        SignalDatabase.getDatabase(context)
    }

    val viewModel: DetailsViewModel = viewModel(
        factory = DetailsViewModelFactory(database)
    )

    val memory by viewModel.memory.collectAsState()

    LaunchedEffect(memoryId) {
        viewModel.loadMemory(memoryId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MEMORY",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->

        if (memory == null) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Memory not found",
                    style = MaterialTheme.typography.titleMedium
                )
            }

        } else {

            MemoryDetailsContent(
                memory = memory!!,
                padding = padding,
                context = context
            )
        }
    }
}

@Composable
private fun MemoryDetailsContent(
    memory: MemoryEntity,
    padding: PaddingValues,
    context: android.content.Context
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = memory.title.ifBlank {
                "Untitled Memory"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = null,
                tint = SignalBlue
            )

            Spacer(
                modifier = Modifier.padding(4.dp)
            )

            Text(
                text = memory.sourceType.ifBlank {
                    "Unknown source"
                },
                fontWeight = FontWeight.SemiBold
            )
        }

        memory.reminderDate?.let { date ->
            DetailCard(
                title = if (memory.reminderAt != null) "DATE REMINDER" else "DATE FOUND",
                content = if (memory.reminderAt != null) {
                    "$date · reminders at ${memory.reminderTime ?: "09:00"}, 6 hours before, and 30 minutes before"
                } else {
                    date
                }
            )
        }

        memory.detectedUrl?.let { website ->
            DetailCard(
                title = "WEBSITE FOUND",
                content = website,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = SignalBlue
                    )
                }
            )
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(website)))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Link, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text(text = "Open detected website")
            }
        }

        DetailCard(
            title = "AI SUMMARY",
            content = memory.summary.ifBlank {
                "No summary available."
            }
        )

        DetailCard(
            title = "KEYWORDS",
            content = memory.keywords.ifBlank {
                "No keywords available."
            }
        )

        if (memory.targetLocation.isNotBlank()) {

            DetailCard(
                title = "TARGET LOCATION",
                content = memory.targetLocation,
                icon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = SignalBlue
                    )
                }
            )
        }

        if (memory.captureLocation.isNotBlank()) {

            DetailCard(
                title = "CAPTURE LOCATION",
                content = memory.captureLocation,
                icon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = SignalBlue
                    )
                }
            )
        }

        if (
            memory.targetLatitude != null &&
            memory.targetLongitude != null
        ) {

            DetailCard(
                title = "TARGET COORDINATES",
                content =
                    "${memory.targetLatitude}, ${memory.targetLongitude}",
                icon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = SignalBlue
                    )
                }
            )

            Button(
                onClick = {

                    val latitude =
                        memory.targetLatitude

                    val longitude =
                        memory.targetLongitude

                    val geoUri =
                        Uri.parse(
                            "geo:$latitude,$longitude" +
                                    "?q=$latitude,$longitude(" +
                                    Uri.encode(
                                        memory.targetLocation
                                    ) +
                                    ")"
                        )

                    val mapIntent =
                        Intent(
                            Intent.ACTION_VIEW,
                            geoUri
                        )

                    try {

                        context.startActivity(
                            mapIntent
                        )

                    } catch (
                        exception: Exception
                    ) {

                        val browserUri =
                            Uri.parse(
                                "https://www.google.com/maps/search/" +
                                        "?api=1" +
                                        "&query=$latitude,$longitude"
                            )

                        val browserIntent =
                            Intent(
                                Intent.ACTION_VIEW,
                                browserUri
                            )

                        context.startActivity(
                            browserIntent
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
            ) {

                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.padding(4.dp)
                )

                Text(
                    text = "Open in Maps"
                )
            }
        }

        if (memory.content.isNotBlank()) {

            DetailCard(
                title = "CONTENT",
                content = memory.content
            )
        }

        if (memory.sourceUrl.isNotBlank()) {

            DetailCard(
                title = "ORIGINAL SOURCE",
                content = memory.sourceUrl,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = SignalBlue
                    )
                }
            )
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )
    }
}

@Composable
private fun DetailCard(
    title: String,
    content: String,
    icon: @Composable (() -> Unit)? = null
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SignalSurfaceSoft
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {

        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                if (icon != null) {
                    icon()

                    Spacer(
                        modifier = Modifier.padding(4.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = SignalBlue,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
