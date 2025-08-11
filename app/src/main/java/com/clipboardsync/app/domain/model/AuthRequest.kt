package com.clipboardsync.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val authKey: String? = null,
    val authValue: String? = null,
    val deviceId: String? = null
)
