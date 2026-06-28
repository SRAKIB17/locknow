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
import android.media.RingtoneManager
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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rakib.locknow.data.PrefsManager
import com.rakib.locknow.utils.LocaleHelper
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
    private lateinit var prefs: PrefsManager
    private var totalDurationMillis: Long = 0

    // Cached views for performance
    private var timerTextView: TextView? = null
    private var quoteTextView: TextView? = null
    private var timeView: TextView? = null
    private var dateView: TextView? = null
    private var circularProgress: ProgressBar? = null

    private val quoteRunnable = object : Runnable {
        override fun run() {
            if (isLocked && prefs.isQuotesEnabled) {
                val quotes = resources.getStringArray(R.array.motivational_quotes)
                quoteTextView?.animate()?.alpha(0f)?.setDuration(500)?.withEndAction {
                    if (quotes.isNotEmpty()) {
                        quoteTextView?.text = quotes.random()
                    }
                    quoteTextView?.animate()?.alpha(1f)?.setDuration(500)?.start()
                }?.start()
                handler.postDelayed(this, 3000)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && isLocked) {
                wakeUpScreen()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = PrefsManager(newBase)
        super.attachBaseContext(LocaleHelper.wrap(newBase, prefs.language))
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
                    updateDateTime()
                } catch (e: Exception) {}
                handler.postDelayed(this, 30)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupCallListener()
        
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationMinutes = intent?.getIntExtra("DURATION_MINUTES", 25) ?: 25
        totalDurationMillis = durationMinutes * 60 * 1000L
        
        val endTime = System.currentTimeMillis() + totalDurationMillis
        prefs.lockEndTime = endTime
        prefs.isLocked = true
        isLocked = true

        playAlert()
        showOverlay(totalDurationMillis)
        handler.post(forceFocusRunnable)
        handler.post(quoteRunnable)
        showNotification()

        return START_STICKY
    }

    private fun playAlert() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }

        try {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, notification)
            r.play()
        } catch (e: Exception) {}
    }

    private fun setupCallListener() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        @Suppress("DEPRECATION")
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> {
                        isCallActive = true
                        removeOverlay()
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        isCallActive = false
                        showOverlayViewAgain()
                    }
                }
            }
        }
        @Suppress("DEPRECATION")
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun showNotification() {
        val channelId = "lock_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, getString(R.string.locked_desc), NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.locked_desc))
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
                    try {
                        @Suppress("DEPRECATION")
                        val closeDialog = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                        sendBroadcast(closeDialog)
                    } catch (e: Exception) {}
                    handler.postDelayed({ if (isLocked) this.requestFocus() }, 1)
                }
            }
        }

        LayoutInflater.from(this).inflate(R.layout.overlay_layout, wrapper, true)
        
        // Cache views
        timerTextView = wrapper.findViewById(R.id.timerTextView)
        quoteTextView = wrapper.findViewById(R.id.quoteTextView)
        timeView = wrapper.findViewById(R.id.timeView)
        dateView = wrapper.findViewById(R.id.dateView)
        circularProgress = wrapper.findViewById(R.id.circularProgress)
        
        val callButton = wrapper.findViewById<Button>(R.id.callButton)
        val remainingLabel = wrapper.findViewById<TextView>(R.id.remainingLabel)

        remainingLabel?.text = getString(R.string.time_remaining)

        val emergencyNum = prefs.emergencyPhone ?: ""
        if (emergencyNum.isNotEmpty() && prefs.isEmergencyCallEnabled) {
            callButton?.text = "${getString(R.string.emergency_title)}: $emergencyNum"
            callButton?.visibility = View.VISIBLE
        } else {
            callButton?.text = getString(R.string.emergency_title)
            callButton?.visibility = if (prefs.isEmergencyCallEnabled) View.VISIBLE else View.GONE
        }

        if (prefs.isQuotesEnabled) {
            val quotes = resources.getStringArray(R.array.motivational_quotes)
            if (quotes.isNotEmpty()) {
                quoteTextView?.text = quotes.random()
            }
            quoteTextView?.visibility = View.VISIBLE
        } else {
            quoteTextView?.visibility = View.GONE
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
            }
        }

        overlayView = wrapper
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {}

        timer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val h = (millisUntilFinished / 1000) / 3600
                val m = ((millisUntilFinished / 1000) % 3600) / 60
                val s = (millisUntilFinished / 1000) % 60
                val timeStr = if (h > 0) 
                    String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s) 
                else 
                    String.format(Locale.getDefault(), "%02d:%02d", m, s)
                timerTextView?.text = timeStr
                
                val progress = ((millisUntilFinished.toFloat() / totalDurationMillis) * 1000).toInt()
                circularProgress?.progress = progress
            }
            override fun onFinish() {
                isLocked = false
                prefs.isLocked = false
                playAlert()
                stopSelf()
            }
        }.start()
    }

    private fun updateDateTime() {
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

    private fun removeOverlay() {
        if (overlayView?.parent != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {}
        }
    }

    private fun showOverlayViewAgain() {
        if (overlayView != null && overlayView?.parent == null) {
            try {
                windowManager?.addView(overlayView, getOverlayParams())
            } catch (e: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isLocked = false
        prefs.isLocked = false
        handler.removeCallbacks(forceFocusRunnable)
        handler.removeCallbacks(quoteRunnable)
        timer?.cancel()
        removeOverlay()
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
    }
}
