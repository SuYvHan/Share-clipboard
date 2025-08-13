package com.clipboardsync.app.domain.usecase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.clipboardsync.app.domain.model.AppConfig
import com.clipboardsync.app.domain.model.ClipboardItem
import com.clipboardsync.app.domain.model.ClipboardType
import com.clipboardsync.app.domain.repository.ConfigRepository
import com.clipboardsync.app.network.http.ClipboardHttpService
import com.clipboardsync.app.network.websocket.WebSocketClient
import com.clipboardsync.app.util.FileUploadUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadFileUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: ConfigRepository,
    private val clipboardHttpService: ClipboardHttpService,
    private val webSocketClient: WebSocketClient
) {
    
    private val tag = "UploadFileUseCase"
    
    /**
     * 上传图片文件
     */
    suspend fun uploadImage(uri: Uri): Result<String> {
        return try {
            val config = configRepository.getConfig().first()
            
            // 检查是否为图片文件
            if (!FileUploadUtils.isImageFile(context, uri)) {
                return Result.failure(Exception("选择的文件不是图片格式"))
            }
            
            // 转换为Base64（包含data URL前缀）
            val base64Content = FileUploadUtils.imageToBase64WithDataUrl(context, uri)
                ?: return Result.failure(Exception("无法读取图片文件"))
            
            val fileName = FileUploadUtils.getFileName(context, uri) ?: "image.jpg"
            val fileSize = FileUploadUtils.getFileSize(context, uri)
            val mimeType = FileUploadUtils.getMimeType(context, uri) ?: "image/jpeg"
            
            Log.d(tag, "Uploading image: $fileName, size: ${fileSize?.let { FileUploadUtils.formatFileSize(it) }}")
            
            // 创建剪切板项
            val clipboardItem = createClipboardItem(
                type = ClipboardType.image,
                content = base64Content,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                config = config
            )
            
            // 上传到服务器
            uploadToServers(clipboardItem, config)
            
            Result.success("图片上传成功: $fileName")
        } catch (e: Exception) {
            Log.e(tag, "Error uploading image", e)
            Result.failure(e)
        }
    }
    
    /**
     * 上传文件
     */
    suspend fun uploadFile(uri: Uri): Result<String> {
        return try {
            val config = configRepository.getConfig().first()
            
            // 转换为Base64
            val base64Content = FileUploadUtils.fileToBase64(context, uri)
                ?: return Result.failure(Exception("无法读取文件"))
            
            val fileName = FileUploadUtils.getFileName(context, uri)
                ?: return Result.failure(Exception("无法获取文件名"))
            val fileSize = FileUploadUtils.getFileSize(context, uri)
            val mimeType = FileUploadUtils.getMimeType(context, uri) ?: "application/octet-stream"
            
            Log.d(tag, "Uploading file: $fileName, size: ${fileSize?.let { FileUploadUtils.formatFileSize(it) }}")
            
            // 创建剪切板项
            val clipboardItem = createClipboardItem(
                type = ClipboardType.file,
                content = base64Content,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                config = config
            )
            
            // 上传到服务器
            uploadToServers(clipboardItem, config)
            
            Result.success("文件上传成功: $fileName")
        } catch (e: Exception) {
            Log.e(tag, "Error uploading file", e)
            Result.failure(e)
        }
    }
    
    private fun createClipboardItem(
        type: ClipboardType,
        content: String,
        fileName: String,
        fileSize: Long?,
        mimeType: String,
        config: AppConfig
    ): ClipboardItem {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())
        return ClipboardItem(
            id = UUID.randomUUID().toString(),
            type = type,
            content = content,
            deviceId = config.deviceId,
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType,
            createdAt = now,
            updatedAt = now
        )
    }
    
    private suspend fun uploadToServers(clipboardItem: ClipboardItem, config: AppConfig) {
        // 通过WebSocket发送
        if (webSocketClient.isConnected()) {
            webSocketClient.syncClipboardItem(clipboardItem)
            Log.d(tag, "Sent via WebSocket: ${clipboardItem.type}")
        }
        
        // 通过HTTP上传
        val result = clipboardHttpService.uploadClipboardContent(config, clipboardItem)
        result.fold(
            onSuccess = { response ->
                Log.d(tag, "HTTP upload successful: $response")
            },
            onFailure = { error ->
                Log.w(tag, "HTTP upload failed: ${error.message}")
            }
        )
    }
}
