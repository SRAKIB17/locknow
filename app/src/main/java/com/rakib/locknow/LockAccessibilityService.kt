package com.rakib.locknow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!LockService.isLocked) return

        val packageName = event.packageName?.toString() ?: ""
        
        // Allowed packages during lock: our app and the phone dialer
        val allowedPackages = listOf(
            packageName, // our own package
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui"
        )

        val isAllowed = allowedPackages.any { it.contains(packageName) || packageName.contains(it) } || 
                        packageName == getPackageName()

        if (!isAllowed && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Block almost everything else
            performGlobalAction(GLOBAL_ACTION_HOME)
            
            // Show a reminder toast or just force our app to front
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        // Close notification shade if it appears
        if (packageName == "com.android.systemui" && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
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
