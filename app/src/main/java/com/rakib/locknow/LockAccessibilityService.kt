package com.rakib.locknow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rakib.locknow.data.PrefsManager

class LockAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PrefsManager

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!prefs.isLocked) return

        val packageName = event.packageName?.toString() ?: ""
        
        // Allowed apps: our app, dialers, and common launchers (to avoid home screen loops)
        val allowedPackages = listOf(
            "com.rakib.locknow",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.samsung.android.incallui",
            "com.android.contacts",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home",
            "com.sec.android.app.launcher",
            "com.huawei.android.launcher"
        )

        val isAllowed = allowedPackages.any { packageName.contains(it) }

        // 1. Aggressively close Notification Shade & Power Menu
        if (packageName == "com.android.systemui") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else {
                @Suppress("DEPRECATION")
                val closeDialog = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                sendBroadcast(closeDialog)
            }
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        // 2. Block Settings, Package Installer, and Play Store
        val restrictedPackages = listOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.vending"
        )

        if (restrictedPackages.any { packageName.contains(it) }) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                if (shouldBlockNode(rootNode)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    bringAppToFront()
                }
            } else {
                performGlobalAction(GLOBAL_ACTION_HOME)
                bringAppToFront()
            }
        }

        // 3. Global Navigation Block for all other apps
        if (!isAllowed && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            bringAppToFront()
        }
    }

    private fun shouldBlockNode(node: AccessibilityNodeInfo): Boolean {
        val forbiddenTexts = listOf(
            "Force stop", "Uninstall", "Disable", "LockNow", 
            "ফোর্স স্টপ", "আনইনস্টল", "নিষ্ক্রিয়", "লকনাউ"
        )
        for (text in forbiddenTexts) {
            if (node.findAccessibilityNodeInfosByText(text).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun bringAppToFront() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    override fun onInterrupt() {}
}
