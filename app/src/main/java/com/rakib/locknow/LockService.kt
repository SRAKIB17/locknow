package com.rakib.locknow

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class LockService : Service() {

    companion object {
        var isLocked = false
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var timer: CountDownTimer? = null
    private var isCallActive = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val forceFocusRunnable = object : Runnable {
        override fun run() {
            if (isLocked && !isCallActive) {
                try {
                    // Force collapse status bar / notifications
                    val statusBarService = getSystemService("statusbar")
                    val statusBarClass = Class.forName("android.app.StatusBarManager")
                    val collapseMethod = statusBarClass.getMethod("collapsePanels")
                    collapseMethod.invoke(statusBarService)
                    
                    @Suppress("DEPRECATION")
                    val closeDialog = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    sendBroadcast(closeDialog)
                    
                    if (overlayView?.parent == null) {
                        showOverlayViewAgain()
                    }
                    overlayView?.requestFocus()
                } catch (e: Exception) {
                    // Fallback to simpler method if reflection fails
                    try {
                        @Suppress("DEPRECATION")
                        val closeDialog = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                        sendBroadcast(closeDialog)
                    } catch (ex: Exception) {}
                }
                
                // Absolute high-frequency heartbeat (30ms) for total domination
                handler.postDelayed(this, 30)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupCallListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationMinutes = intent?.getIntExtra("DURATION_MINUTES", 25) ?: 25
        val durationMillis = durationMinutes * 60 * 1000L
        
        val endTime = System.currentTimeMillis() + durationMillis
        getSharedPreferences("LockNowPrefs", Context.MODE_PRIVATE).edit()
            .putLong("LOCK_END_TIME", endTime)
            .apply()

        isLocked = true
        showNotification()
        showOverlay(durationMillis)
        
        handler.post(forceFocusRunnable)

        return START_STICKY
    }

    private fun setupCallListener() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> {
                        isCallActive = true
                        hideOverlay()
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        isCallActive = false
                        showOverlayViewAgain()
                    }
                }
            }
        }
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun showNotification() {
        val channelId = "lock_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "System Lock", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LockNow: ULTRA-RESTRICT MODE")
            .setContentText("Complete system freeze active. No bypass possible.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun showOverlay(durationMillis: Long) {
        val params = getOverlayParams()

        val wrapper = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                // Consume ALL key events (Home, Back, Recents, Power Menu Triggers, Volume)
                return true 
            }

            override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
                // Return true to consume all touches for the whole screen
                // but we need to let the child (call button) handle its own touches
                return false 
            }
            
            override fun onTouchEvent(event: MotionEvent?): Boolean {
                // Swallow any touches that fall through to the background
                return true
            }

            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (!hasWindowFocus && !isCallActive && isLocked) {
                    try {
                        @Suppress("DEPRECATION")
                        val closeDialog = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                        sendBroadcast(closeDialog)
                    } catch (e: Exception) {}
                    
                    handler.postDelayed({ 
                        try {
                            if (isLocked) this.requestFocus() 
                        } catch (e: Exception) {}
                    }, 1)
                }
            }
        }

        LayoutInflater.from(this).inflate(R.layout.overlay_layout, wrapper, true)
        val timerTextView = wrapper.findViewById<TextView>(R.id.timerTextView)
        val callButton = wrapper.findViewById<Button>(R.id.callButton)

        val sharedPrefs = getSharedPreferences("LockNowPrefs", Context.MODE_PRIVATE)
        val emergencyNum = sharedPrefs.getString("EMERGENCY_NUMBER", "") ?: ""

        if (emergencyNum.isNotEmpty()) {
            callButton?.text = "EMERGENCY: $emergencyNum"
        }

        callButton?.setOnClickListener {
            if (emergencyNum.isNotEmpty()) {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$emergencyNum"))
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(callIntent)
                } else {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNum"))
                    dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(dialIntent)
                }
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL)
                dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(dialIntent)
            }
        }

        overlayView = wrapper
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {}

        timer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                timerTextView?.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                isLocked = false
                stopSelf()
            }
        }.start()
    }

    private fun getOverlayParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or // Allow covering status bar
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.CENTER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return params
    }

    private fun hideOverlay() {
        if (overlayView?.parent != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {}
        }
    }

    private fun showOverlayViewAgain() {
        if (overlayView != null && overlayView?.parent == null) {
            val params = getOverlayParams()
            try {
                windowManager?.addView(overlayView, params)
            } catch (e: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isLocked = false
        handler.removeCallbacks(forceFocusRunnable)
        timer?.cancel()
        hideOverlay()
    }
}
