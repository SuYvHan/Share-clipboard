package com.clipboardsync.app.ui.components

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var showMenu by remember { mutableStateOf(false) }
    
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
                        text = item.content,
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
                    text = formatDateTime(item.createdAt),
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
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.content) {
        Log.d("ClipboardItemCard", "Loading image for item: ${item.id}, content: ${item.content}")

        try {
            // 检查是否是文件ID格式（以file_开头）
            if (item.content.startsWith("file_")) {
                Log.d("ClipboardItemCard", "Detected file ID format, fetching from preview API...")
                Log.d("ClipboardItemCard", "Item ID: ${item.id}, Content: ${item.content}")

                // 使用文件预览接口获取图片数据
                val filePreviewService = com.clipboardsync.app.network.http.FilePreviewService(
                    com.clipboardsync.app.ClipboardSyncApplication.httpClient
                )
                val config = com.clipboardsync.app.ClipboardSyncApplication.appConfig

                // 使用item.id作为id参数，item.content作为name参数（因为content包含filePath）
                val result = filePreviewService.getImagePreview(config, item.id, item.content)
                result.fold(
                    onSuccess = { imageBytes ->
                        Log.d("ClipboardItemCard", "Successfully fetched image data, size: ${imageBytes.size}")
                        bitmap = ImageUtils.bytesToBitmap(imageBytes)
                        isLoading = false
                    },
                    onFailure = { error ->
                        Log.e("ClipboardItemCard", "Failed to fetch image preview: ${error.message}")
                        errorMessage = "加载图片失败: ${error.message}"
                        isLoading = false
                    }
                )
            } else if (item.content.startsWith("data:")) {
                // Base64格式
                Log.d("ClipboardItemCard", "Detected Base64 format, decoding...")
                bitmap = ImageUtils.base64ToBitmap(item.content)
                isLoading = false
            } else {
                // 直接的图片数据
                Log.d("ClipboardItemCard", "Treating as raw image data...")
                val imageBytes = item.content.toByteArray(Charsets.ISO_8859_1)
                bitmap = ImageUtils.bytesToBitmap(imageBytes)
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

        else -> {
            // 显示错误状态
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = errorMessage ?: "图片加载失败",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "内容: ${item.content.take(50)}...",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
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
