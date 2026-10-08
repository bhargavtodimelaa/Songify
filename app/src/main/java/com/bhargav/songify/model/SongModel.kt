package com.bhargav.songify.model

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    @SerializedName("success") val success: Boolean?,
    @SerializedName("data") val data: ApiData?
)

data class ApiData(
    @SerializedName("total") val total: Int?,
    @SerializedName("start") val start: Int?,
    @SerializedName("results") val results: List<Song>?
)

data class Song(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("year") val year: String?,
    @SerializedName("duration") val duration: Int?,
    @SerializedName("label") val label: String?,
    @SerializedName("language") val language: String?,
    @SerializedName("playCount") val playCount: Long?,
    @SerializedName("url") val url: String?,
    @SerializedName("album") val album: Album?,
    @SerializedName("artists") val artists: Artists?,
    @SerializedName("image") val image: List<ImageQuality>?,
    @SerializedName("downloadUrl") val downloadUrl: List<DownloadUrl>?
) {
    fun getBestImageUrl(): String? {
        // Prefer 500x500, then 150x150, then 50x50
        return image?.find { it.quality == "500x500" }?.url
            ?: image?.find { it.quality == "150x150" }?.url
            ?: image?.firstOrNull()?.url
    }

    fun getBestAudioUrl(): String? {
        // Prefer 320kbps, then 160kbps, then highest available
        return downloadUrl?.find { it.quality == "320kbps" }?.url
            ?: downloadUrl?.find { it.quality == "160kbps" }?.url
            ?: downloadUrl?.find { it.quality == "96kbps" }?.url
            ?: downloadUrl?.firstOrNull()?.url
    }

    fun getArtistsString(): String {
        val primary = artists?.primary?.mapNotNull { it.name } ?: emptyList()
        if (primary.isNotEmpty()) return primary.joinToString(", ")
        val all = artists?.all?.mapNotNull { it.name } ?: emptyList()
        return if (all.isNotEmpty()) all.joinToString(", ") else "Unknown Artist"
    }
}

data class Album(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("url") val url: String?
)

data class Artists(
    @SerializedName("primary") val primary: List<Artist>?,
    @SerializedName("featured") val featured: List<Artist>?,
    @SerializedName("all") val all: List<Artist>?
)

data class Artist(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("role") val role: String?,
    @SerializedName("image") val image: List<ImageQuality>?
)

data class ImageQuality(
    @SerializedName("quality") val quality: String?,
    @SerializedName("url") val url: String?
)

data class DownloadUrl(
    @SerializedName("quality") val quality: String?,
    @SerializedName("url") val url: String?
)
