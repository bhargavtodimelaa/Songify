package com.bhargav.songify.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bhargav.songify.model.Song
import com.bhargav.songify.network.JioSaavnApiService
import com.bhargav.songify.player.MusicPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryEntry(
    val song: Song,
    val timestamp: Long,
    val formattedDate: String
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = JioSaavnApiService.instance
    private val playerManager = MusicPlayerManager(application.applicationContext)

    val currentSong: StateFlow<Song?> = playerManager.currentSong
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Int> = playerManager.currentPosition
    val duration: StateFlow<Int> = playerManager.duration
    val playbackError: StateFlow<String?> = playerManager.playbackError
    val isLooping: StateFlow<Boolean> = playerManager.isLooping
    val isShuffled: StateFlow<Boolean> = playerManager.isShuffled
    val sleepTimerMinutes: StateFlow<Int> = playerManager.sleepTimerMinutes

    // History states with date & time
    private val _historyEntries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val historyEntries: StateFlow<List<HistoryEntry>> = _historyEntries.asStateFlow()

    // Recommendations state based on local algorithm
    private val _recommendedSongs = MutableStateFlow<List<Song>>(emptyList())
    val recommendedSongs: StateFlow<List<Song>> = _recommendedSongs.asStateFlow()

    // Liked Songs states
    private val _likedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
    val likedSongs: StateFlow<List<Song>> = _likedSongs.asStateFlow()

    // Home screen states
    private val _teluguSongs = MutableStateFlow<List<Song>>(emptyList())
    val teluguSongs: StateFlow<List<Song>> = _teluguSongs.asStateFlow()

    private val _hindiSongs = MutableStateFlow<List<Song>>(emptyList())
    val hindiSongs: StateFlow<List<Song>> = _hindiSongs.asStateFlow()

    private val _tamilSongs = MutableStateFlow<List<Song>>(emptyList())
    val tamilSongs: StateFlow<List<Song>> = _tamilSongs.asStateFlow()

    private val _punjabiSongs = MutableStateFlow<List<Song>>(emptyList())
    val punjabiSongs: StateFlow<List<Song>> = _punjabiSongs.asStateFlow()

    private val _homeLoading = MutableStateFlow(false)
    val homeLoading: StateFlow<Boolean> = _homeLoading.asStateFlow()

    private val _homeError = MutableStateFlow<String?>(null)
    val homeError: StateFlow<String?> = _homeError.asStateFlow()

    // Search screen states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _homeLoading.value = true
            _homeError.value = null
            try {
                val teluguResp = apiService.searchSongs("telugu songs", limit = 10)
                _teluguSongs.value = teluguResp.data?.results ?: emptyList()

                val hindiResp = apiService.searchSongs("hindi hits", limit = 10)
                _hindiSongs.value = hindiResp.data?.results ?: emptyList()

                val tamilResp = apiService.searchSongs("tamil melody", limit = 10)
                _tamilSongs.value = tamilResp.data?.results ?: emptyList()

                val punjabiResp = apiService.searchSongs("punjabi party", limit = 10)
                _punjabiSongs.value = punjabiResp.data?.results ?: emptyList()

                // Default initial recommendations
                if (_recommendedSongs.value.isEmpty()) {
                    val recResp = apiService.searchSongs("trending hits", limit = 10)
                    _recommendedSongs.value = recResp.data?.results ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _homeError.value = "Failed to load music. Please check your internet connection and try again."
            } finally {
                _homeLoading.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searchError.value = null
            return
        }

        updateRecommendationsBasedOnPreference(query)

        viewModelScope.launch {
            _searchLoading.value = true
            _searchError.value = null
            try {
                val response = apiService.searchSongs(query = query, limit = 20)
                _searchResults.value = response.data?.results ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                _searchResults.value = emptyList()
                _searchError.value = "Search failed. Please check your internet connection."
            } finally {
                _searchLoading.value = false
            }
        }
    }

    private fun updateRecommendationsBasedOnPreference(keyword: String) {
        viewModelScope.launch {
            try {
                val resp = apiService.searchSongs(query = keyword, limit = 10)
                val results = resp.data?.results ?: emptyList()
                if (results.isNotEmpty()) {
                    _recommendedSongs.value = results
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun toggleLike(song: Song) {
        val currentIds = _likedSongIds.value.toMutableSet()
        val currentSongs = _likedSongs.value.toMutableList()
        if (currentIds.contains(song.id)) {
            currentIds.remove(song.id)
            currentSongs.removeAll { it.id == song.id }
        } else {
            currentIds.add(song.id)
            if (currentSongs.none { it.id == song.id }) {
                currentSongs.add(0, song)
            }
        }
        _likedSongIds.value = currentIds
        _likedSongs.value = currentSongs
    }

    fun isLiked(song: Song): Boolean {
        return _likedSongIds.value.contains(song.id)
    }

    fun playSong(song: Song, queue: List<Song> = emptyList(), index: Int = 0) {
        val now = System.currentTimeMillis()
        val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val formatted = dateFormatter.format(Date(now))

        val currentEntries = _historyEntries.value.toMutableList()
        currentEntries.removeAll { it.song.id == song.id }
        currentEntries.add(0, HistoryEntry(song, now, formatted))
        if (currentEntries.size > 50) {
            currentEntries.removeAt(currentEntries.size - 1)
        }
        _historyEntries.value = currentEntries

        val artistPref = song.artists?.primary?.firstOrNull()?.name ?: song.language ?: "hits"
        updateRecommendationsBasedOnPreference(artistPref)

        if (queue.isNotEmpty()) {
            playerManager.playQueue(queue, index)
        } else {
            playerManager.playQueue(listOf(song), 0)
        }
    }

    fun playNext() {
        playerManager.playNext()
    }

    fun playPrevious() {
        playerManager.playPrevious()
    }

    fun toggleLoop() {
        playerManager.toggleLoop()
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun setSleepTimer(minutes: Int) {
        playerManager.setSleepTimer(minutes)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Int) {
        playerManager.seekTo(positionMs)
    }
}
