package com.rakib.locknow

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rakib.locknow.ui.theme.LockNowTheme

class MainActivity : ComponentActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)

        enableEdgeToEdge()
        setContent {
            LockNowTheme {
                MainScreen(
                    onStartLock = { duration -> startLockService(duration) },
                    onEnableAdmin = { requestDeviceAdmin() },
                    onEnableAccessibility = { requestAccessibility() },
                    onEnableOverlay = { requestOverlay() }
                )
            }
        }
    }

    private fun requestOverlay() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun requestAccessibility() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects the app from being uninstalled during focus mode.")
        }
        startActivity(intent)
    }

    private fun startLockService(durationMinutes: Int) {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlay()
            return
        }

        val intent = Intent(this, LockService::class.java).apply {
            putExtra("DURATION_MINUTES", durationMinutes)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        moveTaskToBack(true)
    }
}

@Composable
fun MainScreen(
    onStartLock: (Int) -> Unit,
    onEnableAdmin: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var isAdminActive by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayAllowed by remember { mutableStateOf(false) }
    var isRooted by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(context, DeviceAdminReceiver::class.java)
                isAdminActive = dpm.isAdminActive(admin)
                val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
                isAccessibilityEnabled = enabled == 1
                isOverlayAllowed = Settings.canDrawOverlays(context)
                isRooted = SecurityUtils.isRooted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F0C29), Color(0xFF24243E))))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("LockNow Premium", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(32.dp))
            
            SecurityIndicator("Root Access", !isRooted)
            SecurityIndicator("Device Admin", isAdminActive)
            SecurityIndicator("Accessibility", isAccessibilityEnabled)
            SecurityIndicator("Overlay", isOverlayAllowed)

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    if (isAdminActive && isAccessibilityEnabled && isOverlayAllowed) {
                        onStartLock(25)
                    } else {
                        Toast.makeText(context, "Please enable all protections", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
            ) {
                Text("ACTIVATE TOTAL LOCKDOWN", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (!isAdminActive) TextButton(onClick = onEnableAdmin) { Text("Enable Device Admin", color = Color.White) }
            if (!isAccessibilityEnabled) TextButton(onClick = onEnableAccessibility) { Text("Enable Accessibility", color = Color.White) }
            if (!isOverlayAllowed) TextButton(onClick = onEnableOverlay) { Text("Enable Overlay", color = Color.White) }
        }
    }
}

@Composable
fun SecurityIndicator(label: String, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White)
        Icon(
            if (isActive) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (isActive) Color.Green else Color.Red
        )
    }
}
