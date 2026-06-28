package com.rakib.locknow

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Locale

class LockService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var timer: CountDownTimer? = null
    private var isCallActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupCallListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationMinutes = intent?.getIntExtra("DURATION_MINUTES", 10) ?: 10
        val durationMillis = durationMinutes * 60 * 1000L

        showNotification()
        showOverlay(durationMillis)

        return START_NOT_STICKY
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
            val channel = NotificationChannel(
                channelId,
                "Lock Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LockNow Active")
            .setContentText("Your phone is locked for focus.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun showOverlay(durationMillis: Long) {
        val params = getOverlayParams()

        // Create a wrapper FrameLayout to handle key events
        val wrapper = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_HOME) {
                    return true // Consume the event
                }
                return super.dispatchKeyEvent(event)
            }
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, wrapper, true)
        val timerTextView = wrapper.findViewById<TextView>(R.id.timerTextView)
        val callButton = wrapper.findViewById<Button>(R.id.callButton)

        callButton?.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
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
                stopSelf()
            }
        }.start()
    }

    private fun getOverlayParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }
    }

    private fun hideOverlay() {
        if (overlayView?.parent != null) {
            windowManager?.removeView(overlayView)
        }
    }

    private fun showOverlayViewAgain() {
        if (overlayView != null && overlayView?.parent == null) {
            val params = getOverlayParams()
            try {
                windowManager?.addView(overlayView, params)
            } catch (e: Exception) {
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        hideOverlay()
    }
}
