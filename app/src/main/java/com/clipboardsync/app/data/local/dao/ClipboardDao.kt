package com.clipboardsync.app.data.local.dao

import androidx.room.*
import com.clipboardsync.app.data.local.entity.ClipboardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    
    @Query("SELECT * FROM clipboard_items ORDER BY localTimestamp DESC")
    fun getAllItems(): Flow<List<ClipboardEntity>>
    
    @Query("SELECT * FROM clipboard_items WHERE type = :type ORDER BY localTimestamp DESC")
    fun getItemsByType(type: String): Flow<List<ClipboardEntity>>
    
    @Query("SELECT * FROM clipboard_items ORDER BY localTimestamp DESC LIMIT :limit")
    fun getLatestItems(limit: Int): Flow<List<ClipboardEntity>>
    
    @Query("SELECT * FROM clipboard_items WHERE id = :id")
    suspend fun getItemById(id: String): ClipboardEntity?
    
    @Query("SELECT * FROM clipboard_items WHERE isSynced = 0 ORDER BY localTimestamp ASC")
    suspend fun getUnsyncedItems(): List<ClipboardEntity>

    @Query("SELECT * FROM clipboard_items WHERE isSynced = 1 ORDER BY localTimestamp DESC")
    suspend fun getSyncedItems(): List<ClipboardEntity>

    @Query("SELECT COUNT(*) > 0 FROM clipboard_items WHERE content = :content AND isSynced = 1")
    suspend fun isContentSynced(content: String): Boolean

    @Query("SELECT * FROM clipboard_items WHERE content LIKE '%' || :query || '%' ORDER BY localTimestamp DESC")
    fun searchItems(query: String): Flow<List<ClipboardEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ClipboardEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ClipboardEntity>)
    
    @Update
    suspend fun updateItem(item: ClipboardEntity)
    
    @Query("UPDATE clipboard_items SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
    
    @Query("UPDATE clipboard_items SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)
    
    @Delete
    suspend fun deleteItem(item: ClipboardEntity)
    
    @Query("DELETE FROM clipboard_items WHERE id = :id")
    suspend fun deleteItemById(id: String)
    
    @Query("DELETE FROM clipboard_items WHERE localTimestamp < :timestamp")
    suspend fun deleteOldItems(timestamp: Long)
    
    @Query("DELETE FROM clipboard_items")
    suspend fun deleteAllItems()
    
    @Query("SELECT COUNT(*) FROM clipboard_items")
    suspend fun getItemCount(): Int
    
    @Query("SELECT COUNT(*) FROM clipboard_items WHERE type = :type")
    suspend fun getItemCountByType(type: String): Int
    
    @Query("""
        DELETE FROM clipboard_items 
        WHERE id NOT IN (
            SELECT id FROM clipboard_items 
            ORDER BY localTimestamp DESC 
            LIMIT :maxItems
        )
    """)
    suspend fun keepOnlyLatestItems(maxItems: Int)
}
