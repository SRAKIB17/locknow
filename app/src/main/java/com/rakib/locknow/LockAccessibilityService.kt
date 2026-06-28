package com.rakib.locknow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class LockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!LockService.isLocked) return

        val packageName = event.packageName?.toString() ?: ""
        
        // 1. Allowed System Components (Phone, Dialer)
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

        val isAllowed = allowedPackages.any { packageName.contains(it) }

        // 2. Block Notification Shade & Power Menu (SystemUI)
        if (packageName == "com.android.systemui") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else {
                @Suppress("DEPRECATION")
                val closeDialog = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                sendBroadcast(closeDialog)
            }
            // Force return to home if they are interacting with SystemUI
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        // 3. Block Settings & Package Installer (Prevention of Uninstall/Force Stop)
        val restrictedPackages = listOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.vending" // Block Play Store to prevent uninstall/changes
        )

        if (restrictedPackages.any { packageName.contains(it) }) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            bringAppToFront()
        }

        // 4. Detailed Node Checking (Block 'Force Stop' button specifically if it appears)
        if (packageName.contains("settings")) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                checkAndBlockNodes(rootNode)
            }
        }

        // 5. Global Navigation Block (Home/Recents bypass)
        if (!isAllowed && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            bringAppToFront()
        }
    }

    private fun checkAndBlockNodes(node: AccessibilityNodeInfo) {
        // Look for buttons like "Force stop", "Uninstall", or "LockNow"
        val textToBlock = listOf("Force stop", "Uninstall", "Disable", "LockNow")
        for (text in textToBlock) {
            val nodes = node.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty()) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                checkAndBlockNodes(child)
            }
        }
    }

    private fun bringAppToFront() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    override fun onInterrupt() {}
}
