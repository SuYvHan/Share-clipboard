package com.clipboardsync.app.data.repository

import com.clipboardsync.app.data.local.ConfigManager
import com.clipboardsync.app.domain.model.AppConfig
import com.clipboardsync.app.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val configManager: ConfigManager
) : ConfigRepository {
    
    override fun getConfig(): Flow<AppConfig> = configManager.configFlow
    
    override suspend fun updateConfig(config: AppConfig) {
        configManager.updateConfig(config)
    }
    
    override suspend fun updateServerConfig(host: String, websocketPort: Int, httpPort: Int, deviceId: String, authKey: String, authValue: String) {
        configManager.updateServerConfig(host, websocketPort, httpPort, deviceId, authKey, authValue)
    }
    
    override suspend fun updateDeviceId(deviceId: String) {
        configManager.updateDeviceId(deviceId)
    }
    
    override suspend fun updateSyncSettings(autoSync: Boolean, syncImages: Boolean, syncFiles: Boolean) {
        configManager.updateSyncSettings(autoSync, syncImages, syncFiles)
    }
}
