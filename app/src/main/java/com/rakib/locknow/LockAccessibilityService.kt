package com.rakib.locknow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!LockService.isLocked) return

        val packageName = event.packageName?.toString() ?: ""
        
        // Allowed packages during lock: our app and phone-related apps
        val allowedPackages = listOf(
            "com.rakib.locknow",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.samsung.android.incallui",
            "com.android.contacts"
        )

        val isAllowed = allowedPackages.any { packageName.contains(it) } || 
                        packageName == getPackageName()

        // 1. Block access to EVERYTHING else (Settings, Notification shade, etc.)
        if (!isAllowed && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            
            // Immediately bring our app to front
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        // 2. Aggressively close the notification shade if it's being expanded
        if (packageName == "com.android.systemui") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else {
                try {
                    @Suppress("DEPRECATION")
                    val closeDialog = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    sendBroadcast(closeDialog)
                } catch (e: Exception) {}
            }
        }
    }

    override fun onInterrupt() {}
}
