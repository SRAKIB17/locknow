package com.rakib.locknow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rakib.locknow.data.PrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            val prefs = PrefsManager(context)
            val endTime = prefs.lockEndTime
            val currentTime = System.currentTimeMillis()

            // If lockdown was active and timer hasn't finished
            if (prefs.isLocked && endTime > currentTime) {
                val remainingMillis = endTime - currentTime
                val durationMinutes = (remainingMillis / (1000 * 60)).toInt()
                
                val serviceIntent = Intent(context, LockService::class.java).apply {
                    putExtra("DURATION_MINUTES", if (durationMinutes > 0) durationMinutes else 1)
                }
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    // Log or handle error starting service after boot
                }
            }
        }
    }
}
