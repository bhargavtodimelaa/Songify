package com.bhargav.songify.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bhargav.songify.MainActivity
import com.bhargav.songify.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URL

class MusicPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSessionCompat: MediaSessionCompat? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val currentQueue = mutableListOf<Song>()
    private var currentIndex = -1

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private var currentBitmap: Bitmap? = null

    init {
        setupMediaSession()
        createNotificationChannel()
    }

    private fun setupMediaSession() {
        try {
            mediaSessionCompat = MediaSessionCompat(context, "SongifyMediaSession").apply {
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        togglePlayPause()
                    }
                    override fun onPause() {
                        togglePlayPause()
                    }
                    override fun onSkipToNext() {
                        playNext()
                    }
                    override fun onSkipToPrevious() {
                        playPrevious()
                    }
                    override fun onStop() {
                        stop()
                    }
                    override fun onSeekTo(pos: Long) {
                        seekTo(pos.toInt())
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error setting up MediaSessionCompat", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "songify_playback",
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Songify media controls for HyperOS Island & LockScreen"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestAudioAndFocus() {
        try {
            audioManager.requestAudioFocus(
                { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            if (_isPlaying.value) {
                                mediaPlayer?.pause()
                                _isPlaying.value = false
                                updatePlaybackState(false, mediaPlayer?.currentPosition?.toLong() ?: 0L)
                                stopProgressTicker()
                            }
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            if (_isPlaying.value) {
                                mediaPlayer?.pause()
                                _isPlaying.value = false
                                updatePlaybackState(false, mediaPlayer?.currentPosition?.toLong() ?: 0L)
                                stopProgressTicker()
                            }
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            // Focus regained
                        }
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error requesting audio focus", e)
        }
    }

    private fun updateMediaSessionMetadata(song: Song, bitmap: Bitmap?) {
        try {
            val durationMs = (song.duration ?: 0) * 1000L
            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.name)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtistsString())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album?.name)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)

            if (bitmap != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            }
            mediaSessionCompat?.setMetadata(metadataBuilder.build())
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error updating metadata", e)
        }
    }

    private fun showNotification(song: Song, isPlaying: Boolean, bitmap: Bitmap?) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_player", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, "songify_playback")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(song.name ?: "Unknown Song")
                .setContentText(song.getArtistsString())
                .setContentIntent(pendingIntent)
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSessionCompat?.sessionToken)
                )
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1001, builder.build())
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error showing notification", e)
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        currentQueue.clear()
        currentQueue.addAll(songs)
        currentIndex = startIndex
        if (_isShuffled.value) {
            val current = currentQueue[currentIndex]
            currentQueue.shuffle()
            val newIdx = currentQueue.indexOf(current)
            if (newIdx != -1) {
                currentQueue.removeAt(newIdx)
                currentQueue.add(0, current)
                currentIndex = 0
            }
        }
        playSong(currentQueue[currentIndex])
    }

    fun playNext() {
        if (currentQueue.isEmpty()) return
        if (_isLooping.value && _currentSong.value != null) {
            playSong(_currentSong.value!!)
            return
        }
        currentIndex = (currentIndex + 1) % currentQueue.size
        playSong(currentQueue[currentIndex])
    }

    fun playPrevious() {
        if (currentQueue.isEmpty()) return
        currentIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else currentQueue.size - 1
        playSong(currentQueue[currentIndex])
    }

    fun toggleLoop() {
        _isLooping.value = !_isLooping.value
        mediaPlayer?.isLooping = _isLooping.value
    }

    fun toggleShuffle() {
        _isShuffled.value = !_isShuffled.value
        if (_isShuffled.value && currentQueue.size > 1) {
            val current = _currentSong.value
            currentQueue.shuffle()
            current?.let {
                val idx = currentQueue.indexOf(it)
                if (idx != -1) {
                    currentQueue.removeAt(idx)
                    currentQueue.add(0, it)
                    currentIndex = 0
                }
            }
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        if (minutes <= 0) return
        sleepTimerJob = scope.launch {
            delay(minutes * 60 * 1000L)
            stop()
            _sleepTimerMinutes.value = 0
        }
    }

    fun playSong(song: Song) {
        val audioUrl = song.getBestAudioUrl()
        if (audioUrl.isNullOrEmpty()) {
            _playbackError.value = "Invalid or missing audio stream URL."
            return
        }

        _playbackError.value = null
        requestAudioAndFocus()

        // Load artwork bitmap in background
        scope.launch(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            try {
                val imageUrl = song.getBestImageUrl()
                if (!imageUrl.isNullOrEmpty()) {
                    val url = URL(imageUrl)
                    bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Error downloading artwork bitmap", e)
            }

            currentBitmap = bitmap
            withContext(Dispatchers.Main) {
                updateMediaSessionMetadata(song, bitmap)
                showNotification(song, true, bitmap)
            }
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = _isLooping.value
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(audioUrl)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    _currentSong.value = song
                    _isPlaying.value = true
                    _duration.value = mp.duration
                    updatePlaybackState(true, mp.currentPosition.toLong())
                    currentBitmap?.let { showNotification(song, true, it) }
                    startProgressTicker()
                }
                setOnCompletionListener {
                    if (!_isLooping.value) {
                        playNext()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MusicPlayerManager", "MediaPlayer error: what=$what, extra=$extra")
                    _isPlaying.value = false
                    _playbackError.value = "Playback error. Check your network connection."
                    updatePlaybackState(false, 0)
                    stopProgressTicker()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Exception playing song", e)
            _isPlaying.value = false
            _playbackError.value = "Failed to stream audio. Please check connection."
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition
                        _currentPosition.value = pos
                        updatePlaybackState(true, pos.toLong())
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                updatePlaybackState(false, player.currentPosition.toLong())
                stopProgressTicker()
                _currentSong.value?.let { showNotification(it, false, currentBitmap) }
            } else {
                player.start()
                _isPlaying.value = true
                updatePlaybackState(true, player.currentPosition.toLong())
                startProgressTicker()
                _currentSong.value?.let { showNotification(it, true, currentBitmap) }
            }
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let { player ->
            player.seekTo(positionMs)
            _currentPosition.value = positionMs
            updatePlaybackState(player.isPlaying, positionMs.toLong())
        }
    }

    private fun updatePlaybackState(isPlaying: Boolean, positionMs: Long) {
        try {
            val stateBuilder = PlaybackStateCompat.Builder()
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    positionMs,
                    1.0f
                )
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_STOP
                )
            mediaSessionCompat?.setPlaybackState(stateBuilder.build())
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error updating playback state", e)
        }
    }

    fun stop() {
        try {
            stopProgressTicker()
            sleepTimerJob?.cancel()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            _isPlaying.value = false
            _currentSong.value = null
            _currentPosition.value = 0
            _duration.value = 0
            _sleepTimerMinutes.value = 0
            currentBitmap = null
            mediaSessionCompat?.isActive = false
            audioManager.abandonAudioFocus(null)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1001)
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error stopping player", e)
        }
    }
}
