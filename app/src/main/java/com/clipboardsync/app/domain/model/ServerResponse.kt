package com.clipboardsync.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerResponse(
    val type: String,
    val data: ResponseData
)

@Serializable
data class ResponseData(
    val message: String? = null,
    val connectionId: String? = null,
    val authenticated: Boolean? = null,
    val totalConnections: Int? = null,
    val activeConnections: Int? = null,
    val connectedDevices: List<ConnectedDevice>? = null
)

@Serializable
data class ConnectedDevice(
    val deviceId: String,
    val connectionId: String
)
