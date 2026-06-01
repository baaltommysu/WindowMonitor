package com.baaltommysu.windowmonitor

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

private const val Tag = "WindowMonitorService"

class WindowMonitorAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                Log.d(Tag, "window event package=${event.packageName} class=${event.className}")
            }
        }
    }

    override fun onInterrupt() {
        Log.d(Tag, "service interrupted")
    }
}
