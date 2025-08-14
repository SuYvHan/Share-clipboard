package com.clipboardsync.app.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import com.clipboardsync.app.util.ImageSaveUtils
import androidx.lifecycle.viewModelScope
import com.clipboardsync.app.domain.model.AppConfig
import com.clipboardsync.app.domain.model.ClipboardItem
import com.clipboardsync.app.domain.model.ClipboardType
import com.clipboardsync.app.domain.repository.ClipboardRepository
import com.clipboardsync.app.domain.repository.ConfigRepository
import com.clipboardsync.app.domain.usecase.SaveImageUseCase
import com.clipboardsync.app.domain.usecase.UploadFileUseCase
import com.clipboardsync.app.network.http.ClipboardHttpService
import com.clipboardsync.app.network.websocket.WebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val clipboardRepository: ClipboardRepository,
    private val configRepository: ConfigRepository,
    private val saveImageUseCase: SaveImageUseCase,
    private val uploadFileUseCase: UploadFileUseCase,
    private val webSocketClient: WebSocketClient,
    private val clipboardHttpService: ClipboardHttpService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    val config: StateFlow<AppConfig> = configRepository.getConfig()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppConfig()
        )
    
    // 显示来自其他设备的同步内容
    val clipboardItems: StateFlow<List<ClipboardItem>> = clipboardRepository.getAllItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val connectionState: StateFlow<WebSocketClient.ConnectionState?> = webSocketClient.connectionStateFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    init {
        observeWebSocketMessages()
        observeConnectionState()
    }
    
    private fun observeWebSocketMessages() {
        viewModelScope.launch {
            webSocketClient.messageFlow.collect { message ->
                when (message.type) {
                    "sync" -> {
                        message.data?.let { item ->
                            val currentConfig = config.value
                            if (item.deviceId != currentConfig.deviceId) {
                                clipboardRepository.insertItem(item, isSynced = true)
                                _uiState.update { it.copy(lastSyncMessage = "收到同步: ${getContentPreview(item)}") }
                            }
                        }
                    }
                    "delete" -> {
                        message.id?.let { id ->
                            clipboardRepository.deleteItem(id)
                        }
                    }
                    "all_content" -> {
                        // 处理获取到的所有内容
                        message.items?.let { items ->
                            val currentConfig = config.value
                            // 过滤掉来自当前设备的内容，只同步其他设备的内容
                            val itemsToSync = items.filter { it.deviceId != currentConfig.deviceId }
                            if (itemsToSync.isNotEmpty()) {
                                clipboardRepository.insertItems(itemsToSync, isSynced = true)
                            }
                            _uiState.update {
                                it.copy(
                                    lastSyncMessage = "已同步 ${itemsToSync.size} 条记录",
                                    isLoading = false
                                )
                            }
                        } ?: run {
                            _uiState.update {
                                it.copy(
                                    lastSyncMessage = "已同步 ${message.count ?: 0} 条记录",
                                    isLoading = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 观察WebSocket连接状态，连接成功后自动同步所有剪切板内容
     */
    private fun observeConnectionState() {
        viewModelScope.launch {
            webSocketClient.connectionStateFlow.collect { state ->
                when (state) {
                    is WebSocketClient.ConnectionState.Connected -> {
                        Log.d("MainViewModel", "WebSocket connected, requesting all clipboard content")
                        _uiState.update { it.copy(lastSyncMessage = "WebSocket已连接，正在同步...") }
                        // 连接成功后自动请求所有剪切板内容
                        kotlinx.coroutines.delay(1000) // 等待1秒确保连接稳定
                        webSocketClient.requestAllContent(limit = 500)
                        Log.d("MainViewModel", "Requested all content with limit 500")
                    }
                    is WebSocketClient.ConnectionState.Disconnected -> {
                        Log.d("MainViewModel", "WebSocket disconnected")
                        _uiState.update { it.copy(lastSyncMessage = "WebSocket连接断开") }
                    }
                    is WebSocketClient.ConnectionState.Reconnecting -> {
                        Log.d("MainViewModel", "WebSocket reconnecting, attempt: ${state.attempt}")
                        _uiState.update { it.copy(lastSyncMessage = "WebSocket重连中...") }
                    }
                    is WebSocketClient.ConnectionState.Error -> {
                        Log.e("MainViewModel", "WebSocket error: ${state.message}")
                        _uiState.update { it.copy(lastSyncMessage = "WebSocket错误: ${state.message}") }
                    }
                    is WebSocketClient.ConnectionState.Failed -> {
                        Log.e("MainViewModel", "WebSocket failed: ${state.message}")
                        _uiState.update { it.copy(lastSyncMessage = "WebSocket连接失败: ${state.message}") }
                    }
                }
            }
        }
    }
    
    fun filterItems(type: ClipboardType?) {
        _uiState.update { it.copy(selectedFilter = type) }
    }
    
    fun searchItems(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
    
    fun deleteItem(id: String) {
        viewModelScope.launch {
            try {
                clipboardRepository.deleteItem(id)
                if (webSocketClient.isConnected()) {
                    webSocketClient.deleteItem(id)
                }
                _uiState.update { it.copy(message = "删除成功") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "删除失败: ${e.message}") }
            }
        }
    }
    

    
    fun copyToClipboard(@Suppress("UNUSED_PARAMETER") content: String) {
        // 这里需要在Activity中实现，因为需要ClipboardManager
        _uiState.update { it.copy(message = "已复制到剪切板") }
    }
    
    fun syncWithServer() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val result = clipboardRepository.syncWithServer()
                result.fold(
                    onSuccess = { items ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                message = "同步成功，获取到 ${items.size} 条记录"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "同步失败: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "同步失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 同步所有云端剪切板内容
     */
    fun syncAllClipboardFromServer() {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        message = "正在同步所有云端剪切板..."
                    )
                }

                if (webSocketClient.isConnected()) {
                    // 通过WebSocket请求所有内容
                    webSocketClient.requestAllContent(limit = 500)
                    Log.d("MainViewModel", "Requested all content via WebSocket")
                } else {
                    // WebSocket未连接，尝试通过HTTP同步
                    Log.w("MainViewModel", "WebSocket not connected, trying HTTP sync")
                    syncAllViaHttp()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error syncing all clipboard content", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "同步失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 通过HTTP同步所有剪切板内容（WebSocket不可用时的备用方案）
     */
    private suspend fun syncAllViaHttp() {
        try {
            val currentConfig = configRepository.getConfig().first()
            val result = clipboardHttpService.getClipboardHistory(currentConfig, limit = 500)

            result.fold(
                onSuccess = { response ->
                    try {
                        // 解析JSON响应
                        val json = kotlinx.serialization.json.Json {
                            ignoreUnknownKeys = true
                            coerceInputValues = true
                        }
                        val apiResponse = json.decodeFromString<com.clipboardsync.app.domain.model.ApiResponse<List<ClipboardItem>>>(response)

                        if (apiResponse.success && apiResponse.data != null) {
                            val items = apiResponse.data
                            // 过滤掉来自当前设备的内容
                            val itemsToSync = items.filter { it.deviceId != currentConfig.deviceId }

                            if (itemsToSync.isNotEmpty()) {
                                clipboardRepository.insertItems(itemsToSync, isSynced = true)
                            }

                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    message = "HTTP同步成功，获取到 ${itemsToSync.size} 条记录"
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "HTTP同步失败: ${apiResponse.message ?: "未知错误"}"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error parsing HTTP response", e)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "解析响应失败: ${e.message}"
                            )
                        }
                    }
                },
                onFailure = { error ->
                    Log.e("MainViewModel", "HTTP sync failed", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "HTTP同步失败: ${error.message}"
                        )
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error in HTTP sync", e)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "HTTP同步异常: ${e.message}"
                )
            }
        }
    }
    
    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    /**
     * 清除所有本地剪切板记录
     */
    fun clearAllLocalClipboard() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                clipboardRepository.deleteAllItems()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "已清除所有本地剪切板记录"
                    )
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error clearing all clipboard items", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "清除失败: ${e.message}"
                    )
                }
            }
        }
    }
    
    private fun getContentPreview(item: ClipboardItem): String {
        return when (item.type) {
            ClipboardType.text -> item.content.take(20) + if (item.content.length > 20) "..." else ""
            ClipboardType.image -> "图片"
            ClipboardType.file -> item.fileName ?: "文件"
        }
    }
    
    fun getFilteredItems(): StateFlow<List<ClipboardItem>> {
        return combine(
            clipboardItems,
            uiState.map { it.selectedFilter },
            uiState.map { it.searchQuery }
        ) { items, filter, query ->
            var filteredItems = items
            
            // 按类型过滤
            filter?.let { type ->
                filteredItems = filteredItems.filter { it.type == type }
            }
            
            // 按搜索关键词过滤
            if (query.isNotBlank()) {
                filteredItems = filteredItems.filter { 
                    it.content.contains(query, ignoreCase = true) ||
                    (it.fileName?.contains(query, ignoreCase = true) == true)
                }
            }
            
            filteredItems
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun handleImageSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "正在上传图片...") }

            val result = uploadFileUseCase.uploadImage(uri)
            result.fold(
                onSuccess = { message ->
                    _uiState.update { it.copy(isLoading = false, message = message) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = "上传失败: ${error.message}") }
                }
            )
        }
    }

    fun handleFileSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "正在上传文件...") }

            val result = uploadFileUseCase.uploadFile(uri)
            result.fold(
                onSuccess = { message ->
                    _uiState.update { it.copy(isLoading = false, message = message) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = "上传失败: ${error.message}") }
                }
            )
        }
    }

    fun saveImageToGallery(context: Context, item: ClipboardItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "正在保存图片...") }

            try {
                val result = when {
                    // 方案一：如果有Bitmap数据，直接保存
                    item.content.startsWith("data:") -> {
                        val bitmap = com.clipboardsync.app.util.ImageUtils.base64ToBitmap(item.content)
                        if (bitmap != null) {
                            ImageSaveUtils.saveBitmapToGallery(context, bitmap, item.fileName ?: "clipboard_image.jpg")
                        } else {
                            Result.failure(Exception("无法解码图片"))
                        }
                    }
                    // 方案二：从URL下载保存
                    item.type == ClipboardType.image && !item.fileName.isNullOrEmpty() -> {
                        val config = configRepository.getConfig().first()
                        val encodedFileName = java.net.URLEncoder.encode(item.fileName, "UTF-8")
                        val imageUrl = "${config.httpUrl}/api/files/preview?id=${item.id}&name=$encodedFileName"

                        ImageSaveUtils.saveFromUrl(
                            context = context,
                            imageUrl = imageUrl,
                            authKey = config.authKey,
                            authValue = config.authValue,
                            httpClient = com.clipboardsync.app.ClipboardSyncApplication.httpClient
                        )
                    }
                    else -> {
                        Result.failure(Exception("不支持的图片格式"))
                    }
                }

                result.fold(
                    onSuccess = { message ->
                        _uiState.update { it.copy(isLoading = false, message = message) }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isLoading = false, error = "保存失败: ${error.message}") }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "保存失败: ${e.message}") }
            }
        }
    }

    fun uploadText(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, message = "正在上传文本...") }

                val config = configRepository.getConfig().first()
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())

                val clipboardItem = ClipboardItem(
                    id = UUID.randomUUID().toString(),
                    type = ClipboardType.text,
                    content = text.trim(),
                    deviceId = config.deviceId,
                    mimeType = "text/plain",
                    createdAt = now,
                    updatedAt = now
                )

                Log.d("MainViewModel", "Uploading text via WebSocket and HTTP: ${text.take(50)}...")

                var syncSuccess = false

                // 1. 通过WebSocket发送到服务器（和剪切板监测一样）
                if (webSocketClient.isConnected()) {
                    webSocketClient.syncClipboardItem(clipboardItem)
                    syncSuccess = true
                    Log.d("MainViewModel", "Text sent via WebSocket successfully")
                } else {
                    Log.w("MainViewModel", "WebSocket not connected, will try HTTP only")
                }

                // 2. 同时通过HTTP上传到服务器（和剪切板监测一样）
                uploadToHttpServer(clipboardItem, config)

                // 3. 不保存到本地数据库（和剪切板监测一样，直接发送不保存）

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = if (syncSuccess) "文本上传成功" else "文本上传中..."
                    )
                }

                Log.d("MainViewModel", "Text upload completed: ${text.take(50)}...")

            } catch (e: Exception) {
                Log.e("MainViewModel", "Error uploading text", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "文本上传失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 通过HTTP上传到服务器（和剪切板监测使用相同的方法）
     */
    private fun uploadToHttpServer(clipboardItem: ClipboardItem, config: AppConfig) {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Uploading clipboard item to HTTP server: ${config.httpUrl}")
                val result = clipboardHttpService.uploadClipboardContent(config, clipboardItem)
                result.fold(
                    onSuccess = { response ->
                        Log.i("MainViewModel", "HTTP upload successful: $response")
                    },
                    onFailure = { error ->
                        Log.w("MainViewModel", "HTTP upload failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "HTTP upload error", e)
            }
        }
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val selectedFilter: ClipboardType? = null,
    val searchQuery: String = "",
    val lastSyncMessage: String? = null
)
