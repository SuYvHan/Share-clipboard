package com.clipboardsync.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipboardsync.app.domain.model.AppConfig
import com.clipboardsync.app.domain.repository.ConfigRepository
import com.clipboardsync.app.domain.repository.ClipboardRepository
import com.clipboardsync.app.network.websocket.WebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val clipboardRepository: ClipboardRepository,
    private val webSocketClient: WebSocketClient
) : ViewModel() {

    private val tag = "SettingsViewModel"
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    val config: StateFlow<AppConfig> = configRepository.getConfig()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppConfig()
        )

    init {
        // 初始化时加载当前配置
        loadCurrentConfig()
    }
    
    fun updateServerHost(host: String) {
        _uiState.update { it.copy(serverHost = host) }
    }

    fun updateWebSocketPort(port: String) {
        _uiState.update { it.copy(websocketPort = port) }
    }
    
    fun updateDeviceId(deviceId: String) {
        _uiState.update { it.copy(deviceId = deviceId) }
    }
    
    fun updateAuthKey(authKey: String) {
        _uiState.update { it.copy(authKey = authKey) }
    }

    fun updateAuthValue(authValue: String) {
        _uiState.update { it.copy(authValue = authValue) }
    }
    
    fun updateAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val currentConfig = config.value
                configRepository.updateConfig(currentConfig.copy(autoSync = enabled))
                _uiState.update { it.copy(message = "设置已保存") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "保存失败: ${e.message}") }
            }
        }
    }
    
    fun updateSyncImages(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val currentConfig = config.value
                configRepository.updateConfig(currentConfig.copy(syncImages = enabled))
                _uiState.update { it.copy(message = "设置已保存") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "保存失败: ${e.message}") }
            }
        }
    }
    
    fun updateSyncFiles(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val currentConfig = config.value
                configRepository.updateConfig(currentConfig.copy(syncFiles = enabled))
                _uiState.update { it.copy(message = "设置已保存") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "保存失败: ${e.message}") }
            }
        }
    }
    
    fun updateEnableNotifications(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val currentConfig = config.value
                configRepository.updateConfig(currentConfig.copy(enableNotifications = enabled))
                _uiState.update { it.copy(message = "设置已保存") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "保存失败: ${e.message}") }
            }
        }
    }
    
    fun updateAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val currentConfig = config.value
                configRepository.updateConfig(currentConfig.copy(autoStartOnBoot = enabled))
                _uiState.update { it.copy(message = "设置已保存") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "保存失败: ${e.message}") }
            }
        }
    }
    
    fun updateUseSecureConnection(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val currentConfig = config.value
                configRepository.updateConfig(currentConfig.copy(useSecureConnection = enabled))
                _uiState.update { it.copy(message = "设置已保存") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "保存失败: ${e.message}") }
            }
        }
    }
    
    fun saveServerConfig() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                Log.d(tag, "Saving config - deviceId: '${state.deviceId}', authKey: '${state.authKey}', authValue: '${state.authValue}'")

                // 验证必填字段
                if (state.serverHost.isBlank()) {
                    _uiState.update { it.copy(error = "服务器地址不能为空") }
                    return@launch
                }

                val websocketPort = state.websocketPort.toIntOrNull()
                if (websocketPort == null || websocketPort <= 0 || websocketPort > 65535) {
                    _uiState.update { it.copy(error = "WebSocket端口必须是1-65535之间的数字") }
                    return@launch
                }

                // 保存所有服务器配置（包括认证信息）
                configRepository.updateServerConfig(
                    host = state.serverHost.trim(),
                    websocketPort = websocketPort,
                    deviceId = state.deviceId.trim(),
                    authKey = state.authKey.trim(),
                    authValue = state.authValue.trim()
                )

                Log.d(tag, "Config saved successfully")
                _uiState.update { it.copy(message = "服务器配置已保存", error = null) }

                // 配置保存成功后，尝试连接WebSocket
                tryConnectWebSocket()
            } catch (e: Exception) {
                Log.e(tag, "Failed to save config", e)
                _uiState.update { it.copy(error = "保存失败: ${e.message}") }
            }
        }
    }
    
    fun clearAllData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                clipboardRepository.deleteAllItems()
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        message = "所有数据已清除"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "清除失败: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun testConnection() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val result = clipboardRepository.syncWithServer()
                result.fold(
                    onSuccess = {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                message = "连接测试成功"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "连接测试失败: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "连接测试失败: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun loadCurrentConfig() {
        viewModelScope.launch {
            val currentConfig = config.value
            updateUiFromConfig(currentConfig)
        }
    }

    fun updateUiFromConfig(currentConfig: AppConfig) {
        Log.d(tag, "Updating UI from config - deviceId: '${currentConfig.deviceId}', authKey: '${currentConfig.authKey}', authValue: '${currentConfig.authValue}'")
        _uiState.update {
            it.copy(
                serverHost = currentConfig.serverHost,
                websocketPort = currentConfig.websocketPort.toString(),
                deviceId = currentConfig.deviceId,
                authKey = currentConfig.authKey,
                authValue = currentConfig.authValue
            )
        }
    }
    
    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    private fun tryConnectWebSocket() {
        viewModelScope.launch {
            try {
                val currentConfig = config.value
                Log.d(tag, "Attempting WebSocket connection with config: ${currentConfig.websocketUrl}")
                webSocketClient.connect(currentConfig)
            } catch (e: Exception) {
                Log.e(tag, "Failed to connect WebSocket", e)
                _uiState.update { it.copy(error = "连接失败: ${e.message}") }
            }
        }
    }
}

data class SettingsUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val serverHost: String = "47.239.194.151",
    val websocketPort: String = "3002",
    val deviceId: String = "",
    val authKey: String = "",
    val authValue: String = ""
)
