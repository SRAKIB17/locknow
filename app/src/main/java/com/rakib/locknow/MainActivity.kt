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
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
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
import com.rakib.locknow.data.PrefsManager
import com.rakib.locknow.ui.theme.*
import com.rakib.locknow.utils.LocaleHelper
import com.rakib.locknow.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun attachBaseContext(newBase: Context) {
        val prefs = PrefsManager(newBase)
        val lang = prefs.language
        super.attachBaseContext(LocaleHelper.wrap(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)

        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            
            val isDarkTheme = when(themeMode) {
                0 -> true
                1 -> false
                else -> isSystemInDarkTheme()
            }

            LockNowTheme(darkTheme = isDarkTheme) {
                MainContainer(
                    viewModel = viewModel,
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
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects the app from being uninstalled.")
        }
        startActivity(intent)
    }

    private fun startLockService(durationMinutes: Int) {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlay()
            return
        }

        // Request to disable battery optimization for stability
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                try {
                    startActivity(intent)
                } catch (e: Exception) {}
            }
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
fun MainContainer(
    viewModel: MainViewModel,
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
                title = { 
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp)) 
                },
                actions = {
                    IconButton(onClick = { showAboutSheet = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "About", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                listOf(
                    Triple("home", Icons.Default.Timer, R.string.nav_focus),
                    Triple("emergency", Icons.Default.HealthAndSafety, R.string.nav_safety),
                    Triple("settings", Icons.Default.Tune, R.string.nav_settings)
                ).forEach { (route, icon, labelRes) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = { if (currentRoute != route) navController.navigate(route) { popUpTo("home") } },
                        icon = { Icon(icon, null) },
                        label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val isDark = MaterialTheme.colorScheme.background == DarkBackground
        val backgroundBrush = Brush.verticalGradient(if (isDark) DarkGradient else LightGradient)

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
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
    val quote by viewModel.currentQuote.collectAsState()
    
    var isAdminActive by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayAllowed by remember { mutableStateOf(false) }
    
    var selectedDuration by remember { mutableIntStateOf(25) }

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
        Spacer(modifier = Modifier.height(8.dp))
        
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(32.dp)),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, null, modifier = Modifier.size(16.dp), tint = Success)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isAdminActive && isAccessibilityEnabled && isOverlayAllowed) stringResource(R.string.status_protected) else stringResource(R.string.status_required),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isAdminActive && isAccessibilityEnabled && isOverlayAllowed) Success else Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Box(contentAlignment = Alignment.Center) {
            val infiniteTransition = rememberInfiniteTransition(label = "")
            val glowScale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ), label = ""
            )

            if (isLocked) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f * glowScale), CircleShape)
                )
            }

            CircularProgressIndicator(
                progress = { if (isLocked) (remainingTime.toFloat() / (selectedDuration * 60000f)).coerceIn(0f, 1f) else 1f },
                modifier = Modifier.size(260.dp),
                color = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                strokeWidth = 8.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (isLocked) formatTime(remainingTime) else "$selectedDuration:00",
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onBackground)
                )
                Text(
                    if (isLocked) stringResource(R.string.time_remaining) else stringResource(R.string.ready_to_focus),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Box(modifier = Modifier.height(60.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = quote,
                transitionSpec = {
                    fadeIn(animationSpec = tween(1000)) togetherWith fadeOut(animationSpec = tween(1000))
                }, label = ""
            ) { targetQuote ->
                if (targetQuote.isNotEmpty()) {
                    Text(
                        "\"$targetQuote\"",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        if (!isLocked) {
            Text(stringResource(R.string.quick_presets), modifier = Modifier.align(Alignment.Start), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15, 25, 30, 60, 120, 180).forEach { mins ->
                    SuggestionChip(
                        onClick = { selectedDuration = mins },
                        label = { Text("${mins}m") },
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = if (selectedDuration == mins) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                        ),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (selectedDuration == mins) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.03f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.custom_duration), modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onBackground)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (selectedDuration > 1) selectedDuration -= 1 }) {
                            Icon(Icons.Default.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Text("$selectedDuration", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        IconButton(onClick = { if (selectedDuration < 1440) selectedDuration += 1 }) {
                            Icon(Icons.Default.AddCircleOutline, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isAdminActive && isAccessibilityEnabled && isOverlayAllowed) {
                        onStartLock(selectedDuration)
                    } else {
                        Toast.makeText(context, "Setup security layer first!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Default.Lock, null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.start_lockdown), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
            }
        } else {
            Text(
                stringResource(R.string.locked_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        if (!isAdminActive || !isAccessibilityEnabled || !isOverlayAllowed) {
            Text(stringResource(R.string.security_status), modifier = Modifier.align(Alignment.Start), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            SetupCard(stringResource(R.string.overlay_title), stringResource(R.string.overlay_desc), isOverlayAllowed, onEnableOverlay)
            SetupCard(stringResource(R.string.admin_title), stringResource(R.string.admin_desc), isAdminActive, onEnableAdmin)
            SetupCard(stringResource(R.string.access_title), stringResource(R.string.access_desc), isAccessibilityEnabled, onEnableAccessibility)
        }
    }
}

@Composable
fun SetupCard(title: String, desc: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !active) { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Success.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(if (active) Success.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (active) Icons.Default.Check else Icons.Default.PriorityHigh, null, tint = if (active) Success else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            if (!active) Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
        }
    }
}

@Composable
fun EmergencyScreen(viewModel: MainViewModel) {
    val name by viewModel.emergencyName.collectAsState()
    val phone by viewModel.emergencyPhone.collectAsState()
    val relation by viewModel.emergencyRelation.collectAsState()
    
    var editName by remember { mutableStateOf(name) }
    var editRelation by remember { mutableStateOf(relation) }
    var editPhone by remember { mutableStateOf(phone) }
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.emergency_title), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.emergency_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (name.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            ) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                        Text("$relation • $phone", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    IconButton(onClick = { viewModel.deleteEmergencyContact() }) {
                        Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.03f))
                    .clickable { 
                        editName = ""
                        editRelation = ""
                        editPhone = ""
                        showDialog = true 
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.add_contact), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
            }
        }
    }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.configure_contact)) },
            text = {
                Column {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text(stringResource(R.string.full_name)) }, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(value = editRelation, onValueChange = { editRelation = it }, label = { Text(stringResource(R.string.relationship)) }, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text(stringResource(R.string.phone_number)) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editName.isNotEmpty() && editPhone.isNotEmpty()) {
                        viewModel.saveEmergencyContact(editName, editRelation, editPhone, "", "")
                        showDialog = false
                    }
                }) { Text(stringResource(R.string.save_settings)) }
            }
        )
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isEmergencyEnabled by viewModel.isEmergencyCallEnabled.collectAsState()
    val isQuotesEnabled by viewModel.isQuotesEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.app_prefs), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                SettingsToggleItem(Icons.AutoMirrored.Filled.PhoneCallback, stringResource(R.string.enable_emergency_call), isEmergencyEnabled) { viewModel.toggleEmergencyCall(it) }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                SettingsToggleItem(Icons.Default.FormatQuote, stringResource(R.string.show_quotes), isQuotesEnabled) { viewModel.toggleQuotes(it) }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.interface_theme), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
        ) {
            Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(stringResource(R.string.theme_dark), stringResource(R.string.theme_light), stringResource(R.string.theme_system)).forEachIndexed { index, label ->
                    FilterChip(
                        selected = themeMode == index,
                        onClick = { viewModel.setThemeMode(index) },
                        label = { Text(label) },
                        modifier = Modifier.padding(4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
        ) {
            Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("en" to stringResource(R.string.lang_en), "bn" to stringResource(R.string.lang_bn)).forEach { (code, label) ->
                    FilterChip(
                        selected = language == code,
                        onClick = { 
                            viewModel.setLanguage(code)
                            // Activity needs restart to apply context wrapper change
                            (context as? MainActivity)?.recreate()
                        },
                        label = { Text(label) },
                        modifier = Modifier.padding(4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsToggleItem(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(label, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBottomSheet(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 16.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(modifier = Modifier.size(100.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.DeveloperMode, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            Text("RAKIBUL ISLAM", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black))
            Text(stringResource(R.string.lead_architect), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AboutActionCard(Icons.Default.Phone, "Call", Modifier.weight(1f)) { 
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+8801873989651")))
                }
                AboutActionCard(Icons.Default.Link, "GitHub", Modifier.weight(1f)) { uriHandler.openUri("https://github.com/srakib17") }
                AboutActionCard(Icons.Default.Facebook, "Social", Modifier.weight(1f)) { uriHandler.openUri("https://www.facebook.com/srakib17") }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            Spacer(Modifier.height(16.dp))
            Text("${stringResource(R.string.version)} 1.0.0 (Build 126)", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.copyright), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun AboutActionCard(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

private fun formatTime(millis: Long): String {
    val h = millis / 3600000
    val m = (millis % 3600000) / 60000
    val s = (millis % 60000) / 1000
    return if (h > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}
