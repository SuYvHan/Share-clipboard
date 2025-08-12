package com.clipboardsync.app.network.http

import android.util.Log
import com.clipboardsync.app.domain.model.AppConfig
import com.clipboardsync.app.domain.model.ClipboardItem
import com.clipboardsync.app.domain.model.ClipboardUploadRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHttpService @Inject constructor(
    private val httpClient: HttpClient
) {
    
    private val tag = "ClipboardHttpService"
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    /**
     * 上传剪切板内容到服务器
     */
    suspend fun uploadClipboardContent(
        config: AppConfig,
        clipboardItem: ClipboardItem
    ): Result<String> {
        try {
            val uploadRequest = ClipboardUploadRequest(
                type = when (clipboardItem.type) {
                    com.clipboardsync.app.domain.model.ClipboardType.text -> "text"
                    com.clipboardsync.app.domain.model.ClipboardType.image -> "image"
                    com.clipboardsync.app.domain.model.ClipboardType.file -> "file"
                },
                content = clipboardItem.content,
                deviceId = config.deviceId
            )
            
            val jsonBody = json.encodeToString(uploadRequest)
            Log.d(tag, "Uploading clipboard content: ${clipboardItem.type}")
            
            return httpClient.postClipboardContent(
                config = config,
                endpoint = "/api/clipboard",
                jsonBody = jsonBody
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload clipboard content", e)
            return Result.failure(e)
        }
    }
    
    /**
     * 从服务器获取剪切板历史
     */
    suspend fun getClipboardHistory(
        config: AppConfig,
        limit: Int = 50
    ): Result<String> {
        return httpClient.getFromServer(
            config = config,
            endpoint = "/api/clipboard/history?limit=$limit"
        )
    }
    
    /**
     * 测试HTTP连接
     */
    suspend fun testConnection(config: AppConfig): Result<String> {
        Log.d(tag, "Testing HTTP connection to: ${config.httpUrl}")
        return httpClient.getFromServer(
            config = config,
            endpoint = "/api/health"
        )
    }
}
