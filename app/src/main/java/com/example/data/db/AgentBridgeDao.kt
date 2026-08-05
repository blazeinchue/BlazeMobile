package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentBridgeDao {
    // Agent Profiles
    @Query("SELECT * FROM agent_profiles ORDER BY id DESC")
    fun getAllAgentProfiles(): Flow<List<AgentProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgentProfile(profile: AgentProfileEntity): Long

    @Update
    suspend fun updateAgentProfile(profile: AgentProfileEntity)

    @Query("DELETE FROM agent_profiles WHERE id = :id")
    suspend fun deleteAgentProfile(id: Long)

    // Action Logs
    @Query("SELECT * FROM agent_action_logs ORDER BY timestamp DESC")
    fun getAllActionLogs(): Flow<List<AgentActionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLog(log: AgentActionLogEntity): Long

    @Query("DELETE FROM agent_action_logs")
    suspend fun clearActionLogs()

    // Chat Messages
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllChatMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatMessages()

    // Calendar
    @Query("SELECT * FROM local_calendar_events ORDER BY startTimeMillis ASC")
    fun getAllCalendarEvents(): Flow<List<LocalCalendarEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarEvent(event: LocalCalendarEventEntity): Long

    @Query("DELETE FROM local_calendar_events WHERE id = :id")
    suspend fun deleteCalendarEvent(id: Long)

    // Media Assets
    @Query("SELECT * FROM local_media_assets ORDER BY dateAddedMillis DESC")
    fun getAllMediaAssets(): Flow<List<LocalMediaAssetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaAsset(asset: LocalMediaAssetEntity): Long

    // Files
    @Query("SELECT * FROM local_file_nodes WHERE parentPath = :parentPath ORDER BY isDirectory DESC, name ASC")
    fun getFilesByParentPath(parentPath: String): Flow<List<LocalFileNodeEntity>>

    @Query("SELECT * FROM local_file_nodes WHERE path = :path LIMIT 1")
    suspend fun getFileByPath(path: String): LocalFileNodeEntity?

    @Query("SELECT * FROM local_file_nodes ORDER BY lastModifiedMillis DESC")
    fun getAllFileNodes(): Flow<List<LocalFileNodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFileNode(node: LocalFileNodeEntity): Long

    @Update
    suspend fun updateFileNode(node: LocalFileNodeEntity)

    @Query("DELETE FROM local_file_nodes WHERE path = :path")
    suspend fun deleteFileByPath(path: String)
}
