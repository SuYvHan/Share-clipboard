package com.clipboardsync.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.clipboardsync.app.domain.model.ClipboardItem
import com.clipboardsync.app.domain.model.ClipboardType

@Entity(tableName = "clipboard_items")
data class ClipboardEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val content: String,
    val deviceId: String,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val filePath: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val isSynced: Boolean = false,
    val localTimestamp: Long = System.currentTimeMillis()
)

fun ClipboardEntity.toDomainModel(): ClipboardItem {
    return ClipboardItem(
        id = id,
        type = ClipboardType.valueOf(type),
        content = content,
        deviceId = deviceId,
        fileName = fileName,
        fileSize = fileSize,
        mimeType = mimeType,
        filePath = filePath,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun ClipboardItem.toEntity(isSynced: Boolean = false): ClipboardEntity {
    return ClipboardEntity(
        id = id,
        type = type.name,
        content = content,
        deviceId = deviceId,
        fileName = fileName,
        fileSize = fileSize,
        mimeType = mimeType,
        filePath = filePath,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSynced = isSynced
    )
}
