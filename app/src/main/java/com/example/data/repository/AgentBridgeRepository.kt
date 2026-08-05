package com.example.data.repository

import com.example.data.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AgentBridgeRepository(private val dao: AgentBridgeDao) {

    val agentProfiles: Flow<List<AgentProfileEntity>> = dao.getAllAgentProfiles()
    val actionLogs: Flow<List<AgentActionLogEntity>> = dao.getAllActionLogs()
    val calendarEvents: Flow<List<LocalCalendarEventEntity>> = dao.getAllCalendarEvents()
    val mediaAssets: Flow<List<LocalMediaAssetEntity>> = dao.getAllMediaAssets()
    val allFiles: Flow<List<LocalFileNodeEntity>> = dao.getAllFileNodes()
    val chatMessages: Flow<List<ChatMessageEntity>> = dao.getAllChatMessages()

    suspend fun addChatMessage(message: ChatMessageEntity) {
        dao.insertChatMessage(message)
    }

    suspend fun clearChat() {
        dao.clearChatMessages()
    }

    fun getFilesByParent(parentPath: String): Flow<List<LocalFileNodeEntity>> {
        return dao.getFilesByParentPath(parentPath)
    }

    suspend fun getFileByPath(path: String): LocalFileNodeEntity? {
        return dao.getFileByPath(path)
    }

    suspend fun addOrUpdateAgent(profile: AgentProfileEntity) {
        dao.insertAgentProfile(profile)
    }

    suspend fun updateAgent(profile: AgentProfileEntity) {
        dao.updateAgentProfile(profile)
    }

    suspend fun deleteAgent(id: Long) {
        dao.deleteAgentProfile(id)
    }

    suspend fun logAction(log: AgentActionLogEntity) {
        dao.insertActionLog(log)
    }

    suspend fun clearLogs() {
        dao.clearActionLogs()
    }

    suspend fun addCalendarEvent(event: LocalCalendarEventEntity) {
        dao.insertCalendarEvent(event)
    }

    suspend fun deleteCalendarEvent(id: Long) {
        dao.deleteCalendarEvent(id)
    }

    suspend fun addMediaAsset(asset: LocalMediaAssetEntity) {
        dao.insertMediaAsset(asset)
    }

    suspend fun addFileNode(node: LocalFileNodeEntity) {
        dao.insertFileNode(node)
    }

    suspend fun updateFileNode(node: LocalFileNodeEntity) {
        dao.updateFileNode(node)
    }

    suspend fun deleteFileByPath(path: String) {
        dao.deleteFileByPath(path)
    }

    suspend fun seedInitialDefaultsIfEmpty() {
        // Seed default agent profiles if none exist
        val existingAgents = agentProfiles.first()
        if (existingAgents.isEmpty()) {
            dao.insertAgentProfile(
                AgentProfileEntity(
                    name = "Claude 3.7 Sonnet Agent",
                    provider = AgentProvider.ANTHROPIC_CLAUDE,
                    endpointUrl = "https://api.anthropic.com/v1/messages",
                    apiKeyMasked = "sk-ant-api03-...",
                    isConnected = true,
                    allowTouchSimulation = true,
                    allowCalendarAccess = true,
                    allowGalleryAccess = true,
                    allowFileSystemAccess = true,
                    allowAppLaunching = true
                )
            )
            dao.insertAgentProfile(
                AgentProfileEntity(
                    name = "Google Gemini 2.5 Flash",
                    provider = AgentProvider.GOOGLE_GEMINI,
                    endpointUrl = "https://generativelanguage.googleapis.com",
                    apiKeyMasked = "AIzaSyBlaze...",
                    isConnected = true,
                    allowTouchSimulation = true,
                    allowCalendarAccess = true,
                    allowGalleryAccess = true,
                    allowFileSystemAccess = true,
                    allowAppLaunching = true
                )
            )
            dao.insertAgentProfile(
                AgentProfileEntity(
                    name = "OpenAI GPT-4o Agent",
                    provider = AgentProvider.OPENAI_GPT,
                    endpointUrl = "https://api.openai.com/v1/chat/completions",
                    apiKeyMasked = "sk-proj-...",
                    isConnected = false,
                    allowTouchSimulation = true,
                    allowCalendarAccess = true,
                    allowGalleryAccess = true,
                    allowFileSystemAccess = true,
                    allowAppLaunching = true
                )
            )
        }

        // Seed initial calendar events if empty
        val existingEvents = calendarEvents.first()
        if (existingEvents.isEmpty()) {
            val now = System.currentTimeMillis()
            dao.insertCalendarEvent(
                LocalCalendarEventEntity(
                    title = "Blaze Enterprise AI Review",
                    description = "Quarterly agent bridge performance & security audit",
                    location = "Main Boardroom / Google Meet",
                    startTimeMillis = now + (3600000 * 2),
                    endTimeMillis = now + (3600000 * 3),
                    category = "Work"
                )
            )
            dao.insertCalendarEvent(
                LocalCalendarEventEntity(
                    title = "Android Native Agent Protocol Demo",
                    description = "Demonstrate Touch/Swipe simulation, Gallery inspection & File navigation",
                    location = "Innovation Lab",
                    startTimeMillis = now + (86400000),
                    endTimeMillis = now + (86400000 + 3600000),
                    category = "Engineering"
                )
            )
            dao.insertCalendarEvent(
                LocalCalendarEventEntity(
                    title = "Design Sync with UX Team",
                    description = "Discuss Material 3 dark cyber aesthetic for Blaze Agent Bridge",
                    location = "Virtual Room 4",
                    startTimeMillis = now + (86400000 * 2),
                    endTimeMillis = now + (86400000 * 2 + 1800000),
                    category = "Design"
                )
            )
        }

        // Seed initial media assets if empty
        val existingMedia = mediaAssets.first()
        if (existingMedia.isEmpty()) {
            val now = System.currentTimeMillis()
            dao.insertMediaAsset(
                LocalMediaAssetEntity(
                    title = "blaze_system_architecture.png",
                    album = "Engineering Diagrams",
                    mediaType = "IMAGE",
                    relativePath = "/Pictures/Engineering/blaze_system_architecture.png",
                    mimeType = "image/png",
                    sizeBytes = 2450000,
                    dateAddedMillis = now - 86400000
                )
            )
            dao.insertMediaAsset(
                LocalMediaAssetEntity(
                    title = "q3_roadmap_presentation.png",
                    album = "Keynote Slides",
                    mediaType = "IMAGE",
                    relativePath = "/Pictures/Keynotes/q3_roadmap_presentation.png",
                    mimeType = "image/png",
                    sizeBytes = 1820000,
                    dateAddedMillis = now - (86400000 * 3)
                )
            )
            dao.insertMediaAsset(
                LocalMediaAssetEntity(
                    title = "agent_touch_simulation_test.mp4",
                    album = "Demos",
                    mediaType = "VIDEO",
                    relativePath = "/Movies/Demos/agent_touch_simulation_test.mp4",
                    mimeType = "video/mp4",
                    sizeBytes = 14200000,
                    dateAddedMillis = now - (86400000 * 5)
                )
            )
            dao.insertMediaAsset(
                LocalMediaAssetEntity(
                    title = "device_file_explorer_snapshot.jpg",
                    album = "Screenshots",
                    mediaType = "IMAGE",
                    relativePath = "/Pictures/Screenshots/device_file_explorer_snapshot.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 890000,
                    dateAddedMillis = now - 3600000
                )
            )
        }

        // Seed initial file nodes if empty
        val existingFiles = allFiles.first()
        if (existingFiles.isEmpty()) {
            val now = System.currentTimeMillis()
            // Root directories
            dao.insertFileNode(LocalFileNodeEntity(name = "Documents", path = "/Documents", isDirectory = true, parentPath = "/", lastModifiedMillis = now))
            dao.insertFileNode(LocalFileNodeEntity(name = "Downloads", path = "/Downloads", isDirectory = true, parentPath = "/", lastModifiedMillis = now))
            dao.insertFileNode(LocalFileNodeEntity(name = "Pictures", path = "/Pictures", isDirectory = true, parentPath = "/", lastModifiedMillis = now))
            dao.insertFileNode(LocalFileNodeEntity(name = "SystemConfig", path = "/SystemConfig", isDirectory = true, parentPath = "/", lastModifiedMillis = now))

            // Subfiles in /Documents
            dao.insertFileNode(
                LocalFileNodeEntity(
                    name = "blaze_agent_protocol.json",
                    path = "/Documents/blaze_agent_protocol.json",
                    isDirectory = false,
                    sizeBytes = 4096,
                    extension = "json",
                    parentPath = "/Documents",
                    content = """{
  "protocol": "Blaze Native Agent Bridge v1.0",
  "company": "Blaze Inc.",
  "features": [
    "screen_touch_simulation",
    "screen_swipe_simulation",
    "native_calendar_access",
    "native_gallery_access",
    "file_system_navigation",
    "application_launcher"
  ],
  "security_mode": "strict_permission_check"
}""".trimIndent(),
                    lastModifiedMillis = now
                )
            )
            dao.insertFileNode(
                LocalFileNodeEntity(
                    name = "agent_instructions.txt",
                    path = "/Documents/agent_instructions.txt",
                    isDirectory = false,
                    sizeBytes = 1250,
                    extension = "txt",
                    parentPath = "/Documents",
                    content = "Agents connecting via Blaze Bridge can perform touch gestures, read calendar events, explore photos, and list files on Android.",
                    lastModifiedMillis = now
                )
            )

            // Subfiles in /Downloads
            dao.insertFileNode(
                LocalFileNodeEntity(
                    name = "sample_dataset.csv",
                    path = "/Downloads/sample_dataset.csv",
                    isDirectory = false,
                    sizeBytes = 8192,
                    extension = "csv",
                    parentPath = "/Downloads",
                    content = "id,action,agent,result\n1,TAP,Claude,SUCCESS\n2,SWIPE,Gemini,SUCCESS\n3,READ_CALENDAR,Claude,SUCCESS",
                    lastModifiedMillis = now
                )
            )

            // Subfiles in /SystemConfig
            dao.insertFileNode(
                LocalFileNodeEntity(
                    name = "bridge_permissions.conf",
                    path = "/SystemConfig/bridge_permissions.conf",
                    isDirectory = false,
                    sizeBytes = 512,
                    extension = "conf",
                    parentPath = "/SystemConfig",
                    content = "ALLOW_TOUCH=true\nALLOW_CALENDAR=true\nALLOW_GALLERY=true\nALLOW_FILES=true\nALLOW_LAUNCH=true",
                    lastModifiedMillis = now
                )
            )
        }
    }
}
