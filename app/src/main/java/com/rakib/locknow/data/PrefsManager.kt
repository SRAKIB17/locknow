package com.rakib.locknow.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("LockNowPrefs", Context.MODE_PRIVATE)

    var emergencyName: String?
        get() = prefs.getString("EMERGENCY_NAME", "")
        set(value) = prefs.edit().putString("EMERGENCY_NAME", value).apply()

    var emergencyRelation: String?
        get() = prefs.getString("EMERGENCY_RELATION", "")
        set(value) = prefs.edit().putString("EMERGENCY_RELATION", value).apply()

    var emergencyPhone: String?
        get() = prefs.getString("EMERGENCY_PHONE", "")
        set(value) = prefs.edit().putString("EMERGENCY_PHONE", value).apply()

    var emergencyAltPhone: String?
        get() = prefs.getString("EMERGENCY_ALT_PHONE", "")
        set(value) = prefs.edit().putString("EMERGENCY_ALT_PHONE", value).apply()

    var emergencyNotes: String?
        get() = prefs.getString("EMERGENCY_NOTES", "")
        set(value) = prefs.edit().putString("EMERGENCY_NOTES", value).apply()

    var isLocked: Boolean
        get() = prefs.getBoolean("IS_LOCKED", false)
        set(value) = prefs.edit().putBoolean("IS_LOCKED", value).apply()

    var lockEndTime: Long
        get() = prefs.getLong("LOCK_END_TIME", 0L)
        set(value) = prefs.edit().putLong("LOCK_END_TIME", value).apply()
        
    var isEmergencyCallEnabled: Boolean
        get() = prefs.getBoolean("SETTING_EMERGENCY_CALL", true)
        set(value) = prefs.edit().putBoolean("SETTING_EMERGENCY_CALL", value).apply()

    var isQuotesEnabled: Boolean
        get() = prefs.getBoolean("SETTING_QUOTES", true)
        set(value) = prefs.edit().putBoolean("SETTING_QUOTES", value).apply()

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean("SETTING_VIBRATION", true)
        set(value) = prefs.edit().putBoolean("SETTING_VIBRATION", value).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean("SETTING_SOUND", true)
        set(value) = prefs.edit().putBoolean("SETTING_SOUND", value).apply()
}
