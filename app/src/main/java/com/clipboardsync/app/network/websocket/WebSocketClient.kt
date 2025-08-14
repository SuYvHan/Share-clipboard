package com.clipboardsync.app.network.websocket

import android.util.Log
import com.clipboardsync.app.domain.model.AppConfig
import com.clipboardsync.app.domain.model.ClipboardItem
import com.clipboardsync.app.domain.model.WebSocketMessage
import com.clipboardsync.app.domain.model.WebSocketRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketClient @Inject constructor() {
    
    private val tag = "WebSocketClient"
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var isConnecting = false
    private var shouldReconnect = true
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelayMs = 5000L
    private var currentConfig: AppConfig? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _messageFlow = MutableSharedFlow<WebSocketMessage>()
    val messageFlow: SharedFlow<WebSocketMessage> = _messageFlow.asSharedFlow()
    
    private val _connectionStateFlow = MutableSharedFlow<ConnectionState>()
    val connectionStateFlow: SharedFlow<ConnectionState> = _connectionStateFlow.asSharedFlow()
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        allowStructuredMapKeys = true
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // 无限读取超时，保持长连接
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    fun connect(config: AppConfig) {
        // 如果已经连接或正在连接，先断开
        if (isConnected || isConnecting || webSocket != null) {
            Log.d(tag, "Already connected or connecting, disconnecting first")
            disconnect()
        }

        currentConfig = config
        val url = config.websocketUrlWithAuth
        Log.d(tag, "Connecting to WebSocket with auth: $url")
        shouldReconnect = true
        isConnecting = true
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(tag, "WebSocket connected successfully with URL auth")
                isConnected = true
                isConnecting = false
                reconnectAttempts = 0

                // URL参数认证，连接成功即表示认证成功
                coroutineScope.launch {
                    _connectionStateFlow.emit(ConnectionState.Connected)
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(tag, "Received message: $text")
                try {
                    // 先尝试解析为通用的JSON对象
                    val jsonElement = json.parseToJsonElement(text)
                    val jsonObject = jsonElement.jsonObject
                    val messageType = jsonObject["type"]?.jsonPrimitive?.content

                    when (messageType) {
                        "sync" -> {
                            // 处理同步消息
                            try {
                                val dataElement = jsonObject["data"]
                                if (dataElement != null && dataElement !is JsonNull) {
                                    // 检查data是否包含ClipboardItem的必需字段
                                    val dataObject = dataElement.jsonObject
                                    val hasRequiredFields = dataObject.containsKey("id") &&
                                                          dataObject.containsKey("type") &&
                                                          dataObject.containsKey("content") &&
                                                          dataObject.containsKey("deviceId") &&
                                                          dataObject.containsKey("createdAt") &&
                                                          dataObject.containsKey("updatedAt")

                                    if (hasRequiredFields) {
                                        val clipboardItem = json.decodeFromJsonElement<ClipboardItem>(dataElement)
                                        val message = WebSocketMessage(
                                            type = "sync",
                                            data = clipboardItem
                                        )
                                        coroutineScope.launch {
                                            _messageFlow.emit(message)
                                        }
                                    } else {
                                        // 这是连接成功或其他状态消息，不是剪切板数据
                                        Log.d(tag, "Received sync status message: $text")
                                        val message = WebSocketMessage(
                                            type = "sync_status",
                                            message = dataObject["message"]?.jsonPrimitive?.content
                                        )
                                        coroutineScope.launch {
                                            _messageFlow.emit(message)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Failed to parse sync message", e)
                                // 发送错误状态消息
                                val errorMessage = WebSocketMessage(
                                    type = "sync_error",
                                    message = "消息解析失败: ${e.message}"
                                )
                                coroutineScope.launch {
                                    _messageFlow.emit(errorMessage)
                                }
                            }
                        }
                        "delete" -> {
                            // 处理删除消息
                            val id = jsonObject["id"]?.jsonPrimitive?.content
                            val message = WebSocketMessage(
                                type = "delete",
                                id = id
                            )
                            coroutineScope.launch {
                                _messageFlow.emit(message)
                            }
                        }
                        "all_content" -> {
                            // 处理获取所有内容的响应
                            try {
                                val dataElement = jsonObject["data"]
                                val count = jsonObject["count"]?.jsonPrimitive?.intOrNull
                                val total = jsonObject["total"]?.jsonPrimitive?.intOrNull

                                if (dataElement != null && dataElement is JsonArray) {
                                    val items = json.decodeFromJsonElement<List<ClipboardItem>>(dataElement)
                                    val message = WebSocketMessage(
                                        type = "all_content",
                                        items = items,
                                        count = count,
                                        total = total
                                    )
                                    coroutineScope.launch {
                                        _messageFlow.emit(message)
                                    }
                                } else {
                                    // 如果data不是数组，可能是空的响应
                                    val message = WebSocketMessage(
                                        type = "all_content",
                                        items = emptyList(),
                                        count = count ?: 0,
                                        total = total ?: 0
                                    )
                                    coroutineScope.launch {
                                        _messageFlow.emit(message)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Failed to parse all_content message", e)
                            }
                        }
                        "connection_stats" -> {
                            // 连接统计信息，记录日志但不处理
                            Log.i(tag, "Received connection stats: $text")
                        }
                        else -> {
                            // 其他类型的消息（如连接成功消息），记录日志但不处理
                            Log.i(tag, "Received message of type '$messageType': $text")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse WebSocket message: $text", e)
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closing: $code $reason")
                isConnected = false
                coroutineScope.launch {
                    _connectionStateFlow.emit(ConnectionState.Disconnected)
                }
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(tag, "WebSocket closed: $code $reason")
                isConnected = false
                coroutineScope.launch {
                    _connectionStateFlow.emit(ConnectionState.Disconnected)
                }

                // 只有在非正常关闭且应该重连时才重连
                if (shouldReconnect && code != 1000) {
                    Log.i(tag, "Unexpected close, will attempt to reconnect")
                    scheduleReconnect()
                } else {
                    Log.i(tag, "Normal close or reconnect disabled, not reconnecting")
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket error", t)
                isConnected = false
                isConnecting = false
                coroutineScope.launch {
                    _connectionStateFlow.emit(ConnectionState.Error(t.message ?: "Unknown error"))
                }
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }
        })
    }
    
    private fun scheduleReconnect() {
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            Log.d(tag, "Scheduling reconnect attempt $reconnectAttempts in ${reconnectDelayMs}ms")
            coroutineScope.launch {
                _connectionStateFlow.emit(ConnectionState.Reconnecting(reconnectAttempts))
                delay(reconnectDelayMs)
                if (shouldReconnect && currentConfig != null) {
                    connect(currentConfig!!)
                }
            }
        } else {
            Log.e(tag, "Max reconnect attempts reached")
            coroutineScope.launch {
                _connectionStateFlow.emit(ConnectionState.Failed("Max reconnect attempts reached"))
            }
        }
    }
    
    fun sendMessage(message: WebSocketRequest) {
        if (isConnected) {
            try {
                val jsonString = json.encodeToString(message)
                webSocket?.send(jsonString)
                Log.d(tag, "Sent message: $jsonString")
            } catch (e: Exception) {
                Log.e(tag, "Failed to send message", e)
            }
        } else {
            Log.w(tag, "WebSocket not connected, cannot send message")
        }
    }
    
    fun syncClipboardItem(item: ClipboardItem) {
        val message = WebSocketRequest(
            type = "sync",
            data = mapOf(
                "id" to item.id,
                "type" to item.type.name,
                "content" to item.content,
                "deviceId" to item.deviceId,
                "fileName" to (item.fileName ?: ""),
                "fileSize" to (item.fileSize?.toString() ?: "0"),
                "mimeType" to (item.mimeType ?: ""),
                "createdAt" to item.createdAt,
                "updatedAt" to item.updatedAt
            )
        )
        sendMessage(message)
    }
    
    fun requestAllContent(limit: Int = 100) {
        val message = WebSocketRequest(
            type = "get_all_content",
            data = mapOf("limit" to limit.toString())
        )
        sendMessage(message)
    }

    fun requestLatestContent(count: Int = 10) {
        val message = WebSocketRequest(
            type = "get_latest",
            count = count
        )
        sendMessage(message)
    }
    
    fun deleteItem(id: String) {
        val message = WebSocketRequest(
            type = "delete",
            id = id
        )
        sendMessage(message)
    }
    
    fun disconnect() {
        Log.d(tag, "Manually disconnecting WebSocket")
        shouldReconnect = false
        isConnected = false
        isConnecting = false
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        coroutineScope.launch {
            _connectionStateFlow.emit(ConnectionState.Disconnected)
        }
    }

    fun isConnected(): Boolean = isConnected

    fun isConnectedOrConnecting(): Boolean = isConnected || isConnecting

    fun reconnect(config: AppConfig) {
        Log.d(tag, "Reconnecting WebSocket with new config")
        coroutineScope.launch {
            // 先断开现有连接
            if (isConnected) {
                Log.d(tag, "Disconnecting existing connection")
                shouldReconnect = false
                isConnected = false
                webSocket?.close(1000, "Reconnecting with new config")
                webSocket = null
                _connectionStateFlow.emit(ConnectionState.Disconnected)

                // 等待一下确保连接完全断开
                kotlinx.coroutines.delay(500)
            }

            // 使用新配置重新连接
            Log.d(tag, "Connecting with new config")
            connect(config)
        }
    }

    sealed class ConnectionState {
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        data class Reconnecting(val attempt: Int) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
        data class Failed(val message: String) : ConnectionState()
    }
}
