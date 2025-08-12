package com.clipboardsync.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()

    // 当配置变化时直接更新UI状态
    LaunchedEffect(config) {
        viewModel.updateUiFromConfig(config)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 服务器配置
            ServerConfigSection(
                uiState = uiState,
                onServerHostChange = viewModel::updateServerHost,
                onWebSocketPortChange = viewModel::updateWebSocketPort,
                onHttpPortChange = viewModel::updateHttpPort,
                onDeviceIdChange = viewModel::updateDeviceId,
                onAuthKeyChange = viewModel::updateAuthKey,
                onAuthValueChange = viewModel::updateAuthValue,
                onSave = viewModel::saveServerConfig,
                onTestConnection = viewModel::testConnection
            )
            
            HorizontalDivider()

            // 同步设置
            SyncSettingsSection(
                config = config,
                onAutoSyncChange = viewModel::updateAutoSync,
                onSyncImagesChange = viewModel::updateSyncImages,
                onSyncFilesChange = viewModel::updateSyncFiles,
                onUseSecureConnectionChange = viewModel::updateUseSecureConnection
            )

            HorizontalDivider()

            // 应用设置
            AppSettingsSection(
                config = config,
                onEnableNotificationsChange = viewModel::updateEnableNotifications,
                onAutoStartOnBootChange = viewModel::updateAutoStartOnBoot
            )

            HorizontalDivider()
            
            // 数据管理
            DataManagementSection(
                onClearAllData = viewModel::clearAllData,
                isLoading = uiState.isLoading
            )
            
            // 显示消息
            uiState.message?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerConfigSection(
    uiState: SettingsUiState,
    onServerHostChange: (String) -> Unit,
    onWebSocketPortChange: (String) -> Unit,
    onHttpPortChange: (String) -> Unit,
    onDeviceIdChange: (String) -> Unit,
    onAuthKeyChange: (String) -> Unit,
    onAuthValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "服务器配置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            OutlinedTextField(
                value = uiState.serverHost,
                onValueChange = onServerHostChange,
                label = { Text("服务器地址 *") },
                placeholder = { Text("47.239.194.151") },
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.serverHost.isBlank(),
                supportingText = if (uiState.serverHost.isBlank()) {
                    { Text("必填字段", color = MaterialTheme.colorScheme.error) }
                } else null
            )
            
            // WebSocket端口配置
            OutlinedTextField(
                value = uiState.websocketPort,
                onValueChange = onWebSocketPortChange,
                label = { Text("WebSocket端口 *") },
                placeholder = { Text("3002") },
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.websocketPort.toIntOrNull() == null || uiState.websocketPort.isBlank(),
                supportingText = if (uiState.websocketPort.toIntOrNull() == null || uiState.websocketPort.isBlank()) {
                    { Text("必填字段", color = MaterialTheme.colorScheme.error) }
                } else {
                    { Text("WebSocket通信端口，用于实时数据同步") }
                }
            )

            // HTTP端口配置
            OutlinedTextField(
                value = uiState.httpPort,
                onValueChange = onHttpPortChange,
                label = { Text("HTTP端口 *") },
                placeholder = { Text("80") },
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.httpPort.toIntOrNull() == null || uiState.httpPort.isBlank(),
                supportingText = if (uiState.httpPort.toIntOrNull() == null || uiState.httpPort.isBlank()) {
                    { Text("必填字段", color = MaterialTheme.colorScheme.error) }
                } else {
                    { Text("HTTP端口，用于上传剪切板内容") }
                }
            )

            OutlinedTextField(
                value = uiState.deviceId,
                onValueChange = onDeviceIdChange,
                label = { Text("设备ID（可选）") },
                placeholder = { Text("留空自动生成") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("用于标识设备，留空将自动生成") }
            )
            
            // 认证配置
            Text(
                text = "认证配置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = uiState.authKey,
                onValueChange = onAuthKeyChange,
                label = { Text("请求头名称 (Header Key)") },
                placeholder = { Text("例如: Authorization") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("请求头的名称，如 Authorization、Token 等") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.authValue,
                onValueChange = onAuthValueChange,
                label = { Text("请求头值 (Header Value)") },
                placeholder = { Text("例如: Bearer your-token") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("请求头的值，对应的认证信息") }
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存")
                }

                OutlinedButton(
                    onClick = onTestConnection,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("测试连接")
                }
            }
        }
    }
}

@Composable
private fun SyncSettingsSection(
    config: com.clipboardsync.app.domain.model.AppConfig,
    onAutoSyncChange: (Boolean) -> Unit,
    onSyncImagesChange: (Boolean) -> Unit,
    onSyncFilesChange: (Boolean) -> Unit,
    onUseSecureConnectionChange: (Boolean) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "同步设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            SettingSwitch(
                title = "自动同步",
                description = "自动同步剪切板内容到服务器",
                checked = config.autoSync,
                onCheckedChange = onAutoSyncChange
            )
            
            SettingSwitch(
                title = "同步图片",
                description = "同步剪切板中的图片内容",
                checked = config.syncImages,
                onCheckedChange = onSyncImagesChange
            )
            
            SettingSwitch(
                title = "同步文件",
                description = "同步剪切板中的文件内容",
                checked = config.syncFiles,
                onCheckedChange = onSyncFilesChange
            )
            
            SettingSwitch(
                title = "使用安全连接",
                description = "使用HTTPS/WSS协议连接服务器",
                checked = config.useSecureConnection,
                onCheckedChange = onUseSecureConnectionChange
            )
        }
    }
}

@Composable
private fun AppSettingsSection(
    config: com.clipboardsync.app.domain.model.AppConfig,
    onEnableNotificationsChange: (Boolean) -> Unit,
    onAutoStartOnBootChange: (Boolean) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "应用设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            SettingSwitch(
                title = "启用通知",
                description = "显示同步状态通知",
                checked = config.enableNotifications,
                onCheckedChange = onEnableNotificationsChange
            )
            
            SettingSwitch(
                title = "开机自启动",
                description = "设备启动时自动启动同步服务",
                checked = config.autoStartOnBoot,
                onCheckedChange = onAutoStartOnBootChange
            )
        }
    }
}

@Composable
private fun DataManagementSection(
    onClearAllData: () -> Unit,
    isLoading: Boolean
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "数据管理",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Button(
                onClick = onClearAllData,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                } else {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("清除所有数据")
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
