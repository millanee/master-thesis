package com.millane.thesis.application.apps

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.millane.thesis.application.study.SessionManager

class AppForegroundService : AccessibilityService() {

    private lateinit var sessionManager: SessionManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ACCESSIBILITY", "SERVICE CONNECTED")
        sessionManager = SessionManager(applicationContext)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return

        Log.d(
            "ACCESSIBILITY",
            "eventType=${event.eventType}, package=$pkg"
        )

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        sessionManager.onForegroundAppChanged(pkg)
    }

    override fun onInterrupt() {
        Log.d("ACCESSIBILITY", "SERVICE INTERRUPTED")
    }
}