package com.cineenglish.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var currentBaseUrl = ""
    private var apiService: CineApiService? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Scoring is a server round trip: the wav is uploaded, decoded by a phone
        // recognizer, and compared phone by phone. A cold run measured 29.8 s, so a
        // 30 s read timeout would make the first attempt of a session fail.
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    @Synchronized
    fun getService(baseUrl: String): CineApiService {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (apiService == null || currentBaseUrl != normalized) {
            currentBaseUrl = normalized
            apiService = Retrofit.Builder()
                .baseUrl(normalized)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(CineApiService::class.java)
        }
        return apiService!!
    }
}
