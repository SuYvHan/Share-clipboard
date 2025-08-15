package com.clipboardsync.app.data.repository

import android.util.Log
import com.clipboardsync.app.data.local.dao.ClipboardDao
import com.clipboardsync.app.data.local.entity.toDomainModel
import com.clipboardsync.app.data.local.entity.toEntity
import com.clipboardsync.app.domain.model.ClipboardItem
import com.clipboardsync.app.domain.model.ClipboardType

import com.clipboardsync.app.domain.repository.ClipboardRepository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardRepositoryImpl @Inject constructor(
    private val clipboardDao: ClipboardDao
) : ClipboardRepository {
    
    private val tag = "ClipboardRepository"
    
    override fun getAllItems(): Flow<List<ClipboardItem>> {
        return clipboardDao.getAllItems().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun getItemsByType(type: ClipboardType): Flow<List<ClipboardItem>> {
        return clipboardDao.getItemsByType(type.name).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun getLatestItems(limit: Int): Flow<List<ClipboardItem>> {
        return clipboardDao.getLatestItems(limit).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun searchItems(query: String): Flow<List<ClipboardItem>> {
        return clipboardDao.searchItems(query).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getItemById(id: String): ClipboardItem? {
        return clipboardDao.getItemById(id)?.toDomainModel()
    }
    
    override suspend fun insertItem(item: ClipboardItem, isSynced: Boolean) {
        clipboardDao.insertItem(item.toEntity(isSynced))
    }
    
    override suspend fun insertItems(items: List<ClipboardItem>, isSynced: Boolean) {
        clipboardDao.insertItems(items.map { it.toEntity(isSynced) })
    }
    
    override suspend fun updateItem(item: ClipboardItem) {
        val entity = clipboardDao.getItemById(item.id)
        if (entity != null) {
            clipboardDao.updateItem(item.toEntity(entity.isSynced))
        }
    }
    
    override suspend fun deleteItem(id: String) {
        clipboardDao.deleteItemById(id)
    }
    
    override suspend fun deleteAllItems() {
        clipboardDao.deleteAllItems()
    }
    
    override suspend fun markAsSynced(id: String) {
        clipboardDao.markAsSynced(id)
    }
    
    override suspend fun markAsSynced(ids: List<String>) {
        clipboardDao.markAsSynced(ids)
    }
    
    override suspend fun getUnsyncedItems(): List<ClipboardItem> {
        return clipboardDao.getUnsyncedItems().map { it.toDomainModel() }
    }

    override suspend fun getSyncedItems(): List<ClipboardItem> {
        return clipboardDao.getSyncedItems().map { it.toDomainModel() }
    }

    override suspend fun isContentSynced(content: String): Boolean {
        return clipboardDao.isContentSynced(content)
    }

    override suspend fun cleanupOldItems(maxItems: Int, maxDays: Int) {
        // 删除超过指定天数的项目
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxDays.toLong())
        clipboardDao.deleteOldItems(cutoffTime)
        
        // 保留最新的指定数量的项目
        clipboardDao.keepOnlyLatestItems(maxItems)
    }
    
    override suspend fun getItemCount(): Int {
        return clipboardDao.getItemCount()
    }
    
    override suspend fun getItemCountByType(type: ClipboardType): Int {
        return clipboardDao.getItemCountByType(type.name)
    }
    
    // WebSocket-only implementation - HTTP methods removed
    override suspend fun syncWithServer(): Result<List<ClipboardItem>> {
        // WebSocket handles real-time sync, no HTTP sync needed
        return Result.success(emptyList())
    }

    override suspend fun uploadItem(item: ClipboardItem): Result<ClipboardItem> {
        // WebSocket handles item upload, no HTTP upload needed
        markAsSynced(item.id)
        return Result.success(item)
    }

    override suspend fun deleteItemFromServer(id: String): Result<Unit> {
        // WebSocket handles item deletion, no HTTP deletion needed
        deleteItem(id)
        return Result.success(Unit)
    }
}
