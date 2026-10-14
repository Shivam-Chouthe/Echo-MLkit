package com.example.signal.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.signal.data.local.MemoryEntity
import com.example.signal.data.local.SignalDatabase
import com.example.signal.data.location.DatedReminderScheduler
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class LibraryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database =
        SignalDatabase.getDatabase(application)

    private val memoryDao =
        database.memoryDao()

    private val refreshGeneration = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // --------------------------------------------------
    // SEARCH QUERY
    // --------------------------------------------------

    private val _searchQuery =
        MutableStateFlow("")

    val searchQuery: StateFlow<String> =
        _searchQuery.asStateFlow()

    // --------------------------------------------------
    // ALL MEMORIES
    // --------------------------------------------------

    val memories: StateFlow<List<MemoryEntity>> =
        refreshGeneration
            .flatMapLatest { memoryDao.getAllMemories() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun pullToRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                memoryDao.getAllMemoriesSnapshot()
                refreshGeneration.value += 1
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // --------------------------------------------------
    // SEARCH RESULTS
    // --------------------------------------------------

    val searchResults: StateFlow<List<MemoryEntity>> =
        _searchQuery
            .debounce(250)
            .flatMapLatest { query ->

                if (query.isBlank()) {

                    memoryDao.getAllMemories()

                } else {

                    memoryDao.searchMemories(
                        query.trim()
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    // --------------------------------------------------
    // SEARCH STATE
    // --------------------------------------------------

    val isSearching: StateFlow<Boolean> =
        _searchQuery
            .map { query ->

                query.isNotBlank()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false
            )

    // --------------------------------------------------
    // UPDATE SEARCH
    // --------------------------------------------------

    fun search(
        query: String
    ) {

        _searchQuery.value = query
    }

    // --------------------------------------------------
    // CLEAR SEARCH
    // --------------------------------------------------

    fun clearSearch() {
        _searchQuery.value = ""
    }

    // --------------------------------------------------
    // DELETE MEMORY
    // --------------------------------------------------

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            DatedReminderScheduler.cancel(getApplication(), memory.id)
            memoryDao.delete(memory)
        }
    }
}
