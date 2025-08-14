package com.clipboardsync.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clipboardsync.app.util.PermissionUtils

@Composable
fun AdvancedPermissionDialog(
    permissionResult: PermissionUtils.PermissionCheckResult,
    onDismiss: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onOpenAutoStartSettings: () -> Unit,
    onOpenBasicPermissions: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "权限检查",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 基础权限状态
                PermissionStatusCard(
                    title = "基础权限",
                    isGranted = permissionResult.hasBasicPermissions,
                    description = if (permissionResult.hasBasicPermissions) {
                        "存储、通知等基础权限已授予"
                    } else {
                        "需要授予存储、通知等基础权限"
                    },
                    actionText = if (!permissionResult.hasBasicPermissions) "授予权限" else null,
                    onAction = if (!permissionResult.hasBasicPermissions) onOpenBasicPermissions else null
                )

                // 电池优化状态
                PermissionStatusCard(
                    title = "电池优化",
                    isGranted = !permissionResult.isBatteryOptimized,
                    description = if (permissionResult.isBatteryOptimized) {
                        "应用受到电池优化限制，可能影响后台运行"
                    } else {
                        "已忽略电池优化，后台运行正常"
                    },
                    actionText = if (permissionResult.isBatteryOptimized && permissionResult.canRequestBatteryOptimization) {
                        "关闭优化"
                    } else null,
                    onAction = if (permissionResult.isBatteryOptimized && permissionResult.canRequestBatteryOptimization) {
                        onRequestBatteryOptimization
                    } else null
                )

                // 自启动权限状态
                val autoStartText = when (permissionResult.autoStartStatus) {
                    PermissionUtils.AutoStartStatus.ENABLED -> "自启动权限已开启"
                    PermissionUtils.AutoStartStatus.DISABLED -> "自启动权限已关闭，可能影响开机自启"
                    PermissionUtils.AutoStartStatus.UNKNOWN -> "无法检测自启动权限状态"
                    PermissionUtils.AutoStartStatus.NO_PERMISSION -> "无权限检测自启动状态"
                }

                PermissionStatusCard(
                    title = "自启动权限",
                    isGranted = permissionResult.autoStartStatus == PermissionUtils.AutoStartStatus.ENABLED,
                    description = autoStartText,
                    actionText = if (permissionResult.canOpenAutoStartSettings && 
                        permissionResult.autoStartStatus != PermissionUtils.AutoStartStatus.ENABLED) {
                        "打开设置"
                    } else null,
                    onAction = if (permissionResult.canOpenAutoStartSettings && 
                        permissionResult.autoStartStatus != PermissionUtils.AutoStartStatus.ENABLED) {
                        onOpenAutoStartSettings
                    } else null
                )

                // 设备信息
                DeviceInfoCard()

                // 建议
                if (permissionResult.recommendations.isNotEmpty()) {
                    RecommendationsCard(permissionResult.recommendations)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了")
            }
        }
    )
}

@Composable
private fun PermissionStatusCard(
    title: String,
    isGranted: Boolean,
    description: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (actionText != null && onAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onAction) {
                        Text(actionText)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "设备信息",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = "品牌: ${android.os.Build.BRAND} ${android.os.Build.MODEL}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecommendationsCard(recommendations: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "建议",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            
            recommendations.forEach { recommendation ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = recommendation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
