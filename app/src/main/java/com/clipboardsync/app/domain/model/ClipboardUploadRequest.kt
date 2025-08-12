package com.clipboardsync.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ClipboardUploadRequest(
    val type: String, // "text", "image", "file"
    val content: String,
    val deviceId: String
)
