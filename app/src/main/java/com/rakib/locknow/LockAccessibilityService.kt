package com.rakib.locknow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!LockService.isLocked) return

        val packageName = event.packageName?.toString() ?: ""
        
        // 1. Aggressive redirection for ANY interaction outside the allowed set
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

        if (!isAllowed) {
            // Kick them back home or to our app instantly
            performGlobalAction(GLOBAL_ACTION_HOME)
            
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        // 2. Total Blocking of Settings and System UI (Power Menu, Shade)
        if (packageName.contains("settings") || packageName == "com.android.systemui") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {}
}
