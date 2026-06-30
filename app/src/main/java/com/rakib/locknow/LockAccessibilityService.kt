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
        val className = event.className?.toString() ?: ""
        
        // Allowed apps: our app, dialers, and common launchers
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

        // 1. Aggressively close Notification Shade & Power Menu & Accessibility Shortcuts
        if (packageName == "com.android.systemui") {
            // Dismiss accessibility shortcut dialogs often found in SystemUI
            if (className.contains("AccessibilityShortcut") || className.contains("AccessibilityMenu")) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else {
                @Suppress("DEPRECATION")
                val closeDialog = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                sendBroadcast(closeDialog)
            }
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        // 2. Block Settings, Accessibility Settings & Shortcut Chooser
        val restrictedPackages = listOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.vending"
        )

        if (restrictedPackages.any { packageName.contains(it) }) {
            // Detect accessibility shortcut chooser activity
            if (className.contains("AccessibilityShortcutChooserActivity") || 
                className.contains("AccessibilitySettings")) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                bringAppToFront()
                return
            }

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
            "Force stop", "Uninstall", "Disable", "LockNow", "Accessibility shortcut",
            "ফোর্স স্টপ", "আনইনস্টল", "নিষ্ক্রিয়", "লকনাউ", "অ্যাক্সেসিবিলিটি শর্টকাট"
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
