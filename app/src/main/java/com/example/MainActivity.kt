package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.annotations.IconFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import com.example.data.Contact
import com.example.data.Helpline
import com.example.data.HelplinesAndTips
import com.example.data.SafetyTip
import com.example.ui.SafetyViewModel
import com.example.ui.SmsLog
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    private val viewModel: SafetyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            org.maplibre.android.MapLibre.getInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SafeHerApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.registerShakeListener()
    }

    override fun onPause() {
        super.onPause()
        viewModel.unregisterShakeListener()
    }
}

enum class SafeHerTab(val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    DASHBOARD("Home", Icons.Outlined.Shield, Icons.Filled.Shield),
    CONTACTS("Contacts", Icons.Outlined.People, Icons.Filled.People),
    HELPLINES("Helplines", Icons.Outlined.ContactEmergency, Icons.Filled.ContactEmergency),
    MAP("Safe Map", Icons.Outlined.Map, Icons.Filled.Map)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeHerApp(viewModel: SafetyViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(SafeHerTab.DASHBOARD) }

    // Track active permissions
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasSmsPermission = results[Manifest.permission.SEND_SMS] ?: hasSmsPermission
        hasLocationPermission = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: hasLocationPermission
        
        if (hasSmsPermission && hasLocationPermission) {
            Toast.makeText(context, "Permissions Granted! Protection Active.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Please grant all permissions for complete safety automation.", Toast.LENGTH_LONG).show()
        }
    }

    val requestAllPermissions = {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            text = "SAFESPACE MODE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Safety Pulse",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        val isSosActive by viewModel.isSosTriggered.collectAsState()
                        if (isSosActive) {
                            AssistChip(
                                onClick = { viewModel.resetSOS() },
                                label = { Text("SOS ACTIVE", fontWeight = FontWeight.Bold, color = CrimsonAlert) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = "Active Warning",
                                        tint = CrimsonAlert,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = CrimsonAlert.copy(alpha = 0.15f),
                                    labelColor = CrimsonAlert
                                )
                            )
                        } else {
                            val isShakeOn by viewModel.isShakeDetectionEnabled.collectAsState()
                            Badge(
                                containerColor = if (isShakeOn) SafeEmerald.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isShakeOn) SafeEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    text = if (isShakeOn) "SHAKE ACTIVE" else "SHAKE MUTED",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Immersive Head Badge representing shield_person
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "SafeHer Avatar Icon",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                SafeHerTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == tab) tab.selectedIcon else tab.icon,
                                contentDescription = "${tab.title} Tab Icon"
                            )
                        },
                        label = { Text(text = tab.title, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onBackground,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission Banner Warning Card
            if (!hasSmsPermission || !hasLocationPermission) {
                PermissionRequestBanner(
                    hasSms = hasSmsPermission,
                    hasLoc = hasLocationPermission,
                    onGrantClick = requestAllPermissions
                )
            }

            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.95f, animationSpec = tween(220)))
                        .togetherWith(fadeOut(animationSpec = tween(180)))
                },
                label = "ScreenSwitchingContent"
            ) { target ->
                when (target) {
                    SafeHerTab.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        hasPermissions = hasSmsPermission && hasLocationPermission,
                        onRequestPermission = requestAllPermissions,
                        modifier = Modifier.fillMaxSize()
                    )
                    SafeHerTab.CONTACTS -> ContactsScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    SafeHerTab.HELPLINES -> HelplinesScreen(
                        modifier = Modifier.fillMaxSize()
                    )
                    SafeHerTab.MAP -> SafeMapScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestBanner(hasSms: Boolean, hasLoc: Boolean, onGrantClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("permission_request_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = AlertAmber.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.dp, AlertAmber.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.GppMaybe,
                contentDescription = "Safety Alert Warnings Logo",
                tint = AlertAmber,
                modifier = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Permission Action Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AlertAmber
                )
                Spacer(modifier = Modifier.height(4.dp))
                val pendingText = buildString {
                    append("To automate alerts immediately, we require: ")
                    if (!hasSms) append(" [SMS Direct Delivery]")
                    if (!hasLoc) {
                        if (!hasSms) append(" and ")
                        append("[Location Maps Tracking]")
                    }
                }
                Text(
                    text = pendingText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = AlertAmber, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    viewModel: SafetyViewModel,
    hasPermissions: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSosActive by viewModel.isSosTriggered.collectAsState()
    val isSirenOn by viewModel.isSirenPlaying.collectAsState()
    val isShakeOn by viewModel.isShakeDetectionEnabled.collectAsState()
    val locatingActive by viewModel.isLocating.collectAsState()
    val lastKnownLocation by viewModel.lastLocation.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // SOS Button and Pulsing Rings
        SosPulseButton(
            isTriggered = isSosActive,
            isLocating = locatingActive,
            onClick = {
                if (!hasPermissions) {
                    onRequestPermission()
                } else {
                    viewModel.triggerSOS()
                }
            },
            onReset = { viewModel.resetSOS() }
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Quick Controls Hardware Section
        Text(
            text = "Active Protective Devices",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Siren Alarm Toggle Card
            HardwareToggleCard(
                title = "Siren Audio",
                description = if (isSirenOn) "Looping Loud Alarm" else "Siren Muted",
                icon = if (isSirenOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeMute,
                isActive = isSirenOn,
                activeColor = CrimsonAlert,
                onClick = { viewModel.toggleSiren() },
                modifier = Modifier.weight(1f)
            )

            // Shake Monitor Toggle Card
            HardwareToggleCard(
                title = "Shake Sensor",
                description = if (isShakeOn) "Firm Shake Active" else "Sensor Paused",
                icon = Icons.Filled.Sensors,
                isActive = isShakeOn,
                activeColor = SafeEmerald,
                onClick = { viewModel.toggleShakeDetection() },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Tracking & Privacy Settings Center
        LiveLocationSharingCard(
            viewModel = viewModel,
            hasPermissions = hasPermissions,
            onRequestPermission = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Location Info Display
        if (lastKnownLocation != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Location Pin Active",
                        tint = SecurityBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Resolved Coordinates",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Lat: ${String.format("%.6f", lastKnownLocation?.latitude)} | Lng: ${String.format("%.6f", lastKnownLocation?.longitude)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HardwareToggleCard(
    title: String,
    description: String,
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedBg by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        animationSpec = tween(300)
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        animationSpec = tween(300)
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .height(128.dp)
            .border(BorderStroke(1.dp, animatedBorderColor), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = animatedBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "$title Icon Descriptor",
                    tint = if (isActive) activeColor else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )

                Switch(
                    checked = isActive,
                    onCheckedChange = { onClick() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = activeColor,
                        checkedTrackColor = activeColor.copy(alpha = 0.3f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.scale(0.75f)
                )
            }

            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SosPulseButton(
    isTriggered: Boolean,
    isLocating: Boolean,
    onClick: () -> Unit,
    onReset: () -> Unit
) {
    // Elegant pulsing animation loops
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScaleFactor"
    )

    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlphaFactor"
    )

    val buttonScaleFactor by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ButtonScaleFactor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(260.dp)
    ) {
        // Outer pulsing ring corresponding to bg-[#B3261E]/10
        Box(
            modifier = Modifier
                .size(256.dp)
                .scale(if (isTriggered) pulseScale1 * 1.15f else 1.0f)
                .background(
                    color = Color(0xFFB3261E).copy(alpha = if (isTriggered) pulseAlpha1 * 1.5f else 0.04f),
                    shape = CircleShape
                )
        )
        
        // Mid ring corresponding to bg-[#B3261E]/20
        Box(
            modifier = Modifier
                .size(208.dp)
                .scale(if (isTriggered) (pulseScale1 + 0.1f) * 0.95f else 1.0f)
                .background(
                    color = Color(0xFFB3261E).copy(alpha = if (isTriggered) 0.35f else 0.08f),
                    shape = CircleShape
                )
        )

        // Main action button corresponding to relative w-40 h-40 bg-[#B3261E] rounded-full
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(if (isTriggered) buttonScaleFactor else 1.0f)
                .background(
                    color = if (isTriggered) Color(0xFF8C1D18) else Color(0xFFB3261E),
                    shape = CircleShape
                )
                .clip(CircleShape)
                .clickable {
                    if (isTriggered) {
                        onReset()
                    } else {
                        onClick()
                    }
                }
                .border(4.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                if (isLocating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ACQUIRING GPS",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Icon(
                        imageVector = if (isTriggered) Icons.Filled.Dangerous else Icons.Filled.Warning,
                        contentDescription = "SOS Trigger Indicator",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isTriggered) "TAP TO STOP" else "SEND SOS",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isTriggered) "ALERT DESPATCHED" else "EMERGENCY ONLY",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SmsLogListItem(log: SmsLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonPin,
                        contentDescription = "Contact Name",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = log.contactName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Badge(
                    containerColor = when (log.status) {
                        "SENT" -> SafeEmerald.copy(alpha = 0.15f)
                        "FAILED" -> CrimsonAlert.copy(alpha = 0.15f)
                        else -> AlertAmber.copy(alpha = 0.15f)
                    },
                    contentColor = when (log.status) {
                        "SENT" -> SafeEmerald
                        "FAILED" -> CrimsonAlert
                        else -> AlertAmber
                    }
                ) {
                    Text(
                        text = log.status,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = log.message,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Dispatch details: ${log.phone} at ${log.timestamp}",
                fontSize = 8.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun ContactsScreen(viewModel: SafetyViewModel, modifier: Modifier = Modifier) {
    val contacts by viewModel.contacts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Guardian Circle",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Alerts dispatch automatically to these numbers.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add contact", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Group,
                            contentDescription = "Contacts Directory Empty Icon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Your Guardian Circle is Empty",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add close friends or family to receive quick-alerts.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(contacts) { contact ->
                        ContactItemCard(
                            contact = contact,
                            onDelete = { viewModel.deleteContact(contact) }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddContactDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { name, phone, isPrimary ->
                    viewModel.addContact(name, phone, isPrimary)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun ContactItemCard(contact: Contact, onDelete: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Profile avatar with first letter of the contact name, matching bg-[#6750A4] in light, etc.
                val avatarInit = if (contact.name.isNotBlank()) contact.name.trim().take(1).uppercase() else "?"
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = if (contact.isPrimary) {
                                Brush.verticalGradient(colors = listOf(CrimsonAlert, AlertAmber))
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                    )
                                )
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = avatarInit,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = contact.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (contact.isPrimary) {
                            Badge(
                                containerColor = AlertAmber.copy(alpha = 0.2f),
                                contentColor = AlertAmber
                            ) {
                                Text("Primary", fontSize = 8.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                    Text(
                        text = contact.phone,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Dial action button
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not launch system dialer.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = "Call Contact Direct",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                // Delete contact button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Remove Contact",
                        tint = CrimsonAlert
                    )
                }
            }
        }
    }
}

@Composable
fun AddContactDialog(onDismiss: () -> Unit, onAdd: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isPrimary by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add Guardian",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name") },
                    leadingIcon = { Icon(imageVector = Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Contact Number") },
                    leadingIcon = { Icon(imageVector = Icons.Filled.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Checkbox(
                        checked = isPrimary,
                        onCheckedChange = { isPrimary = it },
                        colors = CheckboxDefaults.colors(checkedColor = CrimsonAlert)
                    )
                    Text(
                        text = "Mark as Prime Contact (Speed Dial / Core)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank() && phone.isNotBlank()) {
                                onAdd(name.trim(), phone.trim(), isPrimary)
                            }
                        },
                        enabled = name.isNotBlank() && phone.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Friend", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun HelplinesScreen(modifier: Modifier = Modifier) {
    val helplines = HelplinesAndTips.helplines
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var activeDialTarget by remember { mutableStateOf<Helpline?>(null) }

    // Filter logic
    val filteredHelplines = helplines.filter { helpline ->
        val matchesCategory = selectedCategory == "All" || helpline.category.equals(selectedCategory, ignoreCase = true)
        val matchesSearch = helpline.name.contains(searchQuery, ignoreCase = true) ||
                helpline.number.contains(searchQuery, ignoreCase = true) ||
                helpline.description.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    if (activeDialTarget != null) {
        DialConfirmationDialog(
            helpline = activeDialTarget!!,
            onDismiss = { activeDialTarget = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Helplines Header Block
        Column {
            Text(
                text = "Emergency Support Core",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Direct connection to critical, expert 24/7 rescue and specialized care.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Premium Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search helpline name or number...", fontSize = 12.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search icon",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                focusedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("helpline_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Row of Horizontal Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Emergency", "Women Specialist", "Support").forEach { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = category },
                    label = { 
                        Text(
                            text = category, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (category) {
                            "Emergency" -> CrimsonAlert
                            "Women Specialist" -> AlertAmber
                            "Support" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        },
                        selectedLabelColor = if (category == "Women Specialist") Color.Black else Color.White,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("helpline_chip_${category.lowercase().replace(" ", "_")}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main List Content
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Prominent Top Quick-Dial Hotlinks on All Default Screen
            if (selectedCategory == "All" && searchQuery.isBlank()) {
                item {
                    Text(
                        text = "PRIORITY RED ACCENTS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeaturedHelplineCard(
                            name = "National Service",
                            number = "112",
                            desc = "Police / Medical / Rescue",
                            icon = Icons.Filled.LocalPolice,
                            color = CrimsonAlert,
                            modifier = Modifier.weight(1f),
                            onDial = { 
                                activeDialTarget = Helpline(
                                    name = "National Emergency Number",
                                    number = "112",
                                    description = "All-in-one emergency service helpline for police, medical assistance, and rescue.",
                                    category = "Emergency"
                                ) 
                            }
                        )

                        FeaturedHelplineCard(
                            name = "Women Help",
                            number = "1091",
                            desc = "Physical abuse / Danger",
                            icon = Icons.Filled.SupportAgent,
                            color = AlertAmber,
                            modifier = Modifier.weight(1f),
                            onDial = { 
                                activeDialTarget = Helpline(
                                    name = "Women Helpline (All India)",
                                    number = "1091",
                                    description = "Specialized 24/7 helpline for women facing physical harassment, abuse, or danger.",
                                    category = "Women Specialist"
                                ) 
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "STANDARD DIRECT SERVICE DIRECTORY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            if (filteredHelplines.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "No Results",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Helplines Matched",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "No service records correspond to query \"$searchQuery\"",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                items(filteredHelplines) { helpline ->
                    HelplineListItem(
                        helpline = helpline,
                        onDialClick = { activeDialTarget = helpline }
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturedHelplineCard(
    name: String,
    number: String,
    desc: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onDial: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onDial() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = name,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .background(color, RoundedCornerShape(30.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                        tint = if (color == AlertAmber) Color.Black else Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "DIAL $number",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = if (color == AlertAmber) Color.Black else Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun HelplineListItem(
    helpline: Helpline,
    onDialClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("helpline_card_${helpline.number}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val barWidth = 4.dp.toPx()
                    val color = when (helpline.category) {
                        "Emergency" -> CrimsonAlert
                        "Women Specialist" -> AlertAmber
                        else -> SafeEmerald
                    }
                    drawRect(
                        color = color,
                        size = androidx.compose.ui.geometry.Size(barWidth, this.size.height)
                    )
                }
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = when (helpline.category) {
                                "Emergency" -> CrimsonAlert.copy(alpha = 0.12f)
                                "Women Specialist" -> AlertAmber.copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (helpline.category) {
                            "Emergency" -> Icons.Filled.LocalPolice
                            "Women Specialist" -> Icons.Filled.SupportAgent
                            else -> Icons.Filled.EscalatorWarning
                        },
                        contentDescription = "Service Icon Detail",
                        tint = when (helpline.category) {
                            "Emergency" -> CrimsonAlert
                            "Women Specialist" -> AlertAmber
                            else -> MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = helpline.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Badge(
                            containerColor = when (helpline.category) {
                                "Emergency" -> CrimsonAlert.copy(alpha = 0.15f)
                                "Women Specialist" -> AlertAmber.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            },
                            contentColor = when (helpline.category) {
                                "Emergency" -> CrimsonAlert
                                "Women Specialist" -> AlertAmber
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        ) {
                            Text(
                                text = helpline.category,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = helpline.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(
                onClick = onDialClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = when (helpline.category) {
                            "Emergency" -> CrimsonAlert.copy(alpha = 0.12f)
                            "Women Specialist" -> AlertAmber.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            when (helpline.category) {
                                "Emergency" -> CrimsonAlert.copy(alpha = 0.25f)
                                "Women Specialist" -> AlertAmber.copy(alpha = 0.25f)
                                else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                            }
                        ),
                        RoundedCornerShape(12.dp)
                    )
                    .testTag("helpline_dial_btn_${helpline.number}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Dial ${helpline.name}",
                    tint = when (helpline.category) {
                        "Emergency" -> CrimsonAlert
                        "Women Specialist" -> AlertAmber
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DialConfirmationDialog(
    helpline: Helpline,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = when (helpline.category) {
                            "Emergency" -> CrimsonAlert.copy(alpha = 0.12f)
                            "Women Specialist" -> AlertAmber.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PhoneInTalk,
                    contentDescription = "Confirm Phone Call",
                    tint = when (helpline.category) {
                        "Emergency" -> CrimsonAlert
                        "Women Specialist" -> AlertAmber
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Initiate Support Call",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "You are about to dial:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = helpline.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = helpline.number,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = when (helpline.category) {
                        "Emergency" -> CrimsonAlert
                        "Women Specialist" -> AlertAmber
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This will direct you to your system phone dialer application.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${helpline.number}"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open phone caller.", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (helpline.category) {
                        "Emergency" -> CrimsonAlert
                        "Women Specialist" -> AlertAmber
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    contentColor = if (helpline.category == "Women Specialist") Color.Black else Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("dial_confirm_approve")
            ) {
                Text("Confirm & Call", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dial_confirm_cancel")
            ) {
                Text(
                    "Cancel", 
                    fontWeight = FontWeight.Normal, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

data class MapPlace(
    val name: String,
    val lat: Double,
    val lng: Double,
    val x: Float, // relative 0..1 coordinate for canvas drawing
    val y: Float, // relative 0..1 coordinate for canvas drawing
    val category: String = "Place", // "Place", "Police", "Hospital", "Safe Haven"
    val address: String = "123 Main Street"
)

data class UnsafeZone(
    val name: String,
    val description: String,
    val x: Float, // relative 0..1 coordinate
    val y: Float, // relative 0..1 coordinate
    val radius: Float // relative size
)

// Helper distance logic
fun getDistanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
    if (l2 == 0f) return Math.sqrt(((px - x1) * (px - x1) + (py - y1) * (py - y1)).toDouble()).toFloat()
    var t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2
    t = Math.max(0f, Math.min(1f, t))
    val projX = x1 + t * (x2 - x1)
    val projY = y1 + t * (y2 - y1)
    return Math.sqrt(((px - projX) * (px - projX) + (py - projY) * (py - projY)).toDouble()).toFloat()
}

fun calculatePath(start: MapPlace, end: MapPlace, avoidUnsafe: Boolean, unsafeZones: List<UnsafeZone>): List<Pair<Float, Float>> {
    val path = mutableListOf<Pair<Float, Float>>()
    path.add(start.x to start.y)
    
    if (avoidUnsafe) {
        val detours = mutableListOf<Pair<Float, Float>>()
        for (zone in unsafeZones) {
            val dist = getDistanceToSegment(zone.x, zone.y, start.x, start.y, end.x, end.y)
            if (dist < zone.radius) {
                val dx = end.x - start.x
                val dy = end.y - start.y
                val length = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (length > 0.01f) {
                    val nx = -dy / length
                    val ny = dx / length
                    val shift = zone.radius + 0.07f
                    var candX = zone.x + nx * shift
                    var candY = zone.y + ny * shift
                    if (candX < 0.1f || candX > 0.9f || candY < 0.1f || candY > 0.9f) {
                        candX = zone.x - nx * shift
                        candY = zone.y - ny * shift
                    }
                    detours.add(candX to candY)
                }
            }
        }
        detours.sortBy { Math.pow((it.first - start.x).toDouble(), 2.0) + Math.pow((it.second - start.y).toDouble(), 2.0) }
        path.addAll(detours)
    }
    path.add(end.x to end.y)
    return path
}

fun createColoredMarkerIcon(context: android.content.Context, color: Int): org.maplibre.android.annotations.Icon {
    val size = 42
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
    }
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint)
    paint.color = color
    canvas.drawCircle(size / 2f, size / 2f, size / 3.2f, paint)
    return org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(bitmap)
}

fun getCirclePoints(lat: Double, lng: Double, radiusInMeters: Double): List<LatLng> {
    val points = mutableListOf<LatLng>()
    val numPoints = 24
    val earthRadius = 6378137.0
    val latRad = Math.toRadians(lat)
    val lngRad = Math.toRadians(lng)
    val dR = radiusInMeters / earthRadius
    for (i in 0 until numPoints) {
        val angle = 2 * Math.PI * i / numPoints
        val pointLatRad = Math.asin(
            Math.sin(latRad) * Math.cos(dR) +
            Math.cos(latRad) * Math.sin(dR) * Math.cos(angle)
        )
        val pointLngRad = lngRad + Math.atan2(
            Math.sin(angle) * Math.sin(dR) * Math.cos(latRad),
            Math.cos(dR) - Math.sin(latRad) * Math.sin(pointLatRad)
        )
        points.add(LatLng(Math.toDegrees(pointLatRad), Math.toDegrees(pointLngRad)))
    }
    points.add(points[0])
    return points
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeMapScreen(viewModel: SafetyViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocationPermission = results[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: hasLocationPermission
    }

    val locationHelper = remember { com.example.util.LocationHelper(context) }
    var liveLocation by remember { mutableStateOf<android.location.Location?>(null) }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            while (true) {
                val loc = locationHelper.getCurrentLocation()
                if (loc != null) {
                    liveLocation = loc
                }
                delay(10000)
            }
        }
    }
    
    // Core landmark places with dynamic My Current Location coordinates based on GPS
    val allPlaces = remember(liveLocation) {
        val currentLat = liveLocation?.latitude ?: 37.7749
        val currentLng = liveLocation?.longitude ?: -122.4194
        listOf(
            MapPlace("My Current Location", currentLat, currentLng, 0.25f, 0.45f, "Place", "Coordinates based on GPS"),
            MapPlace("Central Station", 37.7801, -122.4120, 0.15f, 0.15f, "Place", "Transit hub, high foot traffic"),
            MapPlace("Downtown Plaza", 37.7725, -122.4080, 0.55f, 0.48f, "Place", "Commercial shopping corridor"),
            MapPlace("Tech Park Campus", 37.7650, -122.4250, 0.22f, 0.78f, "Place", "Offices and research facility"),
            MapPlace("Grand Library", 37.7780, -122.4180, 0.42f, 0.28f, "Place", "Public civic center square"),
            MapPlace("SafeHer HQ", 37.7710, -122.4210, 0.35f, 0.62f, "Place", "Community security office"),
            MapPlace("Metro Crossing", 37.7610, -122.4150, 0.75f, 0.85f, "Place", "Subway platform, illuminated 24/7"),
            MapPlace("Oakwood Apartments", 37.7850, -122.4280, 0.82f, 0.12f, "Place", "Residential family zone"),
            
            // Emergencies
            MapPlace("Northern Police Precinct", 37.7830, -122.4220, 0.62f, 0.22f, "Police", "Municipal Police Department (Station 4)"),
            MapPlace("Central Police Station", 37.7712, -122.4100, 0.48f, 0.68f, "Police", "Safety hub & precinct HQ"),
            MapPlace("Saint Jude Hospital", 37.7615, -122.4225, 0.28f, 0.88f, "Hospital", "24-Hour emergency unit & trauma center"),
            MapPlace("SFC Emergency Health Center", 37.7760, -122.4040, 0.88f, 0.52f, "Hospital", "First-aid urgent clinic"),
            MapPlace("Community Refuge Shelter", 37.7680, -122.4150, 0.44f, 0.51f, "Safe Haven", "Verified night-time safe corridor guardian"),
            MapPlace("Westhaven Shelter Circle", 37.7815, -122.4310, 0.85f, 0.32f, "Safe Haven", "24/7 community safety refuge")
        )
    }

    val unsafeZones = remember {
        listOf(
            UnsafeZone("Dimly-lit Alleyway", "Low municipal lighting and active construction zones", 0.48f, 0.38f, 0.10f),
            UnsafeZone("Industrial Warehouse Zone", "Deserted commercial sector with low security presence", 0.68f, 0.70f, 0.12f)
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var startPlace by remember { mutableStateOf(allPlaces[0]) } // Current Location
    var endPlace by remember { mutableStateOf(allPlaces[2]) } // Downtown Plaza
    var avoidUnsafe by remember { mutableStateOf(true) }
    var activeFilter by remember { mutableStateOf("ALL") } // "ALL", "POLICE", "HOSPITAL", "HAVEN"
    var selectedLandmark by remember { mutableStateOf<MapPlace?>(null) }
    var showSearchResults by remember { mutableStateOf(false) }

    LaunchedEffect(liveLocation) {
        if (startPlace.name == "My Current Location") {
            startPlace = allPlaces[0]
        }
    }

    val filteredPlaces = remember(activeFilter, allPlaces) {
        if (activeFilter == "ALL") allPlaces
        else allPlaces.filter { 
            when (activeFilter) {
                "POLICE" -> it.category == "Police"
                "HOSPITAL" -> it.category == "Hospital"
                "HAVEN" -> it.category == "Safe Haven"
                else -> true
            }
        }
    }

    val searchResults = remember(searchQuery, allPlaces) {
        if (searchQuery.isBlank()) emptyList()
        else {
            allPlaces.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.category.contains(searchQuery, ignoreCase = true) ||
                it.address.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Core Header Label
        Column {
            Text(
                text = "Safe Route Planner",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Smart mapping engine that safeguards your commute by routing around high-risk zones.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Search Bar Interface
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    showSearchResults = it.isNotEmpty()
                },
                placeholder = { Text("Search location, hospital or precinct...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon", modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { 
                            searchQuery = "" 
                            showSearchResults = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("map_search_bar"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonAlert,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Auto-complete dropdown list
            if (showSearchResults && searchResults.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 62.dp)
                        .heightIn(max = 200.dp)
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(searchResults) { place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        endPlace = place
                                        searchQuery = ""
                                        showSearchResults = false
                                        selectedLandmark = place
                                        Toast.makeText(context, "Set destination to ${place.name}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (place.category) {
                                        "Police" -> Icons.Default.LocalPolice
                                        "Hospital" -> Icons.Default.LocalHospital
                                        "Safe Haven" -> Icons.Default.Shield
                                        else -> Icons.Default.LocationOn
                                    },
                                    tint = when (place.category) {
                                        "Police" -> Color(0xFF1E88E5)
                                        "Hospital" -> Color(0xFFE53935)
                                        "Safe Haven" -> Color(0xFF8E24AA)
                                        else -> MaterialTheme.colorScheme.secondary
                                    },
                                    contentDescription = "Search Result Type",
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(place.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(place.address, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
                        }
                    }
                }
            }
        }

        // 2. Routing Controller Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Dropdowns or selectors for Start & End
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(SafeEmerald, CircleShape))
                            Text("Start Destination", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        var startExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                    .clickable { startExpanded = true }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(startPlace.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown indicators", modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = startExpanded,
                                onDismissRequest = { startExpanded = false }
                            ) {
                                allPlaces.filter { it.category == "Place" }.forEach { place ->
                                    DropdownMenuItem(
                                        text = { Text(place.name, fontSize = 11.sp) },
                                        onClick = {
                                            startPlace = place
                                            startExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.Bottom)
                            .padding(bottom = 6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val temp = startPlace
                                startPlace = endPlace
                                endPlace = temp
                            },
                            modifier = Modifier.size(34.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), CircleShape)
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Swap Locations", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(CrimsonAlert, CircleShape))
                            Text("End Destination", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        var endExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                    .clickable { endExpanded = true }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(endPlace.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown indices", modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = endExpanded,
                                onDismissRequest = { endExpanded = false }
                            ) {
                                allPlaces.forEach { place ->
                                    DropdownMenuItem(
                                        text = { Text(place.name, fontSize = 11.sp) },
                                        onClick = {
                                            endPlace = place
                                            endExpanded = false
                                            selectedLandmark = place
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))

                // Toggle "Avoid Unsafe Routes"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Safe Shield Icon",
                            tint = if (avoidUnsafe) SafeEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text("Avoid High-Risk Zones", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Auto detour around dimly-lit alleys or unmonitored spots", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = avoidUnsafe,
                        onCheckedChange = { avoidUnsafe = it },
                        modifier = Modifier.testTag("avoid_unsafe_switch")
                    )
                }
            }
        }

        // 3. Emergency Filters Row Panel
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Nearby Safety Anchors",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("ALL", "POLICE", "HOSPITAL", "HAVEN").forEach { category ->
                    val isSelected = activeFilter == category
                    val label = when (category) {
                        "ALL" -> "Show All Assets"
                        "POLICE" -> "Police Hubs"
                        "HOSPITAL" -> "24h Hospitals"
                        "HAVEN" -> "Safe Havens"
                        else -> category
                    }
                    val icon = when (category) {
                        "POLICE" -> Icons.Default.LocalPolice
                        "HOSPITAL" -> Icons.Default.LocalHospital
                        "HAVEN" -> Icons.Default.Shield
                        else -> Icons.Default.Map
                    }
                    
                    FilterChip(
                        selected = isSelected,
                        onClick = { activeFilter = category },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (category == "POLICE") Color(0xFF1976D2) else if (category == "HOSPITAL") CrimsonAlert else Color(0xFF8E24AA),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        // Calculate dynamic routing details
        val matchedPath = remember(startPlace, endPlace, avoidUnsafe) {
            calculatePath(startPlace, endPlace, avoidUnsafe, unsafeZones)
        }

        // Check if direct path crosses unsafe
        val crossesUnsafeDirect = remember(startPlace, endPlace) {
            unsafeZones.any { zone ->
                getDistanceToSegment(zone.x, zone.y, startPlace.x, startPlace.y, endPlace.x, endPlace.y) < zone.radius
            }
        }

        // Base math distances
        val directDist = remember(startPlace, endPlace) {
            val dX = endPlace.x - startPlace.x
            val dY = endPlace.y - startPlace.y
            Math.sqrt((dX * dX + dY * dY).toDouble()).toFloat() * 4.2f
        }

        val finalDist = remember(matchedPath) {
            var total = 0f
            for (i in 0 until matchedPath.size - 1) {
                val p1 = matchedPath[i]
                val p2 = matchedPath[i+1]
                val len = Math.sqrt(((p2.first - p1.first) * (p2.first - p1.first) + (p2.second - p1.second) * (p2.second - p1.second)).toDouble()).toFloat()
                total += len * 4.2f
            }
            total
        }

        // ETA mapping
        // walking is 12m per km
        val minutesETA = Math.round(finalDist * 12.0f)
        val directMinutesETA = Math.round(directDist * 12.0f)

        // 4. THE INTERACTIVE CONTAINER MAP
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column {
                if (!hasLocationPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(Color(0xFF141416))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.LocationOff, contentDescription = "Location disabled", tint = CrimsonAlert, modifier = Modifier.size(48.dp))
                            Text("Location Permission Needed", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                            Text(
                                "SafeHer needs location permission to show your live position and keep you safe.",
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                color = TextSecondary
                            )
                            Button(
                                onClick = {
                                    permissionLauncher.launch(
                                        arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                            ) {
                                Text("Enable Location", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(Color(0xFF141416))
                            .clip(RoundedCornerShape(24.dp))
                    ) {
                        val osmStyleJson = remember {
                            """
                            {
                              "version": 8,
                              "sources": {
                                "osm": {
                                  "type": "raster",
                                  "tiles": [
                                    "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png",
                                    "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png",
                                    "https://c.tile.openstreetmap.org/{z}/{x}/{y}.png"
                                  ],
                                  "tileSize": 256,
                                  "attribution": "© OpenStreetMap contributors"
                                }
                              },
                              "layers": [
                                {
                                  "id": "osm",
                                  "type": "raster",
                                  "source": "osm",
                                  "minzoom": 0,
                                  "maxzoom": 19
                                }
                              ]
                            }
                            """.trimIndent()
                        }

                        val mapLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                        val mapView = remember {
                            org.maplibre.android.maps.MapView(context).apply {
                                getMapAsync { mapboxMap ->
                                    mapboxMap.setStyle(org.maplibre.android.maps.Style.Builder().fromJson(osmStyleJson))
                                }
                            }
                        }

                        DisposableEffect(mapLifecycle, mapView) {
                            val observer = LifecycleEventObserver { _, event ->
                                try {
                                    when (event) {
                                        Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                                        Lifecycle.Event.ON_START -> mapView.onStart()
                                        Lifecycle.Event.ON_RESUME -> mapView.onResume()
                                        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                                        Lifecycle.Event.ON_STOP -> mapView.onStop()
                                        Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                                        else -> {}
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            mapLifecycle.addObserver(observer)
                            onDispose {
                                try {
                                    mapLifecycle.removeObserver(observer)
                                    mapView.onDestroy()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        var mapboxMapRef by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }

                        AndroidView(
                            factory = { mapView },
                            modifier = Modifier.fillMaxSize()
                        ) { view ->
                            view.getMapAsync { map ->
                                if (mapboxMapRef != map) {
                                    mapboxMapRef = map
                                    map.uiSettings.setZoomGesturesEnabled(true)
                                    map.uiSettings.setCompassEnabled(true)
                                    map.uiSettings.setLogoEnabled(false)
                                    map.uiSettings.setAttributionEnabled(false)
                                }
                            }
                        }

                        LaunchedEffect(
                            mapboxMapRef,
                            filteredPlaces,
                            startPlace,
                            endPlace,
                            matchedPath,
                            avoidUnsafe,
                            crossesUnsafeDirect
                        ) {
                            val map = mapboxMapRef ?: return@LaunchedEffect
                            val style = map.style
                            val triggerRedraw: (org.maplibre.android.maps.Style) -> Unit = { _ ->
                                map.clear()

                                // 1. Draw unsafe zones
                                unsafeZones.forEach { zone ->
                                    val centerLat = 37.7880 - (zone.y * 0.0300)
                                    val centerLng = -122.4350 + (zone.x * 0.0350)
                                    val circleCoords = getCirclePoints(centerLat, centerLng, zone.radius.toDouble() * 3100.0)

                                    map.addPolygon(
                                        org.maplibre.android.annotations.PolygonOptions()
                                            .addAll(circleCoords)
                                            .fillColor(android.graphics.Color.parseColor("#33B3261E"))
                                    )
                                    map.addPolyline(
                                        org.maplibre.android.annotations.PolylineOptions()
                                            .addAll(circleCoords)
                                            .color(android.graphics.Color.parseColor("#99B3261E"))
                                            .width(1.5f)
                                    )
                                }

                                // 2. Draw direct (unsafe connection) path
                                if (crossesUnsafeDirect) {
                                    val directCoords = listOf(
                                        org.maplibre.android.geometry.LatLng(startPlace.lat, startPlace.lng),
                                        org.maplibre.android.geometry.LatLng(endPlace.lat, endPlace.lng)
                                    )
                                    map.addPolyline(
                                        org.maplibre.android.annotations.PolylineOptions()
                                            .addAll(directCoords)
                                            .color(android.graphics.Color.parseColor("#66B3261E"))
                                            .width(2.5f)
                                    )
                                }

                                // 3. Draw safe smart routing matched path
                                val pathCoords = matchedPath.map { p ->
                                    org.maplibre.android.geometry.LatLng(
                                        37.7880 - (p.second * 0.0300),
                                        -122.4350 + (p.first * 0.0350)
                                    )
                                }
                                val pathColorHex = if (avoidUnsafe) "#4CAF50" else "#FFA726"
                                map.addPolyline(
                                    org.maplibre.android.annotations.PolylineOptions()
                                        .addAll(pathCoords)
                                        .color(android.graphics.Color.parseColor(pathColorHex))
                                        .width(5f)
                                )

                                // 4. Draw marker overlays of places
                                filteredPlaces.forEach { place ->
                                    val isStart = place.name == startPlace.name
                                    val isEnd = place.name == endPlace.name

                                    val markerColorHex = when {
                                        isStart -> "#4CAF50"
                                        isEnd -> "#B3261E"
                                        place.category == "Police" -> "#1E88E5"
                                        place.category == "Hospital" -> "#E53935"
                                        place.category == "Safe Haven" -> "#8E24AA"
                                        else -> "#FFA726"
                                    }

                                    val coloredIcon = createColoredMarkerIcon(context, android.graphics.Color.parseColor(markerColorHex))
                                    map.addMarker(
                                        org.maplibre.android.annotations.MarkerOptions()
                                            .position(org.maplibre.android.geometry.LatLng(place.lat, place.lng))
                                            .title(place.name)
                                            .snippet(place.address)
                                            .icon(coloredIcon)
                                    )
                                }

                                map.setOnMarkerClickListener { marker ->
                                    val clickedPlace = filteredPlaces.find { it.name == marker.title }
                                    if (clickedPlace != null) {
                                        selectedLandmark = clickedPlace
                                    }
                                    true
                                }
                            }

                            if (style != null && style.isFullyLoaded) {
                                triggerRedraw(style)
                            } else {
                                map.setStyle(org.maplibre.android.maps.Style.Builder().fromJson(osmStyleJson)) { styleObj ->
                                    triggerRedraw(styleObj)
                                }
                            }
                        }

                        LaunchedEffect(selectedLandmark, mapboxMapRef) {
                            val map = mapboxMapRef ?: return@LaunchedEffect
                            selectedLandmark?.let { landmark ->
                                map.animateCamera(
                                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                        org.maplibre.android.geometry.LatLng(landmark.lat, landmark.lng),
                                        14.5
                                    ),
                                    1200
                                )
                            }
                        }

                        LaunchedEffect(startPlace, mapboxMapRef) {
                            val map = mapboxMapRef ?: return@LaunchedEffect
                            if (selectedLandmark == null) {
                                map.animateCamera(
                                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                        org.maplibre.android.geometry.LatLng(startPlace.lat, startPlace.lng),
                                        13.2
                                    ),
                                    1200
                                )
                            }
                        }

                        selectedLandmark?.let { place ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                                    .fillMaxWidth(0.9f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xE61E1E22))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = when (place.category) {
                                                    "Police" -> Icons.Default.LocalPolice
                                                    "Hospital" -> Icons.Default.LocalHospital
                                                    "Safe Haven" -> Icons.Default.Shield
                                                    else -> Icons.Default.Place
                                                },
                                                contentDescription = "Landmark Type Icon",
                                                tint = when (place.category) {
                                                    "Police" -> Color(0xFF1E88E5)
                                                    "Hospital" -> Color(0xFFE53935)
                                                    "Safe Haven" -> Color(0xFF8E24AA)
                                                    else -> SafeEmerald
                                                },
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                place.name,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        Badge(
                                            containerColor = when (place.category) {
                                                "Police" -> Color(0xFF1E88E5).copy(alpha = 0.15f)
                                                "Hospital" -> Color(0xFFE53935).copy(alpha = 0.15f)
                                                "Safe Haven" -> Color(0xFF8E24AA).copy(alpha = 0.15f)
                                                else -> Color.White.copy(alpha = 0.12f)
                                            },
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = place.category.uppercase(),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = place.address,
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val safetyScore = when (place.category) {
                                            "Police", "Hospital" -> "100% Secure"
                                            "Safe Haven" -> "95% Safe"
                                            else -> "90% Safe"
                                        }
                                        Text(
                                            text = "⚡ Security Index: $safetyScore",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SafeEmerald
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TextButton(
                                                onClick = { selectedLandmark = null },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("Dismiss", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                            }

                                            Button(
                                                onClick = {
                                                    endPlace = place
                                                    Toast.makeText(context, "Route planned to ${place.name}", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                                    Text("GO HERE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. LIVE NAVIGATION SUMMARY & ETA FOOTER CONTROL
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (avoidUnsafe) Icons.Default.CheckCircle else Icons.Default.Info,
                                    contentDescription = "Status",
                                    tint = if (avoidUnsafe) SafeEmerald else Color(0xFFFBC02D),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (avoidUnsafe) "Bespoke Safe Corridor Active" else "Direct Mode Route Plot",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            Text(
                                text = "Commute from ${startPlace.name} to ${endPlace.name}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Display computed parameters
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${minutesETA} mins",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (avoidUnsafe) SafeEmerald else Color(0xFFFFA726)
                            )
                            Text(
                                text = String.format(Locale.US, "Est. Distance: %.2f km", finalDist),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Direct comparison disclaimer
                    if (avoidUnsafe && crossesUnsafeDirect) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = SafeEmerald.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SafeEmerald.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = "Safe Shield logo", tint = SafeEmerald, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Smart reroute adds +${minutesETA - directMinutesETA} mins to completely avoid dimly lit alleys.",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SafeEmerald,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    } else if (crossesUnsafeDirect) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = CrimsonAlert.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Hazard Indicator", tint = CrimsonAlert, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Warning: Direct path crosses 1+ active hazard zone(s). Turn on safety filters to bypass threat corridors safely.",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CrimsonAlert,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Passed check", tint = SafeEmerald, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Excellent: Your calculated route is clean and avoids all known urban threat corridors.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Floating SOS Alert Button (Overlayed beautifully at the bottom-right corner)
    FloatingActionButton(
        onClick = { viewModel.triggerSOS() },
        containerColor = CrimsonAlert,
        contentColor = Color.White,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(24.dp)
            .testTag("floating_sos_button"),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Trigger Emergency SOS",
                modifier = Modifier.size(24.dp)
            )
            Text("SOS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
}

// Support Theme Helper
private fun crimsonRedBack(): Color {
    return Color(0xFFAC1C1C)
}

@Composable
fun LiveLocationSharingCard(
    viewModel: SafetyViewModel,
    hasPermissions: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTrackingActive by viewModel.isLiveTrackingActive.collectAsState()
    val trackingContacts by viewModel.selectedTrackingContacts.collectAsState()
    val trackingInterval by viewModel.trackingIntervalSeconds.collectAsState()
    val autoExpireSetting by viewModel.autoExpireMinutes.collectAsState()
    val remainingSecs by viewModel.remainingSeconds.collectAsState()
    val trackingLogs by viewModel.liveTrackingLogs.collectAsState()
    val allContactsList by viewModel.contacts.collectAsState()

    Card(
        modifier = modifier.testTag("live_sharing_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTrackingActive) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp, 
            if (isTrackingActive) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (isTrackingActive) {
                                    SafeEmerald.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isTrackingActive) Icons.Filled.Satellite else Icons.Filled.LocationOn,
                            contentDescription = "Live Tracking Center Icon",
                            tint = if (isTrackingActive) SafeEmerald else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Live GPS Broadcast",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isTrackingActive) "Active Sync Transmission" else "Privacy-protected real-time pings",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isTrackingActive) {
                    Row(
                        modifier = Modifier
                            .background(SafeEmerald.copy(alpha = 0.15f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Flashing Green Dot Animation
                        val infiniteTransition = rememberInfiniteTransition(label = "RadarPing")
                        val dotAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = FastOutLinearInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "RadarPingAlpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(SafeEmerald.copy(alpha = dotAlpha), CircleShape)
                        )
                        Text(
                            text = "LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = SafeEmerald
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isTrackingActive) {
                // Config Setup Screen
                // 1. Selector Emergency Contacts
                Text(
                    text = "1. RECEIVING TRUSTED CIRCLES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (allContactsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "⚠️ No emergency contacts saved yet. Go to the Contacts tab to add colleagues or family members first.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(allContactsList) { contact ->
                            val isSelected = trackingContacts.contains(contact.id) || (trackingContacts.isEmpty() && contact.isPrimary)
                            val avatarInit = if (contact.name.isNotBlank()) contact.name.trim().take(1).uppercase() else "?"
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.toggleTrackingContact(contact.id) }
                                    .background(
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .padding(vertical = 6.dp, horizontal = 10.dp)
                                    .width(62.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = avatarInit,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = contact.name,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Setup Duration and Frequency Selects
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Duration Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SESSION EXPIRE PERIOD",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(15, 60, 240).forEach { mins ->
                                val isSelected = autoExpireSetting == mins
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent
                                            ),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.setAutoExpireMinutes(mins) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (mins >= 60) "${mins / 60}h" else "${mins}m",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Interval Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "UPDATE FREQUENCY PULSE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(15, 60, 300).forEach { secs ->
                                val isSelected = trackingInterval == secs
                                val label = when (secs) {
                                    15 -> "15s"
                                    60 -> "1m"
                                    else -> "5m"
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent
                                            ),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.setTrackingInterval(secs) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Start button
                Button(
                    onClick = {
                        if (!hasPermissions) {
                            onRequestPermission()
                        } else {
                            viewModel.startLiveTracking()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("start_broadcast_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Radar Signal Launcher")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Live GPS Sharing", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

            } else {
                // ACTIVE Tracking Dashboard state
                val hours = remainingSecs / 3600
                val minutes = (remainingSecs % 3600) / 60
                val seconds = remainingSecs % 60
                val countdownFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PRIVACY REVOCATION COUNTDOWN",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = countdownFormatted,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Secure broadcast terminates automatically upon timer expiry",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Settings details row inside Active Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeContactsCount = if (trackingContacts.isEmpty()) {
                        allContactsList.filter { it.isPrimary }.size
                    } else {
                        trackingContacts.size
                    }
                    
                    Text(
                        text = buildString {
                            append("Sharing with ")
                            append(activeContactsCount)
                            append(if (activeContactsCount == 1) " circle" else " circles")
                            append(" (Precise GPS)")
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Small telemetry terminal showing location shared logs
                Text(
                    text = "TRANSMISSION HISTORY",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (trackingLogs.isEmpty()) {
                        Text(
                            text = "Initializing GPS session pipeline. Awaiting satellite links...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Display the most recent logs
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(trackingLogs) { logLine ->
                                Text(
                                    text = logLine,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Terminate Button
                Button(
                    onClick = { viewModel.stopLiveTracking() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("stop_broadcast_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Stop, contentDescription = "Stop Pulse Signalling")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Terminate Live Broadcast", fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
    }
}
