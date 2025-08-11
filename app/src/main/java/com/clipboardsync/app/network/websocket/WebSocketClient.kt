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
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketClient @Inject constructor() {
    
    private val tag = "WebSocketClient"
    private var webSocket: WebSocket? = null
    private var isConnected = false
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
        currentConfig = config
        val url = config.websocketUrlWithAuth
        Log.d(tag, "Connecting to WebSocket with auth: $url")
        shouldReconnect = true
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(tag, "WebSocket connected successfully with URL auth")
                isConnected = true
                reconnectAttempts = 0

                // URL参数认证，连接成功即表示认证成功
                coroutineScope.launch {
                    _connectionStateFlow.emit(ConnectionState.Connected)
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(tag, "Received message: $text")
                try {
                    val message = json.decodeFromString<WebSocketMessage>(text)
                    coroutineScope.launch {
                        _messageFlow.emit(message)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse WebSocket message", e)
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
                "fileSize" to (item.fileSize ?: 0),
                "mimeType" to (item.mimeType ?: "")
            )
        )
        sendMessage(message)
    }
    
    fun requestAllContent(limit: Int = 100) {
        val message = WebSocketRequest(
            type = "get_all_content",
            data = mapOf("limit" to limit)
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
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected = false
        coroutineScope.launch {
            _connectionStateFlow.emit(ConnectionState.Disconnected)
        }
    }

    fun isConnected(): Boolean = isConnected



    sealed class ConnectionState {
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        data class Reconnecting(val attempt: Int) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
        data class Failed(val message: String) : ConnectionState()
    }
}
