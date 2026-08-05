package com.example.engine

import com.example.data.db.LocalCalendarEventEntity
import com.example.data.repository.AgentBridgeRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalendarEngine(private val repository: AgentBridgeRepository) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    suspend fun getUpcomingEvents(): String {
        val events = repository.calendarEvents.first()
        if (events.isEmpty()) {
            return "No upcoming calendar events found."
        }
        val builder = StringBuilder("Upcoming Calendar Events (${events.size}):\n")
        events.forEachIndexed { index, event ->
            val startStr = dateFormat.format(Date(event.startTimeMillis))
            val endStr = dateFormat.format(Date(event.endTimeMillis))
            builder.append("${index + 1}. [${event.category}] ${event.title}\n")
            builder.append("   Time: $startStr to $endStr\n")
            builder.append("   Location: ${event.location}\n")
            if (event.description.isNotEmpty()) {
                builder.append("   Notes: ${event.description}\n")
            }
        }
        return builder.toString()
    }

    suspend fun createEvent(
        title: String,
        description: String,
        location: String,
        startTimeMillis: Long = System.currentTimeMillis() + 3600000,
        durationMinutes: Int = 60,
        category: String = "Agent Request"
    ): String {
        val endTimeMillis = startTimeMillis + (durationMinutes * 60000L)
        val event = LocalCalendarEventEntity(
            title = title,
            description = description,
            location = location,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            category = category
        )
        repository.addCalendarEvent(event)
        val startStr = dateFormat.format(Date(startTimeMillis))
        return "Successfully scheduled event '$title' on $startStr at $location."
    }
}
