package com.clipboardsync.app.network.websocket

import android.util.Log
import com.clipboardsync.app.domain.model.AppConfig
import com.clipboardsync.app.domain.model.AuthRequest
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
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    fun connect(config: AppConfig) {
        currentConfig = config
        val url = config.websocketUrl
        Log.d(tag, "Connecting to WebSocket: $url")
        shouldReconnect = true
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "WebSocket connected")
                isConnected = true
                reconnectAttempts = 0

                // 连接成功后发送认证信息
                sendAuthRequest()

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
                Log.d(tag, "WebSocket closed: $code $reason")
                isConnected = false
                coroutineScope.launch {
                    _connectionStateFlow.emit(ConnectionState.Disconnected)
                }
                if (shouldReconnect) {
                    scheduleReconnect(url)
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket error", t)
                isConnected = false
                coroutineScope.launch {
                    _connectionStateFlow.emit(ConnectionState.Error(t.message ?: "Unknown error"))
                }
                if (shouldReconnect) {
                    scheduleReconnect(url)
                }
            }
        })
    }
    
    private fun scheduleReconnect(@Suppress("UNUSED_PARAMETER") url: String) {
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
        Log.d(tag, "Disconnecting WebSocket")
        shouldReconnect = false
        isConnected = false
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
    
    fun isConnected(): Boolean = isConnected

    private fun sendAuthRequest() {
        currentConfig?.let { config ->
            try {
                val authRequest = AuthRequest(
                    authKey = config.authKey.takeIf { it.isNotEmpty() },
                    authValue = config.authValue.takeIf { it.isNotEmpty() },
                    deviceId = config.deviceId.takeIf { it.isNotEmpty() }
                )

                val jsonString = json.encodeToString(authRequest)
                webSocket?.send(jsonString)
                Log.d(tag, "Sent auth request: $jsonString")
            } catch (e: Exception) {
                Log.e(tag, "Failed to send auth request", e)
            }
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
