package com.bhargav.songify.network

import com.bhargav.songify.model.ApiResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface JioSaavnApiService {
    @GET("api/search/songs")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse

    companion object {
        private const val BASE_URL = "https://shnwazdev-jiosaavn-apii.vercel.app/"

        val instance: JioSaavnApiService by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(JioSaavnApiService::class.java)
        }
    }
}
