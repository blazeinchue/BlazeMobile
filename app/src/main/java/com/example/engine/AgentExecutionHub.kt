package com.example.engine

import com.example.data.db.ActionStatus
import com.example.data.db.ActionType
import com.example.data.db.AgentActionLogEntity
import com.example.data.db.AgentProfileEntity
import com.example.data.repository.AgentBridgeRepository
import kotlinx.coroutines.flow.first

sealed class AgentExecutionResult {
    data class Success(val message: String, val actionType: ActionType, val details: String) : AgentExecutionResult()
    data class Error(val errorMsg: String) : AgentExecutionResult()
    data class RequiresConfirmation(val pendingAction: String) : AgentExecutionResult()
}

class AgentExecutionHub(
    val repository: AgentBridgeRepository,
    val touchEngine: ScreenTouchEngine,
    val calendarEngine: CalendarEngine,
    val galleryEngine: GalleryEngine,
    val fileEngine: FileSystemEngine,
    val appLauncherEngine: AppLauncherEngine
) {

    suspend fun processCommand(
        agentProfile: AgentProfileEntity,
        rawPrompt: String
    ): AgentExecutionResult {
        val trimmed = rawPrompt.trim()
        val lower = trimmed.lowercase()

        // Check permissions and dispatch based on intent keywords or structured tool syntax
        return when {
            lower.contains("tap") || lower.contains("touch") || lower.contains("click") -> {
                if (!agentProfile.allowTouchSimulation) {
                    logBlockedAction(agentProfile, ActionType.TOUCH_TAP, rawPrompt, "Touch simulation permission is disabled for this agent profile.")
                    return AgentExecutionResult.Error("Action BLOCKED: Touch simulation permission is disabled.")
                }
                // Parse coordinates if provided like "tap 0.5 0.5" or default to center
                val coords = extractCoordinates(rawPrompt)
                val event = touchEngine.simulateTap(coords.first, coords.second, "Simulated Target")
                val resultStr = "Screen Touch TAP executed at (X: ${String.format("%.2f", coords.first)}, Y: ${String.format("%.2f", coords.second)}) [${event.absoluteXDp.toInt()}dp, ${event.absoluteYDp.toInt()}dp]"

                repository.logAction(
                    AgentActionLogEntity(
                        agentId = agentProfile.id,
                        agentName = agentProfile.name,
                        provider = agentProfile.provider.name,
                        actionType = ActionType.TOUCH_TAP,
                        parametersJson = """{"x": ${coords.first}, "y": ${coords.second}}""",
                        status = ActionStatus.EXECUTED,
                        executionResult = resultStr,
                        touchX = coords.first,
                        touchY = coords.second
                    )
                )
                AgentExecutionResult.Success(resultStr, ActionType.TOUCH_TAP, "Coordinates: (${coords.first}, ${coords.second})")
            }

            lower.contains("swipe") || lower.contains("scroll") -> {
                if (!agentProfile.allowTouchSimulation) {
                    logBlockedAction(agentProfile, ActionType.TOUCH_SWIPE, rawPrompt, "Touch simulation permission is disabled.")
                    return AgentExecutionResult.Error("Action BLOCKED: Touch simulation permission disabled.")
                }
                val dir = when {
                    lower.contains("up") -> "UP"
                    lower.contains("down") -> "DOWN"
                    lower.contains("left") -> "LEFT"
                    lower.contains("right") -> "RIGHT"
                    else -> "UP"
                }
                val event = touchEngine.simulateSwipe(dir)
                val resultStr = "Screen Gesture SWIPE $dir executed smoothly across display."

                repository.logAction(
                    AgentActionLogEntity(
                        agentId = agentProfile.id,
                        agentName = agentProfile.name,
                        provider = agentProfile.provider.name,
                        actionType = ActionType.TOUCH_SWIPE,
                        parametersJson = """{"direction": "$dir"}""",
                        status = ActionStatus.EXECUTED,
                        executionResult = resultStr,
                        touchX = event.xRatio,
                        touchY = event.yRatio
                    )
                )
                AgentExecutionResult.Success(resultStr, ActionType.TOUCH_SWIPE, "Gesture: Swipe $dir")
            }

            lower.contains("calendar") || lower.contains("event") || lower.contains("schedule") -> {
                if (!agentProfile.allowCalendarAccess) {
                    logBlockedAction(agentProfile, ActionType.CALENDAR_READ, rawPrompt, "Calendar access permission is disabled.")
                    return AgentExecutionResult.Error("Action BLOCKED: Calendar access is disabled.")
                }
                val resultStr = if (lower.contains("add") || lower.contains("create") || lower.contains("new")) {
                    val title = extractTitle(trimmed, "Meeting with Blaze Team")
                    calendarEngine.createEvent(title, "Created via AI Agent Bridge", "Virtual Room")
                } else {
                    calendarEngine.getUpcomingEvents()
                }

                repository.logAction(
                    AgentActionLogEntity(
                        agentId = agentProfile.id,
                        agentName = agentProfile.name,
                        provider = agentProfile.provider.name,
                        actionType = if (lower.contains("add")) ActionType.CALENDAR_CREATE else ActionType.CALENDAR_READ,
                        parametersJson = """{"query": "$rawPrompt"}""",
                        status = ActionStatus.EXECUTED,
                        executionResult = resultStr
                    )
                )
                AgentExecutionResult.Success("Calendar Operation Completed", ActionType.CALENDAR_READ, resultStr)
            }

            lower.contains("photo") || lower.contains("gallery") || lower.contains("media") || lower.contains("picture") -> {
                if (!agentProfile.allowGalleryAccess) {
                    logBlockedAction(agentProfile, ActionType.GALLERY_READ, rawPrompt, "Gallery access permission is disabled.")
                    return AgentExecutionResult.Error("Action BLOCKED: Gallery access is disabled.")
                }
                val resultStr = galleryEngine.getGalleryAssetsSummary()

                repository.logAction(
                    AgentActionLogEntity(
                        agentId = agentProfile.id,
                        agentName = agentProfile.name,
                        provider = agentProfile.provider.name,
                        actionType = ActionType.GALLERY_READ,
                        parametersJson = """{"query": "$rawPrompt"}""",
                        status = ActionStatus.EXECUTED,
                        executionResult = resultStr
                    )
                )
                AgentExecutionResult.Success("Gallery Query Executed", ActionType.GALLERY_READ, resultStr)
            }

            lower.contains("file") || lower.contains("dir") || lower.contains("folder") || lower.contains("read") || lower.contains("ls") -> {
                if (!agentProfile.allowFileSystemAccess) {
                    logBlockedAction(agentProfile, ActionType.FILE_LIST, rawPrompt, "File system permission is disabled.")
                    return AgentExecutionResult.Error("Action BLOCKED: File system permission disabled.")
                }
                val resultStr = if (lower.contains("read") || lower.contains("cat") || lower.contains("open")) {
                    val path = extractPath(trimmed, "/Documents/blaze_agent_protocol.json")
                    fileEngine.readFileContent(path)
                } else {
                    val path = extractPath(trimmed, "/Documents")
                    fileEngine.listDirectoryContents(path)
                }

                repository.logAction(
                    AgentActionLogEntity(
                        agentId = agentProfile.id,
                        agentName = agentProfile.name,
                        provider = agentProfile.provider.name,
                        actionType = ActionType.FILE_READ,
                        parametersJson = """{"query": "$rawPrompt"}""",
                        status = ActionStatus.EXECUTED,
                        executionResult = resultStr
                    )
                )
                AgentExecutionResult.Success("File System Task Executed", ActionType.FILE_READ, resultStr)
            }

            lower.contains("open") || lower.contains("launch") || lower.contains("app") -> {
                if (!agentProfile.allowAppLaunching) {
                    logBlockedAction(agentProfile, ActionType.OPEN_APP, rawPrompt, "App launching permission is disabled.")
                    return AgentExecutionResult.Error("Action BLOCKED: App launching is disabled.")
                }
                val appName = trimmed.replace("open", "", true).replace("launch", "", true).replace("app", "", true).trim()
                val targetApp = appName.ifEmpty { "Settings" }
                val resultStr = appLauncherEngine.launchApp(targetApp)

                repository.logAction(
                    AgentActionLogEntity(
                        agentId = agentProfile.id,
                        agentName = agentProfile.name,
                        provider = agentProfile.provider.name,
                        actionType = ActionType.OPEN_APP,
                        parametersJson = """{"appName": "$targetApp"}""",
                        status = ActionStatus.EXECUTED,
                        executionResult = resultStr
                    )
                )
                AgentExecutionResult.Success("App Launcher Action Executed", ActionType.OPEN_APP, resultStr)
            }

            else -> {
                // Intelligent multi-intent solver fallback
                val calRes = if (agentProfile.allowCalendarAccess) calendarEngine.getUpcomingEvents() else "[Calendar Access Disabled]"
                val fileRes = if (agentProfile.allowFileSystemAccess) fileEngine.listDirectoryContents("/Documents") else "[Files Disabled]"
                val summary = "Agent executed full system status scan:\n\n1. Calendar Status:\n$calRes\n\n2. Primary Documents Directory:\n$fileRes"

                repository.logAction(
                    AgentActionLogEntity(
                        agentId = agentProfile.id,
                        agentName = agentProfile.name,
                        provider = agentProfile.provider.name,
                        actionType = ActionType.SYSTEM_STATUS,
                        parametersJson = """{"prompt": "$rawPrompt"}""",
                        status = ActionStatus.EXECUTED,
                        executionResult = summary
                    )
                )
                AgentExecutionResult.Success("System Scan Executed", ActionType.SYSTEM_STATUS, summary)
            }
        }
    }

    private suspend fun logBlockedAction(
        profile: AgentProfileEntity,
        type: ActionType,
        prompt: String,
        reason: String
    ) {
        repository.logAction(
            AgentActionLogEntity(
                agentId = profile.id,
                agentName = profile.name,
                provider = profile.provider.name,
                actionType = type,
                parametersJson = """{"prompt": "$prompt"}""",
                status = ActionStatus.BLOCKED_PERMISSIONS,
                executionResult = "BLOCKED: $reason"
            )
        )
    }

    private fun extractCoordinates(text: String): Pair<Float, Float> {
        val numbers = Regex("0\\.\\d+|1\\.0|\\d+").findAll(text).mapNotNull { it.value.toFloatOrNull() }.toList()
        return if (numbers.size >= 2) {
            val x = if (numbers[0] > 1.0f) numbers[0] / 1000f else numbers[0]
            val y = if (numbers[1] > 1.0f) numbers[1] / 2000f else numbers[1]
            Pair(x.coerceIn(0.1f, 0.9f), y.coerceIn(0.1f, 0.9f))
        } else {
            Pair(0.5f, 0.5f)
        }
    }

    private fun extractPath(text: String, defaultPath: String): String {
        val pathRegex = Regex("/[a-zA-Z0-9_./\\-]*")
        val match = pathRegex.find(text)
        return match?.value ?: defaultPath
    }

    private fun extractTitle(text: String, defaultTitle: String): String {
        val cleaned = text.replace("add", "", true).replace("calendar", "", true).replace("event", "", true).replace("create", "", true).trim()
        return if (cleaned.length > 3) cleaned else defaultTitle
    }
}
