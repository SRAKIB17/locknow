package com.rakib.locknow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("LockNowPrefs", Context.MODE_PRIVATE)
            val endTime = prefs.getLong("LOCK_END_TIME", 0)
            val currentTime = System.currentTimeMillis()

            if (endTime > currentTime) {
                val durationMinutes = ((endTime - currentTime) / (1000 * 60)).toInt()
                val serviceIntent = Intent(context, LockService::class.java).apply {
                    putExtra("DURATION_MINUTES", durationMinutes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
