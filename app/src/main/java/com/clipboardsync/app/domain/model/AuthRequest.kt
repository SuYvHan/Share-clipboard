package com.clipboardsync.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val authKey: String? = null,
    val authValue: String? = null,
    @SerialName("deviceId")
    val deviceID: String? = null
)
