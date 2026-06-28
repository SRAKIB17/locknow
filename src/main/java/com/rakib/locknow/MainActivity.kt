package com.rakib.locknow

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
        Toast.makeText(this, "Enable LockNow Accessibility Service for full freeze", Toast.LENGTH_LONG).show()
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "This is required to prevent the app from being stopped.")
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
fun MainScreen(onStartLock: (Int) -> Unit, onEnableAdmin: () -> Unit, onEnableAccessibility: () -> Unit) {
    val context = LocalContext.current
    var durationText by remember { mutableStateOf("25") }
    
    val isAdminActive = remember {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)
        dpm.isAdminActive(admin)
    }

    val isAccessibilityEnabled = remember {
        val service = "${context.packageName}/${LockAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        enabled == 1
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            val duration = durationText.toIntOrNull() ?: 25
            onStartLock(duration)
        } else {
            Toast.makeText(context, "Permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                "LockNow",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                "Break the dopamine loop",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Focus Duration", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { if (it.length <= 3) durationText = it },
                        suffix = { Text("min") },
                        modifier = Modifier.width(150.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val perms = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    perms.add(Manifest.permission.READ_PHONE_STATE)
                    
                    val missing = perms.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing.toTypedArray())
                    } else {
                        onStartLock(durationText.toIntOrNull() ?: 25)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Activate Focus Mode", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            if (!isAdminActive) {
                TextButton(onClick = onEnableAdmin, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Enable Extra Protection")
                }
            }

            if (!isAccessibilityEnabled) {
                TextButton(onClick = onEnableAccessibility) {
                    Text("Enable Anti-Stop (Accessibility)")
                }
            }
        }
    }
}
