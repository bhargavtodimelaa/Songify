package com.bhargav.songify.viewmodel

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bhargav.songify.data.SongPrefsManager
import com.bhargav.songify.model.Song
import com.bhargav.songify.network.JioSaavnApiService
import com.bhargav.songify.player.MusicPlayerManager
import com.bhargav.songify.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
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
    private val prefsManager = SongPrefsManager(application.applicationContext)

    val currentSong: StateFlow<Song?> = playerManager.currentSong
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Int> = playerManager.currentPosition
    val duration: StateFlow<Int> = playerManager.duration
    val playbackError: StateFlow<String?> = playerManager.playbackError
    val isLooping: StateFlow<Boolean> = playerManager.isLooping
    val isShuffled: StateFlow<Boolean> = playerManager.isShuffled
    val sleepTimerMinutes: StateFlow<Int> = playerManager.sleepTimerMinutes
    val queueList: StateFlow<List<Song>> = playerManager.queueList

    // History states with date & time (persisted)
    private val _historyEntries = MutableStateFlow<List<HistoryEntry>>(prefsManager.getHistory())
    val historyEntries: StateFlow<List<HistoryEntry>> = _historyEntries.asStateFlow()

    // Downloaded songs state (persisted)
    private val _downloadedSongs = MutableStateFlow<List<Song>>(prefsManager.getDownloadedSongs())
    val downloadedSongs: StateFlow<List<Song>> = _downloadedSongs.asStateFlow()

    private val _downloadedSongIds = MutableStateFlow<Set<String>>(prefsManager.getDownloadedSongs().map { it.id }.toSet())
    val downloadedSongIds: StateFlow<Set<String>> = _downloadedSongIds.asStateFlow()

    // Download progress map (songId -> percentage)
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    // Recommendations state (persisted)
    private val cachedRecs = prefsManager.getRecommendedSongs()
    private val _recommendedSongs = MutableStateFlow<List<Song>>(cachedRecs)
    val recommendedSongs: StateFlow<List<Song>> = _recommendedSongs.asStateFlow()

    // Preference frequency tracker (persisted)
    private val preferenceTracker = prefsManager.getPreferences()

    // Liked Songs states (persisted)
    private val _likedSongIds = MutableStateFlow<Set<String>>(prefsManager.getLikedIds())
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
        hydrateLikedSongs()
    }

    private fun hydrateLikedSongs() {
        val likedIds = prefsManager.getLikedIds()
        val allKnownSongs = (_historyEntries.value.map { it.song } + _downloadedSongs.value + _recommendedSongs.value).distinctBy { it.id }
        val likedList = allKnownSongs.filter { likedIds.contains(it.id) }
        _likedSongs.value = likedList
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _homeLoading.value = true
            _homeError.value = null
            try {
                val context = getApplication<Application>().applicationContext
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _homeError.value = "No internet connection. Explore offline music below!"
                    _homeLoading.value = false
                    return@launch
                }

                val teluguResp = apiService.searchSongs("telugu songs", limit = 10)
                _teluguSongs.value = teluguResp.data?.results ?: emptyList()

                val hindiResp = apiService.searchSongs("hindi hits", limit = 10)
                _hindiSongs.value = hindiResp.data?.results ?: emptyList()

                val tamilResp = apiService.searchSongs("tamil melody", limit = 10)
                _tamilSongs.value = tamilResp.data?.results ?: emptyList()

                val punjabiResp = apiService.searchSongs("punjabi party", limit = 10)
                _punjabiSongs.value = punjabiResp.data?.results ?: emptyList()

                if (_recommendedSongs.value.isEmpty()) {
                    val recResp = apiService.searchSongs("trending hits", limit = 10)
                    val recs = recResp.data?.results ?: emptyList()
                    _recommendedSongs.value = recs
                    prefsManager.saveRecommendedSongs(recs)
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

        trackPreference(query)

        viewModelScope.launch {
            _searchLoading.value = true
            _searchError.value = null
            try {
                val context = getApplication<Application>().applicationContext
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _searchError.value = "No internet connection."
                    _searchLoading.value = false
                    return@launch
                }

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

    private fun trackPreference(keyword: String) {
        if (keyword.isBlank()) return
        val cleaned = keyword.trim().lowercase()
        val count = (preferenceTracker[cleaned] ?: 0) + 1
        preferenceTracker[cleaned] = count
        prefsManager.savePreferences(preferenceTracker)

        val topPreference = preferenceTracker.maxByOrNull { it.value }?.key ?: "trending hits"
        fetchSingerRecommendations(topPreference)
    }

    private fun fetchSingerRecommendations(singer: String) {
        viewModelScope.launch {
            try {
                val query = "$singer hits"
                val resp = apiService.searchSongs(query = query, limit = 12)
                val results = resp.data?.results ?: emptyList()
                if (results.isNotEmpty()) {
                    _recommendedSongs.value = results
                    prefsManager.saveRecommendedSongs(results)
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
        prefsManager.saveLikedIds(currentIds)
    }

    fun isLiked(song: Song): Boolean {
        return _likedSongIds.value.contains(song.id)
    }

    fun isDownloaded(song: Song): Boolean {
        return _downloadedSongIds.value.contains(song.id)
    }

    fun downloadSong(context: Context, song: Song) {
        val audioUrl = song.getBestAudioUrl()
        if (audioUrl.isNullOrEmpty()) {
            Toast.makeText(context, "Invalid audio URL for download", Toast.LENGTH_SHORT).show()
            return
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            Toast.makeText(context, "No internet connection to download song.", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "song_${song.id}.mp3")
                val url = URL(audioUrl)
                val connection = url.openConnection()
                connection.connect()
                val fileLength = connection.contentLength
                val inputStream = connection.getInputStream()
                val outputStream = FileOutputStream(file)

                val data = ByteArray(4096)
                var count: Int
                var total: Long = 0

                while (inputStream.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    if (fileLength > 0) {
                        val progress = ((total * 100) / fileLength).toInt()
                        val progMap = _downloadProgress.value.toMutableMap()
                        progMap[song.id] = progress
                        _downloadProgress.value = progMap
                    }
                    outputStream.write(data, 0, count)
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                withContext(Dispatchers.Main) {
                    val currentIds = _downloadedSongIds.value.toMutableSet()
                    val currentList = _downloadedSongs.value.toMutableList()
                    currentIds.add(song.id)
                    if (currentList.none { it.id == song.id }) {
                        currentList.add(0, song)
                    }
                    _downloadedSongIds.value = currentIds
                    _downloadedSongs.value = currentList
                    prefsManager.saveDownloadedSongs(currentList)

                    val progMap = _downloadProgress.value.toMutableMap()
                    progMap.remove(song.id)
                    _downloadProgress.value = progMap

                    Toast.makeText(context, "Downloaded successfully to offline storage!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val progMap = _downloadProgress.value.toMutableMap()
                    progMap.remove(song.id)
                    _downloadProgress.value = progMap
                    Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getLocalFileForSong(context: Context, song: Song): File? {
        val file = File(context.filesDir, "song_${song.id}.mp3")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun playSong(song: Song, queue: List<Song> = emptyList(), index: Int = 0, context: Context? = null) {
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
        prefsManager.saveHistory(currentEntries)

        val primarySinger = song.artists?.primary?.firstOrNull()?.name ?: song.artists?.all?.firstOrNull()?.name
        if (!primarySinger.isNullOrBlank()) {
            trackPreference(primarySinger)
        }

        if (context != null) {
            val localFile = getLocalFileForSong(context, song)
            if (localFile != null) {
                playerManager.playLocalFile(song, localFile.absolutePath, queue, index)
                return
            }
        }

        if (queue.isNotEmpty()) {
            playerManager.playQueue(queue, index)
        } else {
            playerManager.playQueue(listOf(song), 0)
        }
    }

    fun clearHistory() {
        _historyEntries.value = emptyList()
        prefsManager.saveHistory(emptyList())
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
