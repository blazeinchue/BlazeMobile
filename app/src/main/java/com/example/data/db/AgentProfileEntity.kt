package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AgentProvider {
    ANTHROPIC_CLAUDE,
    GOOGLE_GEMINI,
    OPENAI_GPT,
    BLAZE_CUSTOM_REST,
    LOCAL_AGENT
}

@Entity(tableName = "agent_profiles")
data class AgentProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val provider: AgentProvider,
    val endpointUrl: String = "",
    val apiKeyMasked: String = "",
    val isConnected: Boolean = true,
    val allowTouchSimulation: Boolean = true,
    val allowCalendarAccess: Boolean = true,
    val allowGalleryAccess: Boolean = true,
    val allowFileSystemAccess: Boolean = true,
    val allowAppLaunching: Boolean = true,
    val requireConfirmation: Boolean = false,
    val activeTasksCount: Int = 0,
    val lastConnectedTime: Long = System.currentTimeMillis()
)
