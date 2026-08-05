package com.example.engine

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class TouchSimulationEvent(
    val id: Long = System.currentTimeMillis(),
    val xRatio: Float, // 0.0 to 1.0 relative to screen width
    val yRatio: Float, // 0.0 to 1.0 relative to screen height
    val absoluteXDp: Float,
    val absoluteYDp: Float,
    val gestureType: String, // "TAP", "SWIPE_UP", "SWIPE_DOWN", "SWIPE_LEFT", "SWIPE_RIGHT", "LONG_PRESS"
    val targetElementName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class UiElementNode(
    val id: String,
    val text: String,
    val resourceId: String,
    val className: String,
    val bounds: String, // e.g., "[100,200][500,300]"
    val xRatioCenter: Float,
    val yRatioCenter: Float,
    val isClickable: Boolean = true
)

class ScreenTouchEngine {

    private val _touchEvents = MutableSharedFlow<TouchSimulationEvent>(extraBufferCapacity = 64)
    val touchEvents: SharedFlow<TouchSimulationEvent> = _touchEvents.asSharedFlow()

    // Simulated screen accessibility tree elements
    fun getMockScreenAccessibilityTree(): List<UiElementNode> {
        return listOf(
            UiElementNode("btn_home", "Home Dashboard", "com.blaze.app:id/nav_home", "android.widget.Button", "[30,1200][300,1350]", 0.15f, 0.90f),
            UiElementNode("btn_calendar", "Calendar Events", "com.blaze.app:id/nav_calendar", "android.widget.Button", "[320,1200][600,1350]", 0.45f, 0.90f),
            UiElementNode("btn_files", "File Navigator", "com.blaze.app:id/nav_files", "android.widget.Button", "[620,1200][900,1350]", 0.75f, 0.90f),
            UiElementNode("card_agent_status", "Active Bridge Node: Connected", "com.blaze.app:id/card_status", "android.view.ViewGroup", "[50,200][1000,450]", 0.50f, 0.25f),
            UiElementNode("btn_quick_scan", "Scan Media & Calendar", "com.blaze.app:id/btn_scan", "android.widget.Button", "[100,500][900,650]", 0.50f, 0.40f),
            UiElementNode("input_prompt", "Enter Agent Instruction...", "com.blaze.app:id/edit_prompt", "android.widget.EditText", "[50,700][900,820]", 0.50f, 0.52f),
            UiElementNode("fab_trigger", "Execute AI Action", "com.blaze.app:id/fab_execute", "com.google.android.material.floatingactionbutton.FloatingActionButton", "[800,950][980,1130]", 0.85f, 0.72f)
        )
    }

    suspend fun simulateTap(xRatio: Float, yRatio: Float, targetName: String? = null): TouchSimulationEvent {
        val event = TouchSimulationEvent(
            xRatio = xRatio.coerceIn(0f, 1f),
            yRatio = yRatio.coerceIn(0f, 1f),
            absoluteXDp = xRatio * 360f,
            absoluteYDp = yRatio * 800f,
            gestureType = "TAP",
            targetElementName = targetName
        )
        _touchEvents.emit(event)
        return event
    }

    suspend fun simulateSwipe(direction: String): TouchSimulationEvent {
        val (startX, startY) = when (direction.uppercase()) {
            "UP" -> Pair(0.5f, 0.8f)
            "DOWN" -> Pair(0.5f, 0.2f)
            "LEFT" -> Pair(0.8f, 0.5f)
            "RIGHT" -> Pair(0.2f, 0.5f)
            else -> Pair(0.5f, 0.5f)
        }
        val event = TouchSimulationEvent(
            xRatio = startX,
            yRatio = startY,
            absoluteXDp = startX * 360f,
            absoluteYDp = startY * 800f,
            gestureType = "SWIPE_${direction.uppercase()}"
        )
        _touchEvents.emit(event)
        return event
    }
}
