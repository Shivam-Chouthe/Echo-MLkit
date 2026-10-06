package com.example.signal.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.signal.data.local.MemoryEntity
import com.example.signal.ui.theme.EchoBlack
import com.example.signal.ui.theme.EchoDeleteRed
import com.example.signal.ui.theme.EchoDeleteRedText
import com.example.signal.ui.theme.EchoMintBright
import com.example.signal.ui.theme.EchoTextMuted
import com.example.signal.ui.theme.EchoTextPrimary
import com.example.signal.ui.theme.EchoTextSecondary

@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onMemoryClick: (Long) -> Unit
) {
    val viewModel: LibraryViewModel = viewModel()

    // ── State from ViewModel ───────────────────────────
    val memories by viewModel.memories.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    // ── Category filters ───────────────────────────────
    val categories = listOf("All", "PLACE", "RECIPE", "EVENT", "TOOL")
    var selectedCategory by remember { mutableStateOf("All") }

    // ── Filter logic ───────────────────────────────────
    val baseList = if (isSearching) searchResults else memories

    val displayedMemories = if (selectedCategory == "All") {
        baseList
    } else {
        baseList.filter {
            it.sourceType.equals(selectedCategory, ignoreCase = true) ||
            it.keywords.contains(selectedCategory, ignoreCase = true) ||
            (selectedCategory == "PLACE" && it.targetLocation.isNotBlank())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEEF8F1))
    ) {
        // ── Subtle organic background blobs ─────────────
        Box(
            modifier = Modifier
                .size(340.dp)
                .offset(x = 140.dp, y = (-70).dp)
                .clip(CircleShape)
                .background(Color(0xFFD6F5E3).copy(alpha = 0.5f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // ── Top Header ─────────────────────────────
            LibraryHeader(
                memoryCount = memories.size,
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ── Search bar (pill outline with white fill) ─
            LibrarySearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.search(it) },
                onClear = { viewModel.clearSearch() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Category Filter Pills ──────────────────
            CategoryChips(
                categories = categories,
                selected = selectedCategory,
                onSelect = { selectedCategory = it }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ── "YOUR ECHO" Info Banner Card ───────────
            YourEchoInfoCard(
                count = displayedMemories.size
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Memory List or Empty State ─────────────
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::pullToRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                if (displayedMemories.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    ) {
                        EmptyState(isSearching = isSearching)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 2.dp,
                            bottom = 32.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(items = displayedMemories, key = { it.id }) { memory ->
                            EchoMemoryCard(
                                memory = memory,
                                onClick = { onMemoryClick(memory.id) },
                                onDelete = { viewModel.deleteMemory(memory) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// HEADER (Back button square + Title + Profile avatar)
// ─────────────────────────────────────────────────────────────

@Composable
private fun LibraryHeader(
    memoryCount: Int,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // White rounded square button with back arrow
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF111814),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111814),
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$memoryCount things Echo remembers.",
                    fontSize = 13.sp,
                    color = Color(0xFF75857B)
                )
            }
        }

        // Black circular avatar with white "E"
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(EchoBlack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "E",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SEARCH BAR (Thin border, pill rounded container)
// ─────────────────────────────────────────────────────────────

@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = Color(0xFFD4E6D9),
                shape = RoundedCornerShape(26.dp)
            )
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Search your memories...",
                    color = Color(0xFF75857B),
                    fontSize = 15.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF111814),
                    modifier = Modifier.size(22.dp)
                )
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color(0xFF75857B)
                        )
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────
// CATEGORY CHIPS ROW
// ─────────────────────────────────────────────────────────────

@Composable
private fun CategoryChips(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categories.forEach { category ->
            val isSelected = category.equals(selected, ignoreCase = true)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) Color(0xFF0F1713)
                        else Color.White
                    )
                    .clickable { onSelect(category) }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (category == "All") "All" else category.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Color(0xFF111814),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// YOUR ECHO INFO CARD (Matching Screenshot 1)
// ─────────────────────────────────────────────────────────────

@Composable
private fun YourEchoInfoCard(
    count: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular mint green badge with sparkle
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFB8F5CE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF0F3B28),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "YOUR ECHO",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF75857B),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.8.sp,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Tap a memory to open it.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF111814)
                    )
                }
            }

            Text(
                text = "$count",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111814)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// MEMORY CARD (Matching Screenshot 1)
// ─────────────────────────────────────────────────────────────

@Composable
private fun EchoMemoryCard(
    memory: MemoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // ── Top row: Badge + type/status + chevron ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Rounded square mint badge with crosshair / location icon
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFB8F5CE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = Color(0xFF0F3B28),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = memory.sourceType.ifBlank { "PLACE" }.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111814),
                            letterSpacing = 1.5.sp
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Ready",
                            fontSize = 12.sp,
                            color = Color(0xFF75857B),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                // Right chevron >
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = Color(0xFF75857B),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Title ──
            Text(
                text = memory.title.ifBlank { "Untitled Memory" },
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111814),
                lineHeight = 25.sp
            )

            // ── Subtitle / Details ──
            val subtitleText = when {
                memory.summary.isNotBlank() -> memory.summary
                memory.targetLocation.isNotBlank() -> memory.targetLocation
                else -> memory.content
            }

            if (subtitleText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitleText,
                    fontSize = 14.sp,
                    color = Color(0xFF75857B),
                    maxLines = 2,
                    lineHeight = 19.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── DELETE button (aligned right, soft peach bg with coral text) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFDECE8))
                        .clickable { onDelete() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "DELETE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCE4A3B),
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// EMPTY STATE
// ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(isSearching: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Bookmark,
            contentDescription = null,
            tint = Color(0xFF81C995),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isSearching) "No memories found" else "No memories yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111814)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isSearching) "Try a different search query" else "Save something to Echo to see it here",
            fontSize = 14.sp,
            color = Color(0xFF75857B)
        )
    }
}
