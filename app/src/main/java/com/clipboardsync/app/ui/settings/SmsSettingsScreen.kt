package com.clipboardsync.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clipboardsync.app.util.SmsPermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SmsSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var showKeywordDialog by remember { mutableStateOf(false) }
    var showSenderDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            showPermissionDialog = true
        }
    }
    
    // 检查权限状态
    val hasPermissions = SmsPermissionHelper.hasSmsPermissions(context)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("短信验证码设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 权限状态卡片
            item {
                PermissionStatusCard(
                    hasPermissions = hasPermissions,
                    onRequestPermissions = {
                        val permissions = SmsPermissionHelper.getAllSmsPermissions()
                        permissionLauncher.launch(permissions)
                    }
                )
            }
            
            // 自动上传开关
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "自动上传短信验证码",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "收到验证码短信时自动上传到剪切板",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.autoUploadSms && hasPermissions,
                                onCheckedChange = { enabled ->
                                    if (enabled && !hasPermissions) {
                                        val permissions = SmsPermissionHelper.getAllSmsPermissions()
                                        permissionLauncher.launch(permissions)
                                    } else {
                                        viewModel.updateAutoUploadSms(enabled)
                                    }
                                },
                                enabled = hasPermissions
                            )
                        }
                    }
                }
            }
            
            // 发送方过滤
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "启用发送方过滤",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (uiState.smsFilterSender) "只处理来自信任发送方的短信" else "处理所有发送方的短信",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.smsFilterSender,
                                onCheckedChange = viewModel::updateSmsFilterSender,
                                enabled = uiState.autoUploadSms && hasPermissions
                            )
                        }

                        if (!uiState.smsFilterSender) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "已信任全部发送方",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            
            // 验证码关键词设置
            item {
                KeywordSettingsCard(
                    title = "验证码关键词",
                    description = "包含这些关键词的短信将被识别为验证码",
                    items = uiState.smsKeywords,
                    onAddItem = { showKeywordDialog = true },
                    onRemoveItem = viewModel::removeKeyword,
                    enabled = uiState.autoUploadSms && hasPermissions
                )
            }
            
            // 信任发送方设置
            if (uiState.smsFilterSender) {
                item {
                    KeywordSettingsCard(
                        title = "信任发送方",
                        description = "只处理来自这些发送方的短信",
                        items = uiState.trustedSenders,
                        onAddItem = { showSenderDialog = true },
                        onRemoveItem = viewModel::removeTrustedSender,
                        enabled = uiState.autoUploadSms && hasPermissions
                    )
                }
            }
            
            // 重置按钮
            item {
                OutlinedButton(
                    onClick = viewModel::resetToDefaults,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.autoUploadSms && hasPermissions
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("恢复默认设置")
                }
            }
        }
    }
    
    // 添加关键词对话框
    if (showKeywordDialog) {
        AddItemDialog(
            title = "添加关键词",
            placeholder = "输入验证码关键词",
            onConfirm = { keyword ->
                viewModel.addKeyword(keyword)
                showKeywordDialog = false
            },
            onDismiss = { showKeywordDialog = false }
        )
    }
    
    // 添加发送方对话框
    if (showSenderDialog) {
        AddItemDialog(
            title = "添加信任发送方",
            placeholder = "输入发送方号码或名称",
            onConfirm = { sender ->
                viewModel.addTrustedSender(sender)
                showSenderDialog = false
            },
            onDismiss = { showSenderDialog = false }
        )
    }
    
    // 权限被拒绝对话框
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("权限被拒绝") },
            text = { Text(SmsPermissionHelper.getPermissionDeniedExplanation()) },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
private fun PermissionStatusCard(
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hasPermissions) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasPermissions) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (hasPermissions) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasPermissions) "权限已授予" else "需要短信权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (hasPermissions) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (hasPermissions) {
                    "应用已获得短信权限，可以自动上传验证码"
                } else {
                    "需要短信权限才能自动上传验证码"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasPermissions) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
            
            if (!hasPermissions) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("申请权限")
                }
            }
        }
    }
}

@Composable
private fun KeywordSettingsCard(
    title: String,
    description: String,
    items: List<String>,
    onAddItem: () -> Unit,
    onRemoveItem: (String) -> Unit,
    enabled: Boolean
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onAddItem,
                    enabled = enabled
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
            }

            if (items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(
                            onClick = { onRemoveItem(item) },
                            enabled = enabled
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddItemDialog(
    title: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text.trim())
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
