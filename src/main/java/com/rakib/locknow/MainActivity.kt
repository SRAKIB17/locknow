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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                    onEnableAccessibility = { requestAccessibility() }
                )
            }
        }
    }

    private fun requestAccessibility() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Find 'LockNow' and turn it ON", Toast.LENGTH_LONG).show()
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
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStartLock: (Int) -> Unit,
    onEnableAdmin: () -> Unit,
    onEnableAccessibility: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sharedPrefs = remember { context.getSharedPreferences("LockNowPrefs", Context.MODE_PRIVATE) }
    
    var durationMinutes by remember { mutableStateOf(25) }
    var isAdminActive by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayAllowed by remember { mutableStateOf(false) }
    
    var emergencyNumber by remember { 
        mutableStateOf(sharedPrefs.getString("EMERGENCY_NUMBER", "") ?: "") 
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(context, DeviceAdminReceiver::class.java)
                isAdminActive = dpm.isAdminActive(admin)
                val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
                isAccessibilityEnabled = enabled == 1
                isOverlayAllowed = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            // Logo
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = Color(0xFFE94560).copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE94560))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(40.dp), tint = Color(0xFFE94560))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("LockNow Pro", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("MAXIMUM FOCUS PROTECTION", fontSize = 12.sp, color = Color(0xFFE94560), fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(32.dp))

            // Timer Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SET FOCUS DURATION", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { if (durationMinutes > 5) durationMinutes -= 5 }) {
                            Text("-", fontSize = 32.sp, color = Color.White)
                        }
                        Text("$durationMinutes", fontSize = 56.sp, fontWeight = FontWeight.Light, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp))
                        TextButton(onClick = { if (durationMinutes < 180) durationMinutes += 5 }) {
                            Text("+", fontSize = 32.sp, color = Color.White)
                        }
                    }
                    Text("MINUTES", color = Color.Gray, letterSpacing = 2.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Emergency Call Setting
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("EMERGENCY CONTACT", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = emergencyNumber,
                        onValueChange = { 
                            emergencyNumber = it
                            sharedPrefs.edit().putString("EMERGENCY_NUMBER", it).apply()
                        },
                        placeholder = { Text("Enter phone number", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE94560),
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Phone, null, tint = Color.Gray) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security Configuration
            Text("SECURITY SHIELD STATUS", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start, color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))

            PermissionItem("Screen Overlay", "Locks your device interface", isOverlayAllowed, Icons.Default.Layers, {})
            PermissionItem("Device Admin", "Prevents bypass and removal", isAdminActive, Icons.Default.AdminPanelSettings, onEnableAdmin)
            PermissionItem("Accessibility Service", "Aggressive anti-stop system", isAccessibilityEnabled, Icons.Default.Security, onEnableAccessibility)

            Spacer(modifier = Modifier.height(32.dp))

            // The Initiate Button
            Button(
                onClick = {
                    if (!isOverlayAllowed || !isAdminActive || !isAccessibilityEnabled) {
                        Toast.makeText(context, "All security layers must be active!", Toast.LENGTH_SHORT).show()
                    } else {
                        onStartLock(durationMinutes)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
            ) {
                Text("INITIATE DEEP FOCUS", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            Text("WARNING: System will be strictly locked until timer ends.", color = Color.Gray.copy(alpha = 0.6f), fontSize = 10.sp)
        }
    }
}

@Composable
fun PermissionItem(title: String, desc: String, isGranted: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f))
            .clickable(enabled = !isGranted) { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).background(if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (isGranted) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(desc, color = Color.Gray, fontSize = 10.sp)
        }
        if (isGranted) Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
        else Icon(Icons.Default.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}
