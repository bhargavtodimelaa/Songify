package com.bhargav.songify.data

import android.content.Context
import android.content.SharedPreferences
import com.bhargav.songify.model.Song
import com.bhargav.songify.viewmodel.HistoryEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SongPrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("songify_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveLikedIds(ids: Set<String>) {
        prefs.edit().putStringSet("liked_ids", ids).apply()
    }

    fun getLikedIds(): Set<String> {
        return prefs.getStringSet("liked_ids", emptySet()) ?: emptySet()
    }

    fun saveHistory(entries: List<HistoryEntry>) {
        val json = gson.toJson(entries)
        prefs.edit().putString("history_entries", json).apply()
    }

    fun getHistory(): List<HistoryEntry> {
        val json = prefs.getString("history_entries", null) ?: return emptyList()
        try {
            val type = object : TypeToken<List<HistoryEntry>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    fun saveDownloadedSongs(songs: List<Song>) {
        val json = gson.toJson(songs)
        prefs.edit().putString("downloaded_songs", json).apply()
    }

    fun getDownloadedSongs(): List<Song> {
        val json = prefs.getString("downloaded_songs", null) ?: return emptyList()
        try {
            val type = object : TypeToken<List<Song>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    fun savePreferences(tracker: Map<String, Int>) {
        val json = gson.toJson(tracker)
        prefs.edit().putString("preference_tracker", json).apply()
    }

    fun getPreferences(): MutableMap<String, Int> {
        val json = prefs.getString("preference_tracker", null) ?: return mutableMapOf()
        try {
            val type = object : TypeToken<MutableMap<String, Int>>() {}.type
            return gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            return mutableMapOf()
        }
    }

    fun saveRecommendedSongs(songs: List<Song>) {
        val json = gson.toJson(songs)
        prefs.edit().putString("recommended_songs", json).apply()
    }

    fun getRecommendedSongs(): List<Song> {
        val json = prefs.getString("recommended_songs", null) ?: return emptyList()
        try {
            val type = object : TypeToken<List<Song>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }
}
