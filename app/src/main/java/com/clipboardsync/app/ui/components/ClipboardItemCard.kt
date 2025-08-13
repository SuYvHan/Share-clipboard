package com.clipboardsync.app.ui.components

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.clipboardsync.app.domain.model.ClipboardItem
import com.clipboardsync.app.domain.model.ClipboardType
import com.clipboardsync.app.util.ImageUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardItemCard(
    item: ClipboardItem,
    onCopy: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSaveImage: (ClipboardItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // 使用remember优化性能，避免不必要的重组
    var showMenu by remember { mutableStateOf(false) }

    // 预计算格式化时间，避免每次重组都计算
    val formattedTime = remember(item.createdAt) {
        try {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(item.createdAt)
        } catch (e: Exception) {
            "未知时间"
        }
    }

    // 预计算内容预览，避免每次重组都截取
    val contentPreview = remember(item.content) {
        when {
            item.content.length > 100 -> item.content.take(100) + "..."
            else -> item.content
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 头部信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (item.type) {
                            ClipboardType.text -> Icons.Default.Description
                            ClipboardType.image -> Icons.Default.Image
                            ClipboardType.file -> Icons.Default.Attachment
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (item.type) {
                            ClipboardType.text -> "文本"
                            ClipboardType.image -> "图片"
                            ClipboardType.file -> "文件"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("复制") },
                            onClick = {
                                onCopy(item.content)
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                            }
                        )
                        
                        if (item.type == ClipboardType.image) {
                            DropdownMenuItem(
                                text = { Text("保存到相册") },
                                onClick = {
                                    onSaveImage(item)
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                }
                            )
                        }
                        
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                onDelete(item.id)
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 内容显示
            when (item.type) {
                ClipboardType.text -> {
                    Text(
                        text = contentPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                ClipboardType.image -> {
                    ImageContent(item = item)
                }
                
                ClipboardType.file -> {
                    FileContent(item = item)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 底部信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (item.deviceId.isNotEmpty()) {
                    Text(
                        text = "来自: ${item.deviceId.take(8)}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageContent(item: ClipboardItem) {
    // 使用item.id和content作为key，确保状态正确管理
    var bitmap by remember(item.id, item.content) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(item.id, item.content) { mutableStateOf(true) }
    var errorMessage by remember(item.id, item.content) { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id, item.content) {
        Log.d("ClipboardItemCard", "Loading image for item: ${item.id}, content: ${item.content}")

        try {
            // 检查是否是图片类型且有fileName（来自WebSocket同步）
            if (item.type == com.clipboardsync.app.domain.model.ClipboardType.image &&
                !item.fileName.isNullOrEmpty()) {
                Log.d("ClipboardItemCard", "Detected synced image, using direct URL display...")
                Log.d("ClipboardItemCard", "Item ID: ${item.id}, FileName: ${item.fileName}, Content: ${item.content}")

                // 获取用户实际配置（包括端口和协议设置）
                val config = com.clipboardsync.app.ClipboardSyncApplication.getCurrentConfig()
                Log.d("ClipboardItemCard", "Using config - httpUrl: ${config.httpUrl}, authKey: ${config.authKey}")

                // 根据API.MD文档：id=响应的data.id值，name=响应的data.fileName值
                val encodedFileName = java.net.URLEncoder.encode(item.fileName, "UTF-8")
                val imageUrl = "${config.httpUrl}/api/files/preview?id=${item.id}&name=$encodedFileName"
                Log.d("ClipboardItemCard", "Direct image URL: $imageUrl")

                // 设置为特殊标记，表示使用URL显示
                errorMessage = "USE_URL:$imageUrl"
                isLoading = false
            } else if (item.content.startsWith("file_")) {
                // 兼容旧格式：文件ID格式
                Log.d("ClipboardItemCard", "Detected file ID format, using direct URL display...")
                Log.d("ClipboardItemCard", "Item ID: ${item.id}, Content: ${item.content}")

                val config = com.clipboardsync.app.ClipboardSyncApplication.getCurrentConfig()
                val fileName = item.fileName ?: item.filePath ?: item.content
                val encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8")
                val imageUrl = "${config.httpUrl}/api/files/preview?id=${item.id}&name=$encodedFileName"
                Log.d("ClipboardItemCard", "Direct image URL (legacy): $imageUrl")

                errorMessage = "USE_URL:$imageUrl"
                isLoading = false
            } else if (item.content.startsWith("data:")) {
                // Base64格式
                Log.d("ClipboardItemCard", "Detected Base64 format, decoding...")
                bitmap = ImageUtils.base64ToBitmap(item.content)
                if (bitmap == null) {
                    errorMessage = buildString {
                        append("🚫 Base64解码失败\n\n")
                        append("📋 内容信息:\n")
                        append("• 内容长度: ${item.content.length}\n")
                        append("• 内容前缀: ${item.content.take(100)}...\n")
                        append("• 是否包含data URL: ${item.content.startsWith("data:")}\n\n")
                        append("❌ 可能原因:\n")
                        append("• Base64格式不正确\n")
                        append("• 图片数据损坏\n")
                        append("• 不支持的图片格式")
                    }
                }
                isLoading = false
            } else {
                // 直接的图片数据
                Log.d("ClipboardItemCard", "Treating as raw image data...")
                val imageBytes = item.content.toByteArray(Charsets.ISO_8859_1)
                bitmap = ImageUtils.bytesToBitmap(imageBytes)
                if (bitmap == null) {
                    errorMessage = buildString {
                        append("🚫 原始数据解码失败\n\n")
                        append("📋 数据信息:\n")
                        append("• 原始长度: ${item.content.length}\n")
                        append("• 字节长度: ${imageBytes.size}\n")
                        append("• 前16字节: ${imageBytes.take(16).joinToString(" ") { "%02X".format(it) }}\n\n")
                        append("❌ 可能原因:\n")
                        append("• 不是有效的图片数据\n")
                        append("• 字符编码问题\n")
                        append("• 数据格式不支持")
                    }
                }
                isLoading = false
            }
        } catch (e: Exception) {
            Log.e("ClipboardItemCard", "Error loading image", e)
            errorMessage = "图片加载错误: ${e.message}"
            isLoading = false
        }
    }

    when {
        isLoading -> {
            // 显示加载状态
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "加载图片中...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        bitmap != null -> {
            // 显示图片
            Log.d("ClipboardItemCard", "Displaying bitmap: ${bitmap!!.width}x${bitmap!!.height}")
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "剪切板图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }

        errorMessage?.startsWith("USE_URL:") == true -> {
            // 使用URL直接显示图片
            val imageUrl = errorMessage!!.removePrefix("USE_URL:")
            Log.d("ClipboardItemCard", "Displaying image from URL: $imageUrl")

            // 使用AsyncImage直接显示图片URL
            val config = com.clipboardsync.app.ClipboardSyncApplication.getCurrentConfig()

            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .addHeader(config.authKey, config.authValue)
                    .crossfade(true)
                    .build(),
                contentDescription = "剪切板图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }

        else -> {
            // 显示详细错误状态
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // 错误标题
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "图片加载失败",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 可滚动的错误详情
                    Text(
                        text = errorMessage ?: "未知错误",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

@Composable
private fun FileContent(item: ClipboardItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Attachment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = item.fileName ?: "未知文件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (item.fileSize != null) {
                    Text(
                        text = formatFileSize(item.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

private fun formatDateTime(dateTimeString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val date = inputFormat.parse(dateTimeString)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        dateTimeString
    }
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1 -> String.format("%.1f GB", gb)
        mb >= 1 -> String.format("%.1f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
