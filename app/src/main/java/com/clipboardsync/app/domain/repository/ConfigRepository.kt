package com.clipboardsync.app.domain.repository

import com.clipboardsync.app.domain.model.AppConfig
import kotlinx.coroutines.flow.Flow

interface ConfigRepository {
    fun getConfig(): Flow<AppConfig>
    suspend fun updateConfig(config: AppConfig)
    suspend fun updateServerConfig(host: String, websocketPort: Int, httpPort: Int = 80, deviceId: String = "", authKey: String = "", authValue: String = "")
    suspend fun updateDeviceId(deviceId: String)
    suspend fun updateSyncSettings(autoSync: Boolean, syncImages: Boolean, syncFiles: Boolean)
}
