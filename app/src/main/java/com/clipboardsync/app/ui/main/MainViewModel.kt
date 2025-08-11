package com.clipboardsync.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipboardsync.app.domain.model.AppConfig
import com.clipboardsync.app.domain.model.ClipboardItem
import com.clipboardsync.app.domain.model.ClipboardType
import com.clipboardsync.app.domain.repository.ClipboardRepository
import com.clipboardsync.app.domain.repository.ConfigRepository
import com.clipboardsync.app.domain.usecase.SaveImageUseCase
import com.clipboardsync.app.network.websocket.WebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val clipboardRepository: ClipboardRepository,
    private val configRepository: ConfigRepository,
    private val saveImageUseCase: SaveImageUseCase,
    private val webSocketClient: WebSocketClient
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    val config: StateFlow<AppConfig> = configRepository.getConfig()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppConfig()
        )
    
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
                        _uiState.update { it.copy(lastSyncMessage = "已同步 ${message.count ?: 0} 条记录") }
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
    
    fun saveImageToGallery(item: ClipboardItem) {
        if (item.type != ClipboardType.image) return
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val result = saveImageUseCase.saveBase64Image(item.content, item.fileName)
                result.fold(
                    onSuccess = { _ ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                message = "图片已保存到相册"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "保存失败: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "保存失败: ${e.message}"
                    )
                }
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
    
    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
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
}

data class MainUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val selectedFilter: ClipboardType? = null,
    val searchQuery: String = "",
    val lastSyncMessage: String? = null
)
