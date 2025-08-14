package com.clipboardsync.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipboardsync.app.domain.model.AppConfig
import com.clipboardsync.app.domain.repository.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmsSettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val tag = "SmsSettingsViewModel"
    private val _uiState = MutableStateFlow(SmsSettingsUiState())
    val uiState: StateFlow<SmsSettingsUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            configRepository.getConfig().collect { config ->
                Log.d(tag, "配置更新: autoUploadSms=${config.autoUploadSms}, smsFilterSender=${config.smsFilterSender}")
                _uiState.value = _uiState.value.copy(
                    autoUploadSms = config.autoUploadSms,
                    smsKeywords = config.smsKeywords,
                    smsFilterSender = config.smsFilterSender,
                    trustedSenders = config.trustedSenders,
                    isLoading = false
                )
                Log.d(tag, "UI状态更新: autoUploadSms=${_uiState.value.autoUploadSms}, smsFilterSender=${_uiState.value.smsFilterSender}")
            }
        }
    }

    fun updateAutoUploadSms(enabled: Boolean) {
        viewModelScope.launch {
            Log.d(tag, "更新自动上传短信设置: $enabled")
            val currentConfig = configRepository.getConfig().first()
            val updatedConfig = currentConfig.copy(autoUploadSms = enabled)
            configRepository.updateConfig(updatedConfig)
            Log.d(tag, "配置已保存")
        }
    }

    fun updateSmsFilterSender(enabled: Boolean) {
        viewModelScope.launch {
            Log.d(tag, "更新发送方过滤设置: $enabled")
            val currentConfig = configRepository.getConfig().first()
            val updatedConfig = currentConfig.copy(smsFilterSender = enabled)
            configRepository.updateConfig(updatedConfig)
            Log.d(tag, "发送方过滤配置已保存")
        }
    }

    fun addKeyword(keyword: String) {
        if (keyword.isBlank()) return

        viewModelScope.launch {
            val currentConfig = configRepository.getConfig().first()
            val updatedKeywords = currentConfig.smsKeywords.toMutableList()
            if (!updatedKeywords.contains(keyword.trim())) {
                updatedKeywords.add(keyword.trim())
                val updatedConfig = currentConfig.copy(smsKeywords = updatedKeywords)
                configRepository.updateConfig(updatedConfig)
            }
        }
    }

    fun removeKeyword(keyword: String) {
        viewModelScope.launch {
            val currentConfig = configRepository.getConfig().first()
            val updatedKeywords = currentConfig.smsKeywords.toMutableList()
            updatedKeywords.remove(keyword)
            val updatedConfig = currentConfig.copy(smsKeywords = updatedKeywords)
            configRepository.updateConfig(updatedConfig)
        }
    }

    fun addTrustedSender(sender: String) {
        if (sender.isBlank()) return

        viewModelScope.launch {
            val currentConfig = configRepository.getConfig().first()
            val updatedSenders = currentConfig.trustedSenders.toMutableList()
            if (!updatedSenders.contains(sender.trim())) {
                updatedSenders.add(sender.trim())
                val updatedConfig = currentConfig.copy(trustedSenders = updatedSenders)
                configRepository.updateConfig(updatedConfig)
            }
        }
    }

    fun removeTrustedSender(sender: String) {
        viewModelScope.launch {
            val currentConfig = configRepository.getConfig().first()
            val updatedSenders = currentConfig.trustedSenders.toMutableList()
            updatedSenders.remove(sender)
            val updatedConfig = currentConfig.copy(trustedSenders = updatedSenders)
            configRepository.updateConfig(updatedConfig)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            val currentConfig = configRepository.getConfig().first()
            val defaultConfig = AppConfig()
            val updatedConfig = currentConfig.copy(
                autoUploadSms = defaultConfig.autoUploadSms,
                smsKeywords = defaultConfig.smsKeywords,
                smsFilterSender = defaultConfig.smsFilterSender,
                trustedSenders = defaultConfig.trustedSenders
            )
            configRepository.updateConfig(updatedConfig)
        }
    }
}

data class SmsSettingsUiState(
    val autoUploadSms: Boolean = true,
    val smsKeywords: List<String> = emptyList(),
    val smsFilterSender: Boolean = true,
    val trustedSenders: List<String> = emptyList(),
    val isLoading: Boolean = true
)
