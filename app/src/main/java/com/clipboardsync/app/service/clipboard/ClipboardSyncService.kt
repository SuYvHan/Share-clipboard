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
    private var lastClipboardContent: String? = null
    private var isProcessingClipboard = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val tag = "ClipboardSyncService"
    private val notificationId = 1001
    private val channelId = "clipboard_sync_channel"
    
    companion object {
        const val ACTION_START_SERVICE = "START_SERVICE"
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
        
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
        createNotificationChannel()
        startForeground(notificationId, createNotification("剪切板同步服务运行中"))
        
        setupClipboardListener()
        connectWebSocket()
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
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "剪切板同步服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持剪切板同步服务运行"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("剪切板同步")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(notificationId, createNotification(content))
    }
    
    private fun setupClipboardListener() {
        clipboardManager.addPrimaryClipChangedListener {
            serviceScope.launch {
                handleClipboardChange()
            }
        }
    }
    
    private suspend fun handleClipboardChange() {
        try {
            // 防止重复处理
            if (isProcessingClipboard) return
            isProcessingClipboard = true

            val config = configRepository.getConfig().first()
            if (!config.autoSync) return

            val clipData = clipboardManager.primaryClip
            if (clipData == null || clipData.itemCount == 0) return

            val item = clipData.getItemAt(0)
            val currentContent = when {
                item.text != null -> item.text.toString()
                item.uri != null -> item.uri.toString()
                else -> return
            }

            // 避免重复处理相同内容
            if (currentContent == lastClipboardContent) return
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
            // 临时禁用剪切板监听，避免触发自己的处理逻辑
            isProcessingClipboard = true

            val clipData = ClipData.newPlainText("Synced Text", text)
            clipboardManager.setPrimaryClip(clipData)

            Log.d(tag, "Successfully set text to clipboard: ${text.take(50)}...")
        } catch (e: Exception) {
            Log.e(tag, "Error setting text to clipboard", e)
        } finally {
            // 延迟重新启用剪切板监听，避免立即触发
            serviceScope.launch {
                kotlinx.coroutines.delay(1000) // 等待1秒
                isProcessingClipboard = false
                Log.d(tag, "Re-enabled clipboard monitoring after sync")
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
            val bitmap = loadBitmapFromUri(uri) ?: return null
            val base64 = bitmapToBase64(bitmap)
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())
            
            ClipboardItem(
                id = UUID.randomUUID().toString(),
                type = ClipboardType.image,
                content = base64,
                deviceId = config.deviceId,
                mimeType = "image/png",
                createdAt = now,
                updatedAt = now
            )
        } catch (e: Exception) {
            Log.e(tag, "Error creating image clipboard item", e)
            null
        }
    }
    
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            android.graphics.BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e(tag, "Error loading bitmap from URI", e)
            null
        }
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        Log.d(tag, "Encoded bitmap to base64, length: ${base64.length}")
        return base64
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
                        // 保存来自其他设备的同步内容为剪切板块
                        Log.d(tag, "Received sync from device: ${item.deviceId}, type: ${item.type}")

                        // 如果是文本类型，直接设置到系统剪切板
                        if (item.type == com.clipboardsync.app.domain.model.ClipboardType.text) {
                            Log.d(tag, "Setting text content to system clipboard: ${item.content.take(50)}...")
                            setTextToClipboard(item.content)
                            updateNotification("已同步文本到剪切板: ${getContentPreview(item)}")
                        } else {
                            // 非文本内容只保存，不设置到剪切板
                            if (item.type == com.clipboardsync.app.domain.model.ClipboardType.image) {
                                Log.d(tag, "Image content length: ${item.content.length}")
                                Log.d(tag, "Image content prefix: ${item.content.take(100)}")
                            }
                            updateNotification("收到同步: ${getContentPreview(item)}")
                        }

                        clipboardRepository.insertItem(item, isSynced = true)
                        Log.d(tag, "Received and saved sync from device: ${item.deviceId}")
                    } else {
                        Log.d(tag, "Ignored sync from own device: ${item.deviceId}")
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
}
