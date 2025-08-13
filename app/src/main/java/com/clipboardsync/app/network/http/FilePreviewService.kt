package com.clipboardsync.app.network.http

import android.util.Log
import com.clipboardsync.app.domain.model.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilePreviewService @Inject constructor(
    private val httpClient: OkHttpClient
) {
    
    private val tag = "FilePreviewService"
    
    /**
     * 获取文件预览数据
     * @param itemId 剪切板项的ID（响应中的id字段）
     * @param filePath 文件路径（响应中的filePath字段）
     */
    suspend fun getFilePreview(config: AppConfig, itemId: String, filePath: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${config.httpUrl}/api/files/preview?id=$itemId&name=$filePath"
                Log.d(tag, "Fetching file preview from: $url")
                Log.d(tag, "Using itemId: $itemId, filePath: $filePath")
                
                val request = Request.Builder()
                    .url(url)
                    .addHeader("accept", "application/octet-stream")
                    .addHeader(config.authKey, config.authValue)
                    .get()
                    .build()
                
                val response: Response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        Log.d(tag, "Successfully fetched file preview, size: ${bytes.size} bytes")
                        Result.success(bytes)
                    } else {
                        Log.w(tag, "Response body is null")
                        Result.failure(Exception("Response body is null"))
                    }
                } else {
                    val errorMsg = "HTTP ${response.code}: ${response.message}"
                    Log.e(tag, "File preview request failed: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(tag, "Error fetching file preview", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 获取图片预览数据
     * @param itemId 剪切板项的ID（响应中的id字段）
     * @param filePath 文件路径（响应中的filePath字段）
     */
    suspend fun getImagePreview(config: AppConfig, itemId: String, filePath: String): Result<ByteArray> {
        return getFilePreview(config, itemId, filePath)
    }
}
