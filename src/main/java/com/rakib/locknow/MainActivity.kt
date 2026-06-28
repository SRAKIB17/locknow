package com.rakib.locknow

import android.Manifest
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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rakib.locknow.ui.theme.LockNowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LockNowTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LockScreenConfig(
                        modifier = Modifier.padding(innerPadding),
                        onStartLock = { durationMinutes ->
                            startLockService(durationMinutes)
                        }
                    )
                }
            }
        }
    }

    private fun startLockService(durationMinutes: Int) {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_SHORT).show()
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
    }
}

@Composable
fun LockScreenConfig(
    modifier: Modifier = Modifier,
    onStartLock: (Int) -> Unit
) {
    val context = LocalContext.current
    var durationText by remember { mutableStateOf("10") }
    
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_PHONE_STATE)
    } else {
        arrayOf(Manifest.permission.READ_PHONE_STATE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            val duration = durationText.toIntOrNull() ?: 10
            onStartLock(duration)
        } else {
            Toast.makeText(context, "All permissions are required for the lock service", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("LockNow - Focus Mode", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        TextField(
            value = durationText,
            onValueChange = { durationText = it },
            label = { Text("Duration (minutes)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                val missingPermissions = permissionsToRequest.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }

                if (missingPermissions.isNotEmpty()) {
                    permissionLauncher.launch(missingPermissions.toTypedArray())
                } else {
                    val duration = durationText.toIntOrNull() ?: 10
                    onStartLock(duration)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Lock")
        }
    }
}
