package com.rakib.locknow

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
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
import java.text.SimpleDateFormat
import java.util.*

class LockService : Service() {

    companion object {
        var isLocked = false
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var timer: CountDownTimer? = null
    private var isCallActive = false
    private val handler = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && isLocked) {
                wakeUpScreen()
            }
        }
    }

    private fun wakeUpScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
            PowerManager.ACQUIRE_CAUSES_WAKEUP, 
            "LockNow:WakeUp"
        )
        wakeLock.acquire(1000)
        wakeLock.release()
    }

    private val forceFocusRunnable = object : Runnable {
        override fun run() {
            if (isLocked && !isCallActive) {
                try {
                    // 1. Collapse Status Bar
                    val statusBarService = getSystemService("statusbar")
                    val statusBarClass = Class.forName("android.app.StatusBarManager")
                    val collapseMethod = statusBarClass.getMethod("collapsePanels")
                    collapseMethod.invoke(statusBarService)
                    
                    // 2. Close System Dialogs (Power Menu)
                    @Suppress("DEPRECATION")
                    val closeDialog = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    sendBroadcast(closeDialog)
                    
                    // 3. Keep Overlay on Top
                    if (overlayView?.parent == null) {
                        showOverlayViewAgain()
                    }
                    overlayView?.requestFocus()
                    
                    updateTimeAndDate()
                } catch (e: Exception) {}
                handler.postDelayed(this, 50)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupCallListener()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenReceiver, filter)
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
            val channel = NotificationChannel(channelId, "LockDown Active", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LockNow Protection")
            .setContentText("Device is strictly frozen for focus.")
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
            override fun dispatchKeyEvent(event: KeyEvent): Boolean = true 
            override fun onTouchEvent(event: MotionEvent?): Boolean = true
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (!hasWindowFocus && !isCallActive && isLocked) {
                    handler.postDelayed({ if (isLocked) this.requestFocus() }, 1)
                }
            }
        }

        LayoutInflater.from(this).inflate(R.layout.overlay_layout, wrapper, true)
        val timerTextView = wrapper.findViewById<TextView>(R.id.timerTextView)
        val callButton = wrapper.findViewById<Button>(R.id.callButton)

        val sharedPrefs = getSharedPreferences("LockNowPrefs", Context.MODE_PRIVATE)
        val emergencyNum = sharedPrefs.getString("EMERGENCY_NUMBER", "") ?: ""

        callButton?.setOnClickListener {
            val intent = if (emergencyNum.isNotEmpty()) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$emergencyNum"))
            } else {
                Intent(Intent.ACTION_DIAL)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED || emergencyNum.isEmpty()) {
                startActivity(intent)
            }
        }

        overlayView = wrapper
        windowManager?.addView(overlayView, params)

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

    private fun updateTimeAndDate() {
        val timeView = overlayView?.findViewById<TextView>(R.id.timeView)
        val dateView = overlayView?.findViewById<TextView>(R.id.dateView)
        
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
        
        timeView?.text = timeFormat.format(Date())
        dateView?.text = dateFormat.format(Date())
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
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
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
            windowManager?.removeView(overlayView)
        }
    }

    private fun showOverlayViewAgain() {
        if (overlayView != null && overlayView?.parent == null) {
            windowManager?.addView(overlayView, getOverlayParams())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isLocked = false
        handler.removeCallbacks(forceFocusRunnable)
        timer?.cancel()
        hideOverlay()
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
    }
}
