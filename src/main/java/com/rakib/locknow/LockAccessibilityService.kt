package com.rakib.locknow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!LockService.isLocked) return

        val packageName = event.packageName?.toString() ?: ""
        
        // Block access to Settings, System UI (Power Menu, Status Bar), and Package Installer
        if (isRestrictedPackage(packageName)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        // Aggressively close the notification shade if it's opened
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (packageName == "com.android.systemui") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                } else {
                    // Older versions trick: send home or collapse via intent if possible
                    val closeDialog = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    sendBroadcast(closeDialog)
                }
            }
        }
    }

    private fun isRestrictedPackage(packageName: String): Boolean {
        val restricted = listOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.android.systemui",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.settings.Settings\$DeviceAdminSettingsActivity"
        )
        return restricted.any { packageName.contains(it) }
    }

    override fun onInterrupt() {}
}
