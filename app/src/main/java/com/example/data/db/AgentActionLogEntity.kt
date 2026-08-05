package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ActionType {
    TOUCH_TAP,
    TOUCH_SWIPE,
    OPEN_APP,
    CALENDAR_READ,
    CALENDAR_CREATE,
    GALLERY_READ,
    FILE_LIST,
    FILE_READ,
    FILE_WRITE,
    SYSTEM_STATUS
}

enum class ActionStatus {
    EXECUTED,
    PENDING_CONFIRMATION,
    BLOCKED_PERMISSIONS,
    FAILED
}

@Entity(tableName = "agent_action_logs")
data class AgentActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val agentId: Long,
    val agentName: String,
    val provider: String,
    val actionType: ActionType,
    val parametersJson: String,
    val status: ActionStatus,
    val executionResult: String,
    val touchX: Float = -1f,
    val touchY: Float = -1f,
    val timestamp: Long = System.currentTimeMillis()
)
