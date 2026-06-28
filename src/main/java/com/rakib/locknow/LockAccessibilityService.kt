package com.rakib.locknow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            
            // If the user tries to open settings or some other apps while lock is active
            // we can redirect them. 
            // We only do this if the LockService is running.
            if (isLockServiceRunning() && isRestrictedPackage(packageName)) {
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
            }
        }
    }

    private fun isRestrictedPackage(packageName: String?): Boolean {
        if (packageName == null) return false
        val restricted = listOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.android.systemui"
        )
        return restricted.any { packageName.contains(it) }
    }

    private fun isLockServiceRunning(): Boolean {
        return LockService.isLocked
    }

    override fun onInterrupt() {}
}
