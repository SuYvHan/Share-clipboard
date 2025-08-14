package com.clipboardsync.app.domain.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class ClipboardItem(
    val id: String,
    val type: ClipboardType,
    val content: String,
    val deviceId: String,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val filePath: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
enum class ClipboardType {
    text, image, file
}

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val total: Int? = null
)

@Serializable
data class CreateClipboardRequest(
    val type: ClipboardType,
    val content: String,
    val deviceId: String,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null
)

@Serializable
data class WebSocketMessage(
    val type: String,
    val data: ClipboardItem? = null,
    val items: List<ClipboardItem>? = null,
    val id: String? = null,
    val count: Int? = null,
    val message: String? = null,
    val success: Boolean? = null,
    val total: Int? = null
)

@Serializable
data class WebSocketRequest(
    val type: String,
    val data: Map<String, String>? = null,
    val id: String? = null,
    val count: Int? = null,
    val limit: Int? = null
)
