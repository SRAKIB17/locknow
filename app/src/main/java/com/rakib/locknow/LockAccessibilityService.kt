package com.rakib.locknow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!LockService.isLocked) return

        val packageName = event.packageName?.toString() ?: ""
        
        // 1. Allowed apps (Self and Dialer)
        val allowedApps = listOf(
            "com.rakib.locknow",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.samsung.android.incallui",
            "com.android.contacts"
        )

        val isAllowed = allowedApps.any { packageName.contains(it) }

        // 2. Absolute Redirection: If anything else is opened, immediately home and restart our app
        if (!isAllowed) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            
            // Dismiss notification shade if opened via gesture
            if (packageName == "com.android.systemui") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                }
            }

            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        // 3. Extra Protection against Settings & Status Bar
        if (packageName.contains("settings") || packageName == "com.android.systemui") {
            performGlobalAction(GLOBAL_ACTION_HOME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }
        }
    }

    override fun onInterrupt() {}
}
