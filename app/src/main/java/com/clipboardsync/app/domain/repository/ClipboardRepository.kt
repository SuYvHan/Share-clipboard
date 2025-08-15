package com.clipboardsync.app.domain.repository

import com.clipboardsync.app.domain.model.ClipboardItem
import com.clipboardsync.app.domain.model.ClipboardType
import kotlinx.coroutines.flow.Flow

interface ClipboardRepository {
    fun getAllItems(): Flow<List<ClipboardItem>>
    fun getItemsByType(type: ClipboardType): Flow<List<ClipboardItem>>
    fun getLatestItems(limit: Int): Flow<List<ClipboardItem>>
    fun searchItems(query: String): Flow<List<ClipboardItem>>
    suspend fun getItemById(id: String): ClipboardItem?
    suspend fun insertItem(item: ClipboardItem, isSynced: Boolean = false)
    suspend fun insertItems(items: List<ClipboardItem>, isSynced: Boolean = false)
    suspend fun updateItem(item: ClipboardItem)
    suspend fun deleteItem(id: String)
    suspend fun deleteAllItems()
    suspend fun markAsSynced(id: String)
    suspend fun markAsSynced(ids: List<String>)
    suspend fun getUnsyncedItems(): List<ClipboardItem>
    suspend fun getSyncedItems(): List<ClipboardItem>
    suspend fun isContentSynced(content: String): Boolean
    suspend fun cleanupOldItems(maxItems: Int, maxDays: Int)
    suspend fun getItemCount(): Int
    suspend fun getItemCountByType(type: ClipboardType): Int
    
    // 远程操作
    suspend fun syncWithServer(): Result<List<ClipboardItem>>
    suspend fun uploadItem(item: ClipboardItem): Result<ClipboardItem>
    suspend fun deleteItemFromServer(id: String): Result<Unit>
}
