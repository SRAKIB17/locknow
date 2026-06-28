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
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rakib.locknow.ui.theme.LockNowTheme
import com.rakib.locknow.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

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
                MainContainer(
                    onEnableAdmin = { requestDeviceAdmin() },
                    onEnableAccessibility = { requestAccessibility() },
                    onEnableOverlay = { requestOverlay() },
                    onStartLock = { duration -> startLockService(duration) }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainContainer(
    viewModel: MainViewModel = viewModel(),
    onEnableAdmin: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit,
    onStartLock: (Int) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var showAboutSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("LockNow Pro", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { showAboutSheet = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "About")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = currentRoute == "home",
                    onClick = { 
                        if (currentRoute != "home") {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentRoute == "emergency",
                    onClick = { 
                        if (currentRoute != "emergency") {
                            navController.navigate("emergency") 
                        }
                    },
                    icon = { Icon(Icons.Default.ContactPhone, null) },
                    label = { Text("Emergency") }
                )
                NavigationBarItem(
                    selected = currentRoute == "settings",
                    onClick = { 
                        if (currentRoute != "settings") {
                            navController.navigate("settings") 
                        }
                    },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        },
        containerColor = Color(0xFF0F0C29)
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") { HomeScreen(viewModel, onEnableAdmin, onEnableAccessibility, onEnableOverlay, onStartLock) }
                composable("emergency") { EmergencyScreen(viewModel) }
                composable("settings") { SettingsScreen(viewModel) }
            }
            
            if (showAboutSheet) {
                AboutBottomSheet(onDismiss = { showAboutSheet = false })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onEnableAdmin: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit,
    onStartLock: (Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val remainingTime by viewModel.remainingTime.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    
    var isAdminActive by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayAllowed by remember { mutableStateOf(false) }
    var selectedDuration by remember { mutableStateOf(25) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(context, DeviceAdminReceiver::class.java)
                isAdminActive = dpm.isAdminActive(admin)
                isAccessibilityEnabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
                isOverlayAllowed = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault()).format(Date()),
            color = Color.Gray,
            fontSize = 14.sp
        )
        Text(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraLight
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.size(260.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            border = androidx.compose.foundation.BorderStroke(4.dp, if (isLocked) Color(0xFFE94560) else Color.Gray.copy(alpha = 0.3f))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isLocked) formatTime(remainingTime) else "${selectedDuration}:00",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        if (isLocked) "REMAINING" else "READY TO FOCUS",
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                        color = if (isLocked) Color(0xFFE94560) else Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        if (!isLocked) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf(15, 30, 60, 120, 180, 360, 720, 1440).forEach { mins ->
                    FilterChip(
                        selected = selectedDuration == mins,
                        onClick = { selectedDuration = mins },
                        label = { Text(if (mins < 60) "${mins}m" else "${mins/60}h") },
                        modifier = Modifier.padding(4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE94560),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (selectedDuration > 5) selectedDuration -= 5 }) {
                    Icon(Icons.Default.Remove, null, tint = Color.White)
                }
                Text("Custom: $selectedDuration min", color = Color.White, fontWeight = FontWeight.Bold)
                IconButton(onClick = { selectedDuration += 5 }) {
                    Icon(Icons.Default.Add, null, tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isAdminActive && isAccessibilityEnabled && isOverlayAllowed) {
                        onStartLock(selectedDuration)
                    } else {
                        Toast.makeText(context, "Please enable all protections", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
            ) {
                Icon(Icons.Default.Lock, null)
                Spacer(Modifier.width(12.dp))
                Text("START LOCKDOWN", fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        } else {
            Text(
                "Locked Mode Active\nNo escape until timer ends.",
                color = Color.Gray,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            SmallStatusItem("Admin", isAdminActive, onEnableAdmin)
            SmallStatusItem("Access", isAccessibilityEnabled, onEnableAccessibility)
            SmallStatusItem("Overlay", isOverlayAllowed, onEnableOverlay)
        }
    }
}

@Composable
fun SmallStatusItem(label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = !active) { onClick() }) {
        Icon(
            if (active) Icons.Default.CheckCircle else Icons.Default.Error,
            null,
            tint = if (active) Color.Green else Color.Red,
            modifier = Modifier.size(24.dp)
        )
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun EmergencyScreen(viewModel: MainViewModel) {
    val name by viewModel.emergencyName.collectAsState()
    val phone by viewModel.emergencyPhone.collectAsState()
    
    var editName by remember { mutableStateOf("") }
    var editRelation by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Emergency Contact", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Used only during active lockdown for emergency calls.", fontSize = 12.sp, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (name.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(50.dp).background(Color(0xFFE94560), CircleShape), contentAlignment = Alignment.Center) {
                        Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(phone, color = Color.Gray)
                    }
                    IconButton(onClick = { viewModel.deleteEmergencyContact() }) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                    }
                }
            }
        } else {
            Button(
                onClick = { showDialog = true },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Add Emergency Contact", color = Color.White)
            }
        }
    }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add Emergency Contact") },
            text = {
                Column {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Full Name") }, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(value = editRelation, onValueChange = { editRelation = it }, label = { Text("Relationship") }, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text("Phone Number") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotEmpty() && editPhone.isNotEmpty()) {
                        viewModel.saveEmergencyContact(editName, editRelation, editPhone, "", "")
                        showDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val isEmergencyEnabled by viewModel.isEmergencyCallEnabled.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingsToggle("Enable Emergency Call", isEmergencyEnabled) { viewModel.toggleEmergencyCall(it) }
        SettingsToggle("Motivational Quotes", true) {}
        SettingsToggle("Vibration", true) {}
        SettingsToggle("Sound", true) {}
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("Theme", color = Color.Gray, fontSize = 12.sp)
        Row(modifier = Modifier.padding(top = 8.dp)) {
            FilterChip(selected = true, onClick = {}, label = { Text("Dark") }, modifier = Modifier.padding(end = 8.dp))
            FilterChip(selected = false, onClick = {}, label = { Text("Light") }, modifier = Modifier.padding(end = 8.dp))
            FilterChip(selected = false, onClick = {}, label = { Text("System") })
        }
    }
}

@Composable
fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White)
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE94560))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBottomSheet(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF16213E)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Developer Info", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
            Spacer(Modifier.height(24.dp))
            
            Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFFE94560).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = Color(0xFFE94560))
            }
            
            Spacer(Modifier.height(16.dp))
            Text("RAKIBUL ISLAM", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("Android Developer", color = Color.Gray, fontSize = 14.sp)
            
            Spacer(Modifier.height(24.dp))
            
            AboutItem(Icons.Default.Phone, "+8801873989651") { 
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+8801873989651"))
                context.startActivity(intent)
            }
            AboutItem(Icons.Default.Link, "GitHub Profile") { uriHandler.openUri("https://github.com/srakib17") }
            AboutItem(Icons.Default.Facebook, "Facebook Profile") { uriHandler.openUri("https://www.facebook.com/srakib17") }
            
            Spacer(Modifier.height(32.dp))
            Text("Version 1.0.0 (Build 1)", color = Color.Gray, fontSize = 12.sp)
            Text("© 2026 Rakibul Islam", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun AboutItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFFE94560), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(text, color = Color.White, fontSize = 14.sp)
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
