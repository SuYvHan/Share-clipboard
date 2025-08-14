package com.clipboardsync.app.service.clipboard

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.clipboardsync.app.R
import com.clipboardsync.app.domain.model.AppConfig
import com.clipboardsync.app.domain.model.ClipboardItem
import com.clipboardsync.app.domain.model.ClipboardType
import com.clipboardsync.app.domain.repository.ClipboardRepository
import com.clipboardsync.app.domain.repository.ConfigRepository
import com.clipboardsync.app.network.http.ClipboardHttpService
import com.clipboardsync.app.network.websocket.WebSocketClient
import com.clipboardsync.app.ui.main.MainActivity
import com.clipboardsync.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardSyncService : Service() {
    
    @Inject
    lateinit var clipboardRepository: ClipboardRepository

    @Inject
    lateinit var configRepository: ConfigRepository

    @Inject
    lateinit var webSocketClient: WebSocketClient

    @Inject
    lateinit var clipboardHttpService: ClipboardHttpService
    
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var notificationHelper: NotificationHelper
    private var lastClipboardContent: String? = null
    private var isProcessingClipboard = false
    private var lastSyncedContent: String? = null // 记录最后一次同步设置的内容
    private var syncSetTimestamp: Long = 0 // 记录同步设置的时间戳
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastForegroundCheck: Long = 0 // 记录最后一次前台检查时间
    private var pendingClipboardCheck = false // 是否有待处理的剪切板检查
    
    private val tag = "ClipboardSyncService"
    
    companion object {
        const val ACTION_START_SERVICE = "START_SERVICE"
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
        const val ACTION_APP_IN_FOREGROUND = "APP_IN_FOREGROUND"
        
        fun startService(context: Context) {
            val intent = Intent(context, ClipboardSyncService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, ClipboardSyncService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created")

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        notificationHelper = NotificationHelper(this)

        // 异步初始化通知，支持重试
        serviceScope.launch {
            val success = notificationHelper.createNotificationChannelWithRetry()
            if (success) {
                val notification = notificationHelper.createForegroundNotification("剪切板同步服务运行中")
                if (notification != null) {
                    try {
                        startForeground(notificationHelper.getNotificationId(), notification)
                        Log.i(tag, "Foreground service started successfully")
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to start foreground service", e)
                    }
                } else {
                    Log.w(tag, "Failed to create notification, service will run without foreground notification")
                }
            } else {
                Log.w(tag, "Notification channel creation failed, service will run without foreground notification")
            }
        }

        setupClipboardListener()
        connectWebSocket()
        startForegroundMonitoring()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                Log.d(tag, "Service start command received")
            }
            ACTION_STOP_SERVICE -> {
                Log.d(tag, "Service stop command received")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_APP_IN_FOREGROUND -> {
                Log.d(tag, "App in foreground notification received")
                handleAppInForeground()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Service destroyed")
        webSocketClient.disconnect()
        serviceScope.cancel()
    }
    
    private fun updateNotification(content: String) {
        if (::notificationHelper.isInitialized && notificationHelper.isNotificationEnabled()) {
            notificationHelper.updateNotification(content)
        }
    }

    /**
     * 检查应用是否在前台（Android 12+需要）
     */
    private fun isAppInForeground(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningAppProcesses = activityManager.runningAppProcesses

            val isInForeground = runningAppProcesses?.any { processInfo ->
                processInfo.processName == packageName &&
                processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            } ?: false

            Log.d(tag, "前台状态检查: $isInForeground")
            return isInForeground
        } catch (e: Exception) {
            Log.w(tag, "Error checking foreground status: ${e.message}")
            // 如果检查失败，返回false以避免剪切板访问被拒绝
            return false
        }
    }

    /**
     * 检查是否可以访问剪切板（综合检查）
     */
    private fun canAccessClipboard(): Boolean {
        // Android 12+ 需要前台检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val inForeground = isAppInForeground()
            if (!inForeground) {
                Log.d(tag, "应用不在前台，无法访问剪切板 (Android 12+)")
                return false
            }
        }

        // 尝试简单的剪切板访问测试
        return try {
            val testClip = clipboardManager.primaryClip
            Log.d(tag, "剪切板访问测试成功")
            true
        } catch (e: SecurityException) {
            Log.w(tag, "剪切板访问被拒绝: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(tag, "剪切板访问异常: ${e.message}")
            false
        }
    }
    
    private fun setupClipboardListener() {
        clipboardManager.addPrimaryClipChangedListener {
            serviceScope.launch {
                handleClipboardChange()
            }
        }
    }

    /**
     * 启动前台状态监控（Android 12+）
     */
    private fun startForegroundMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            serviceScope.launch {
                var wasPreviouslyForeground = false

                while (true) {
                    try {
                        val isNowForeground = isAppInForeground()

                        // 检测从后台切换到前台的状态变化
                        if (!wasPreviouslyForeground && isNowForeground) {
                            Log.d(tag, "检测到应用从后台切换到前台")

                            // 延迟一小段时间确保应用完全进入前台
                            kotlinx.coroutines.delay(1000)

                            // 执行前台剪切板检查
                            if (pendingClipboardCheck) {
                                Log.d(tag, "执行待处理的剪切板检查")
                                pendingClipboardCheck = false
                                handleClipboardChange()
                            } else {
                                Log.d(tag, "主动检查当前剪切板内容")
                                checkAndUploadCurrentClipboard()
                            }
                        }

                        wasPreviouslyForeground = isNowForeground
                        lastForegroundCheck = System.currentTimeMillis()

                        // 每3秒检查一次前台状态（更频繁的检查）
                        kotlinx.coroutines.delay(3000)
                    } catch (e: Exception) {
                        Log.e(tag, "前台监控异常", e)
                        kotlinx.coroutines.delay(5000) // 出错时延长检查间隔
                    }
                }
            }
        }
    }

    /**
     * 处理应用进入前台的通知
     */
    private fun handleAppInForeground() {
        serviceScope.launch {
            try {
                Log.d(tag, "应用进入前台，开始检查剪切板状态")

                // 1. 如果有待处理的剪切板检查，立即执行
                if (pendingClipboardCheck) {
                    Log.d(tag, "执行待处理的剪切板检查")
                    pendingClipboardCheck = false
                    handleClipboardChange()
                } else {
                    // 2. 即使没有待处理的检查，也主动检查当前剪切板内容
                    Log.d(tag, "主动检查当前剪切板内容")
                    checkAndUploadCurrentClipboard()
                }

                // 更新通知状态
                updateNotification("剪切板同步服务运行中")
            } catch (e: Exception) {
                Log.e(tag, "处理应用前台通知时出错", e)
            }
        }
    }

    /**
     * 检查并上传当前剪切板内容（应用回到前台时调用）
     */
    private suspend fun checkAndUploadCurrentClipboard() {
        try {
            val config = configRepository.getConfig().first()
            if (!config.autoSync) {
                Log.d(tag, "自动同步已关闭，跳过前台剪切板检查")
                return
            }

            // 检查是否可以访问剪切板
            if (!canAccessClipboard()) {
                Log.w(tag, "前台状态下仍无法访问剪切板")
                return
            }

            val clipData = try {
                clipboardManager.primaryClip
            } catch (e: Exception) {
                Log.e(tag, "获取剪切板内容失败: ${e.message}")
                return
            }

            if (clipData == null || clipData.itemCount == 0) {
                Log.d(tag, "剪切板为空，无需处理")
                return
            }

            val item = clipData.getItemAt(0)
            val currentContent = when {
                item.text != null -> item.text.toString()
                item.uri != null -> item.uri.toString()
                else -> {
                    Log.d(tag, "剪切板内容类型不支持")
                    return
                }
            }

            // 检查是否与上次处理的内容相同
            if (currentContent == lastClipboardContent) {
                Log.d(tag, "剪切板内容未变化，无需重复上传: ${currentContent.take(50)}...")
                return
            }

            // 检查是否是刚刚同步设置的内容（防止循环）
            val currentTime = System.currentTimeMillis()
            if (currentContent == lastSyncedContent &&
                (currentTime - syncSetTimestamp) < 5000) { // 5秒内的同步内容
                Log.d(tag, "跳过刚刚同步设置的内容: ${currentContent.take(50)}...")
                return
            }

            // 检查是否是短信调试信息，跳过上传
            if (currentContent.contains("处理短信 - 发送方:") ||
                currentContent.contains("📱 短信验证码")) {
                Log.d(tag, "跳过短信调试信息，不上传: ${currentContent.take(50)}...")
                return
            }

            Log.d(tag, "发现新的剪切板内容，准备上传: ${currentContent.take(50)}...")

            // 更新记录
            lastClipboardContent = currentContent

            // 创建剪切板项目并上传
            val clipboardItem = when {
                item.text != null -> createTextClipboardItem(currentContent, config)
                item.uri != null -> createImageClipboardItem(item.uri, config)
                else -> return
            }

            clipboardItem?.let { validItem ->
                var syncSuccess = false

                // 通过WebSocket同步到服务器
                if (webSocketClient.isConnected()) {
                    webSocketClient.syncClipboardItem(validItem)
                    syncSuccess = true
                    Log.d(tag, "前台检查：通过WebSocket发送剪切板内容: ${validItem.type}")
                }

                // 同时通过HTTP上传到服务器
                uploadToHttpServer(validItem, config)

                // 显示同步信息
                if (syncSuccess) {
                    updateNotification("已同步: ${getContentPreview(validItem)}")
                } else {
                    updateNotification("同步中: ${getContentPreview(validItem)}")
                }

                Log.d(tag, "前台检查：成功处理剪切板内容: ${validItem.type}")
            }

        } catch (e: Exception) {
            Log.e(tag, "前台剪切板检查失败", e)
        }
    }

    private suspend fun handleClipboardChange() {
        try {
            // 防止重复处理
            if (isProcessingClipboard) return
            isProcessingClipboard = true

            // 清理过期的同步记录
            cleanupExpiredSyncRecords()

            val config = configRepository.getConfig().first()
            if (!config.autoSync) {
                Log.d(tag, "自动同步已关闭，跳过剪切板处理")
                return
            }

            // 检查是否可以访问剪切板
            if (!canAccessClipboard()) {
                Log.w(tag, "无法访问剪切板，标记为待处理 (Android 12+ 后台限制)")
                // 标记有待处理的剪切板检查
                pendingClipboardCheck = true
                // 显示提示信息
                updateNotification("剪切板访问受限 - 请打开应用")
                return
            }

            val clipData = try {
                clipboardManager.primaryClip
            } catch (e: SecurityException) {
                Log.w(tag, "剪切板访问被拒绝 (Android 12+): ${e.message}")
                updateNotification("剪切板访问被拒绝 - 请打开应用")
                return
            } catch (e: Exception) {
                Log.e(tag, "剪切板访问错误: ${e.message}")
                updateNotification("剪切板访问错误")
                return
            }

            if (clipData == null || clipData.itemCount == 0) return

            val item = clipData.getItemAt(0)
            val currentContent = when {
                item.text != null -> item.text.toString()
                item.uri != null -> item.uri.toString()
                else -> return
            }

            // 避免重复处理相同内容
            if (currentContent == lastClipboardContent) {
                Log.d(tag, "跳过重复内容: ${currentContent.take(50)}...")
                return
            }

            // 检查是否是刚刚同步设置的内容（防止循环）
            val currentTime = System.currentTimeMillis()
            if (currentContent == lastSyncedContent &&
                (currentTime - syncSetTimestamp) < 3000) { // 3秒内的同步内容
                Log.d(tag, "跳过刚刚同步设置的内容，避免循环: ${currentContent.take(50)}...")
                lastClipboardContent = currentContent // 更新记录但不处理
                return
            }

            lastClipboardContent = currentContent
            
            val clipboardItem = when {
                item.text != null -> createTextClipboardItem(currentContent, config)
                item.uri != null -> createImageClipboardItem(item.uri, config)
                else -> return
            }

            clipboardItem?.let { validItem ->
                // 复制的内容直接发送，不保存到本地数据库
                var syncSuccess = false

                // 通过WebSocket同步到服务器
                if (webSocketClient.isConnected()) {
                    webSocketClient.syncClipboardItem(validItem)
                    syncSuccess = true
                    Log.d(tag, "Local clipboard sent via WebSocket: ${validItem.type}")
                }

                // 同时通过HTTP上传到服务器
                uploadToHttpServer(validItem, config)

                // 只显示同步信息，不保存为剪切板块
                if (syncSuccess) {
                    updateNotification("已发送: ${getContentPreview(validItem)}")
                } else {
                    updateNotification("发送中: ${getContentPreview(validItem)}")
                }

                Log.d(tag, "Local clipboard content sent (not saved): ${validItem.type}")
            }
            
        } catch (e: Exception) {
            Log.e(tag, "Error handling clipboard change", e)
        } finally {
            isProcessingClipboard = false
        }
    }

    private fun setTextToClipboard(text: String) {
        try {
            Log.d(tag, "开始设置同步文本到剪切板: ${text.take(50)}...")

            // 记录即将设置的同步内容和时间戳
            lastSyncedContent = text
            syncSetTimestamp = System.currentTimeMillis()

            // 临时禁用剪切板监听，避免触发自己的处理逻辑
            isProcessingClipboard = true

            val clipData = ClipData.newPlainText("Synced Text", text)
            clipboardManager.setPrimaryClip(clipData)

            // 更新最后的剪切板内容记录
            lastClipboardContent = text

            Log.d(tag, "成功设置同步文本到剪切板: ${text.take(50)}...")
        } catch (e: Exception) {
            Log.e(tag, "设置同步文本到剪切板失败", e)
        } finally {
            // 延迟重新启用剪切板监听，避免立即触发
            serviceScope.launch {
                kotlinx.coroutines.delay(2000) // 等待2秒，给足够时间避免循环
                isProcessingClipboard = false
                Log.d(tag, "重新启用剪切板监听，同步内容: ${lastSyncedContent?.take(50)}")
            }
        }
    }

    private fun uploadToHttpServer(clipboardItem: ClipboardItem, config: AppConfig) {
        serviceScope.launch {
            try {
                Log.d(tag, "Uploading clipboard item to HTTP server: ${config.httpUrl}")
                val result = clipboardHttpService.uploadClipboardContent(config, clipboardItem)
                result.fold(
                    onSuccess = { response ->
                        Log.i(tag, "HTTP upload successful: $response")
                    },
                    onFailure = { error ->
                        Log.w(tag, "HTTP upload failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "HTTP upload error", e)
            }
        }
    }

    private fun createTextClipboardItem(content: String, config: AppConfig): ClipboardItem {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())
        return ClipboardItem(
            id = UUID.randomUUID().toString(),
            type = ClipboardType.text,
            content = content,
            deviceId = config.deviceId,
            createdAt = now,
            updatedAt = now
        )
    }
    
    private suspend fun createImageClipboardItem(uri: Uri, config: AppConfig): ClipboardItem? {
        return try {
            Log.d(tag, "Attempting to create image clipboard item from URI: $uri")

            val bitmap = loadBitmapFromUri(uri)
            if (bitmap == null) {
                Log.w(tag, "Failed to load bitmap, creating text fallback")
                return createTextFallbackForImage(uri, config)
            }

            val base64 = bitmapToBase64(bitmap)
            if (base64.isEmpty()) {
                Log.w(tag, "Failed to convert bitmap to base64, creating text fallback")
                bitmap.recycle() // 释放内存
                return createTextFallbackForImage(uri, config)
            }

            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())

            val imageItem = ClipboardItem(
                id = UUID.randomUUID().toString(),
                type = ClipboardType.image,
                content = base64,
                deviceId = config.deviceId,
                mimeType = "image/jpeg", // 改为JPEG，因为我们使用JPEG压缩
                createdAt = now,
                updatedAt = now
            )

            Log.d(tag, "Successfully created image clipboard item")
            imageItem

        } catch (e: OutOfMemoryError) {
            Log.e(tag, "Out of memory creating image clipboard item, using text fallback", e)
            createTextFallbackForImage(uri, config)
        } catch (e: SecurityException) {
            Log.e(tag, "Security exception creating image clipboard item (Android 12+), using text fallback", e)
            createTextFallbackForImage(uri, config)
        } catch (e: Exception) {
            Log.e(tag, "Error creating image clipboard item, using text fallback", e)
            createTextFallbackForImage(uri, config)
        }
    }

    /**
     * 创建图片的文本兜底方案
     */
    private fun createTextFallbackForImage(uri: Uri, config: AppConfig): ClipboardItem? {
        return try {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())
            val fallbackText = "📷 图片文件 (Android 12兼容模式)\n" +
                    "URI: $uri\n" +
                    "时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n" +
                    "注意: 由于系统限制，图片以文本形式同步"

            Log.d(tag, "Created text fallback for image: $fallbackText")

            ClipboardItem(
                id = UUID.randomUUID().toString(),
                type = ClipboardType.text,
                content = fallbackText,
                deviceId = config.deviceId,
                mimeType = "text/plain",
                createdAt = now,
                updatedAt = now
            )
        } catch (e: Exception) {
            Log.e(tag, "Error creating text fallback for image", e)
            null
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            Log.d(tag, "Loading bitmap from URI: $uri")

            // Android 12+ 需要特殊处理URI访问
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 检查URI权限
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    Log.w(tag, "Cannot take persistable URI permission (Android 12+): ${e.message}")
                    // 继续尝试读取，可能是临时权限
                } catch (e: UnsupportedOperationException) {
                    Log.w(tag, "URI does not support persistable permissions: ${e.message}")
                    // 某些URI不支持持久权限，继续尝试
                }
            }

            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(tag, "Cannot open input stream for URI: $uri")
                return null
            }

            // 使用BitmapFactory.Options进行内存优化
            val options = android.graphics.BitmapFactory.Options().apply {
                // 首先只获取图片尺寸
                inJustDecodeBounds = true
            }

            android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Android 12+ 内存限制：如果图片太大，进行采样
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val maxSize = 2048
                if (options.outWidth > maxSize || options.outHeight > maxSize) {
                    options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
                    Log.d(tag, "Using sample size: ${options.inSampleSize} for large image")
                }
            }

            // 实际解码图片
            options.inJustDecodeBounds = false
            val newInputStream = contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(newInputStream, null, options)
            newInputStream?.close()

            if (bitmap != null) {
                Log.d(tag, "Successfully loaded bitmap: ${bitmap.width}x${bitmap.height}")
            } else {
                Log.w(tag, "Failed to decode bitmap from URI")
            }

            bitmap
        } catch (e: SecurityException) {
            Log.e(tag, "Security exception loading bitmap from URI (Android 12+): ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            Log.e(tag, "Out of memory loading bitmap from URI: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(tag, "Error loading bitmap from URI: ${e.message}")
            null
        }
    }

    /**
     * 计算图片采样大小以减少内存使用
     */
    private fun calculateInSampleSize(
        options: android.graphics.BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        return try {
            Log.d(tag, "Converting bitmap to base64: ${bitmap.width}x${bitmap.height}")

            // Android 12+ 内存优化：限制图片大小
            val processedBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 如果图片太大，进行压缩
                val maxSize = 1024 // 减小最大尺寸以避免内存问题
                if (bitmap.width > maxSize || bitmap.height > maxSize) {
                    val scale = minOf(
                        maxSize.toFloat() / bitmap.width,
                        maxSize.toFloat() / bitmap.height
                    )
                    val newWidth = (bitmap.width * scale).toInt()
                    val newHeight = (bitmap.height * scale).toInt()

                    Log.d(tag, "Resizing bitmap from ${bitmap.width}x${bitmap.height} to ${newWidth}x${newHeight}")

                    try {
                        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                    } catch (e: OutOfMemoryError) {
                        Log.e(tag, "Out of memory resizing bitmap, using original")
                        bitmap
                    }
                } else {
                    bitmap
                }
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            // 使用JPEG格式和较低质量以减少内存使用
            val quality = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 60 else 80 // Android 12+使用更低质量
            val success = processedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

            if (!success) {
                Log.e(tag, "Failed to compress bitmap")
                return ""
            }

            val byteArray = outputStream.toByteArray()
            outputStream.close()

            // 如果创建了新的bitmap，释放内存
            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }

            // Android 12+ 检查最终大小
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && byteArray.size > 5 * 1024 * 1024) { // 5MB限制
                Log.w(tag, "Image too large after compression: ${byteArray.size} bytes, skipping")
                return ""
            }

            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            Log.d(tag, "Base64 conversion completed, size: ${byteArray.size} bytes")

            base64
        } catch (e: OutOfMemoryError) {
            Log.e(tag, "Out of memory converting bitmap to base64", e)
            ""
        } catch (e: Exception) {
            Log.e(tag, "Error converting bitmap to base64", e)
            ""
        }
    }
    
    private fun getContentPreview(item: ClipboardItem): String {
        return when (item.type) {
            ClipboardType.text -> item.content.take(20) + if (item.content.length > 20) "..." else ""
            ClipboardType.image -> "图片"
            ClipboardType.file -> item.fileName ?: "文件"
        }
    }
    
    private fun connectWebSocket() {
        // 只在未连接且未连接中时才连接WebSocket
        if (!webSocketClient.isConnectedOrConnecting()) {
            serviceScope.launch {
                try {
                    val config = configRepository.getConfig().first()
                    Log.d(tag, "Service connecting to WebSocket with config: ${config.websocketUrl}")
                    webSocketClient.connect(config)
                } catch (e: Exception) {
                    Log.e(tag, "Error connecting WebSocket", e)
                }
            }
        } else {
            Log.d(tag, "WebSocket already connected or connecting, skipping service connection")
        }

        // 在独立协程中监听WebSocket消息
        serviceScope.launch {
            try {
                webSocketClient.messageFlow.collect { message ->
                    handleWebSocketMessage(message)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error handling WebSocket messages", e)
            }
        }

        // 在独立协程中监听连接状态
        serviceScope.launch {
            try {
                webSocketClient.connectionStateFlow.collect { state ->
                    handleConnectionStateChange(state)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error handling connection state", e)
            }
        }
    }

    fun reconnectWebSocket() {
        serviceScope.launch {
            try {
                val config = configRepository.getConfig().first()
                Log.d(tag, "Service reconnecting WebSocket with new config")
                webSocketClient.reconnect(config)
            } catch (e: Exception) {
                Log.e(tag, "Error reconnecting WebSocket in service", e)
            }
        }
    }

    private fun handleConnectionStateChange(state: WebSocketClient.ConnectionState) {
        Log.d(tag, "WebSocket connection state changed: $state")
        when (state) {
            is WebSocketClient.ConnectionState.Connected -> {
                Log.i(tag, "WebSocket connected successfully")
                updateNotification("已连接到服务器")
            }

            is WebSocketClient.ConnectionState.Disconnected -> {
                Log.w(tag, "WebSocket disconnected")
                updateNotification("与服务器断开连接")
            }
            is WebSocketClient.ConnectionState.Error -> {
                Log.e(tag, "WebSocket error: ${state.message}")
                updateNotification("连接错误: ${state.message}")
            }
            is WebSocketClient.ConnectionState.Reconnecting -> {
                Log.i(tag, "WebSocket reconnecting, attempt: ${state.attempt}")
                updateNotification("重连中 (${state.attempt})")
            }
            is WebSocketClient.ConnectionState.Failed -> {
                Log.e(tag, "WebSocket failed: ${state.message}")
                updateNotification("连接失败: ${state.message}")
            }
        }
    }

    private suspend fun handleWebSocketMessage(message: com.clipboardsync.app.domain.model.WebSocketMessage) {
        when (message.type) {
            "sync" -> {
                message.data?.let { item ->
                    // 避免同步自己发送的内容
                    val config = configRepository.getConfig().first()
                    if (item.deviceId != config.deviceId) {
                        Log.d(tag, "收到来自设备的同步: ${item.deviceId}, 类型: ${item.type}, 内容: ${item.content.take(50)}...")

                        // 检查是否是刚刚发送的内容（双重保险）
                        if (item.content == lastClipboardContent &&
                            (System.currentTimeMillis() - syncSetTimestamp) < 5000) {
                            Log.d(tag, "跳过可能的循环同步内容: ${item.content.take(50)}...")
                            return@let
                        }

                        // 如果是文本类型，直接设置到系统剪切板
                        if (item.type == com.clipboardsync.app.domain.model.ClipboardType.text) {
                            Log.d(tag, "设置文本内容到系统剪切板: ${item.content.take(50)}...")
                            setTextToClipboard(item.content)
                            updateNotification("已同步文本到剪切板: ${getContentPreview(item)}")
                        } else {
                            // 非文本内容只保存，不设置到剪切板
                            if (item.type == com.clipboardsync.app.domain.model.ClipboardType.image) {
                                Log.d(tag, "图片内容长度: ${item.content.length}")
                                Log.d(tag, "图片内容前缀: ${item.content.take(100)}")
                            }
                            updateNotification("收到同步: ${getContentPreview(item)}")
                        }

                        clipboardRepository.insertItem(item, isSynced = true)
                        Log.d(tag, "已接收并保存来自设备的同步: ${item.deviceId}")
                    } else {
                        Log.d(tag, "忽略来自自己设备的同步: ${item.deviceId}")
                    }
                }
            }
            "delete" -> {
                message.id?.let { id ->
                    clipboardRepository.deleteItem(id)
                    Log.d(tag, "Deleted item: $id")
                }
            }
            "all_content" -> {
                // 处理获取到的所有内容
                val count = message.count ?: 0
                updateNotification("已同步 $count 条记录")
                Log.d(tag, "Synced $count items")
            }
        }
    }

    /**
     * 清理过期的同步记录，防止内存泄漏
     */
    private fun cleanupExpiredSyncRecords() {
        val currentTime = System.currentTimeMillis()
        // 如果同步记录超过10分钟，清理掉
        if (lastSyncedContent != null && (currentTime - syncSetTimestamp) > 600000) {
            Log.d(tag, "清理过期的同步记录")
            lastSyncedContent = null
            syncSetTimestamp = 0
        }
    }
}
