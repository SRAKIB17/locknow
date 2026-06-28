package com.rakib.locknow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!LockService.isLocked) return

        val packageName = event.packageName?.toString() ?: ""
        
        // 1. Aggressive System UI & Notification Shade Blocking
        // If the user tries to pull down the shade or open the power menu
        if (packageName == "com.android.systemui") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        // 2. Strict Settings and Package Blocking
        val restrictedPackages = listOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.systemui"
        )

        val isRestricted = restrictedPackages.any { packageName.contains(it) }

        if (isRestricted) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            
            // Re-assert our app's presence
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        // 3. Prevent any other app interaction
        val allowedApps = listOf(
            "com.rakib.locknow",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.samsung.android.incallui"
        )

        if (!allowedApps.any { packageName.contains(it) }) {
            // If they managed to switch apps, send them back
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {}
}
