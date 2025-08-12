package com.clipboardsync.app.network.http

import android.util.Log
import com.clipboardsync.app.domain.model.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpClient @Inject constructor() {
    
    private val tag = "HttpClient"
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * 发送POST请求到服务器
     */
    suspend fun postClipboardContent(
        config: AppConfig,
        endpoint: String,
        jsonBody: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${config.httpUrl}$endpoint"
            Log.d(tag, "Sending POST request to: $url")
            Log.d(tag, "Request body: $jsonBody")
            
            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
            
            // 添加认证请求头
            if (config.authKey.isNotEmpty() && config.authValue.isNotEmpty()) {
                requestBuilder.addHeader(config.authKey, config.authValue)
                Log.d(tag, "Added auth header: ${config.authKey}: ${config.authValue}")
            }
            
            // 添加设备ID请求头（如果有）
            if (config.deviceId.isNotEmpty()) {
                requestBuilder.addHeader("Device-ID", config.deviceId)
                Log.d(tag, "Added device ID header: Device-ID: ${config.deviceId}")
            }
            
            val request = requestBuilder.build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.d(tag, "Request successful: $responseBody")
                Result.success(responseBody)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                Log.e(tag, "Request failed: $errorMsg")
                Result.failure(IOException(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(tag, "Request error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 发送GET请求到服务器
     */
    suspend fun getFromServer(
        config: AppConfig,
        endpoint: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${config.httpUrl}$endpoint"
            Log.d(tag, "Sending GET request to: $url")
            
            val requestBuilder = Request.Builder()
                .url(url)
                .get()
            
            // 添加认证请求头
            if (config.authKey.isNotEmpty() && config.authValue.isNotEmpty()) {
                requestBuilder.addHeader(config.authKey, config.authValue)
                Log.d(tag, "Added auth header: ${config.authKey}: ${config.authValue}")
            }
            
            // 添加设备ID请求头（如果有）
            if (config.deviceId.isNotEmpty()) {
                requestBuilder.addHeader("Device-ID", config.deviceId)
                Log.d(tag, "Added device ID header: Device-ID: ${config.deviceId}")
            }
            
            val request = requestBuilder.build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.d(tag, "Request successful: $responseBody")
                Result.success(responseBody)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                Log.e(tag, "Request failed: $errorMsg")
                Result.failure(IOException(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(tag, "Request error", e)
            Result.failure(e)
        }
    }
}
