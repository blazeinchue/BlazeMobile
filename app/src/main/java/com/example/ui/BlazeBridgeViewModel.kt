package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AppNavigationTab {
    CHAT,
    SETTINGS
}

enum class MessageSender {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class BlazeUiState(
    val currentTab: AppNavigationTab = AppNavigationTab.CHAT,
    val isDarkMode: Boolean = false,
    val selectedProvider: String = "Gemini",
    val selectedModel: String = "Gemini 3.5 Flash",
    val userName: String = "Blaze User",
    val isGeneratingResponse: Boolean = false,
    val chatMessages: List<ChatMessage> = listOf(
        ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = "Hello! I am your AI Assistant powered by Blaze. How can I help you today?"
        )
    )
)

class BlazeBridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("blaze_app_preferences", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(BlazeUiState())
    val uiState: StateFlow<BlazeUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        val isDark = prefs.getBoolean("is_dark_mode", false)
        val selectedProvider = prefs.getString("selected_provider", "Gemini") ?: "Gemini"
        val selectedModel = prefs.getString("selected_model", "Gemini 3.5 Flash") ?: "Gemini 3.5 Flash"
        val userName = prefs.getString("user_name", "Blaze User") ?: "Blaze User"
        val savedChatJson = prefs.getString("chat_messages_json", null)

        val chatList = if (!savedChatJson.isNullOrEmpty()) {
            deserializeChatMessages(savedChatJson)
        } else null

        _uiState.update {
            it.copy(
                isDarkMode = isDark,
                selectedProvider = selectedProvider,
                selectedModel = selectedModel,
                userName = userName,
                chatMessages = chatList ?: it.chatMessages
            )
        }
    }

    fun selectTab(tab: AppNavigationTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun toggleThemeMode() {
        val newMode = !_uiState.value.isDarkMode
        setThemeMode(newMode)
    }

    fun setThemeMode(isDark: Boolean) {
        _uiState.update { it.copy(isDarkMode = isDark) }
        prefs.edit().putBoolean("is_dark_mode", isDark).apply()
    }

    fun setSelectedProvider(provider: String) {
        // Automatically default model when provider changes if model doesn't match
        val defaultModel = when (provider) {
            "Qwen" -> "Qwen 2.5 Max"
            "Anthropic" -> "Claude 3.5 Sonnet"
            "OpenAI" -> "GPT-4o"
            "Groq" -> "Llama 3.3 70B"
            else -> "Gemini 3.5 Flash"
        }
        _uiState.update { 
            it.copy(
                selectedProvider = provider,
                selectedModel = defaultModel
            ) 
        }
        prefs.edit()
            .putString("selected_provider", provider)
            .putString("selected_model", defaultModel)
            .apply()
    }

    fun setSelectedModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
        prefs.edit().putString("selected_model", model).apply()
    }

    fun setUserName(name: String) {
        _uiState.update { it.copy(userName = name) }
        prefs.edit().putString("user_name", name).apply()
    }

    fun clearChatHistory() {
        val clearedList = listOf(
            ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Chat history cleared. How can I help you?"
            )
        )
        _uiState.update { it.copy(chatMessages = clearedList) }
        saveChatMessages(clearedList)
    }

    fun sendMessage(userPrompt: String) {
        val trimmed = userPrompt.trim()
        if (trimmed.isEmpty()) return

        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = trimmed
        )

        val updatedMessagesWithUser = _uiState.value.chatMessages + userMessage
        _uiState.update {
            it.copy(
                chatMessages = updatedMessagesWithUser,
                isGeneratingResponse = true
            )
        }
        saveChatMessages(updatedMessagesWithUser)

        viewModelScope.launch {
            delay(1000) // Simulate streaming response generation

            val responseText = generateAssistantResponse(trimmed, _uiState.value.selectedModel)

            val assistantMessage = ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = responseText
            )

            val finalMessages = _uiState.value.chatMessages + assistantMessage
            _uiState.update {
                it.copy(
                    chatMessages = finalMessages,
                    isGeneratingResponse = false
                )
            }
            saveChatMessages(finalMessages)
        }
    }

    private fun saveChatMessages(messages: List<ChatMessage>) {
        try {
            val jsonArray = JSONArray()
            messages.forEach { msg ->
                val jsonObj = JSONObject().apply {
                    put("id", msg.id)
                    put("sender", msg.sender.name)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                }
                jsonArray.put(jsonObj)
            }
            prefs.edit().putString("chat_messages_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun deserializeChatMessages(jsonStr: String): List<ChatMessage>? {
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ChatMessage(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        sender = MessageSender.valueOf(obj.getString("sender")),
                        text = obj.getString("text"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            if (list.isNotEmpty()) list else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun generateAssistantResponse(prompt: String, model: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hello there! How can I assist you with your tasks today?"

            lower.contains("who are you") || lower.contains("what is this") ->
                "I am Blaze, your fast and intelligent mobile AI Assistant running on $model."

            lower.contains("theme") || lower.contains("dark") || lower.contains("light") ->
                "You can switch between Dark Mode and Light Mode anytime in the Settings tab or using the theme toggle in the top bar!"

            lower.contains("time") || lower.contains("date") ->
                "The current device time is ${java.text.SimpleDateFormat("HH:mm, EEE dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())}."

            lower.contains("code") || lower.contains("kotlin") ->
                "Here is a quick Kotlin Compose example:\n\n```kotlin\n@Composable\nfun Greeting(name: String) {\n    Text(\"Hello \$name!\")\n}\n```"

            else ->
                "That's a great question regarding \"$prompt\". Using $model, I can help you summarize text, draft notes, answer queries, or organize your daily tasks."
        }
    }
}

