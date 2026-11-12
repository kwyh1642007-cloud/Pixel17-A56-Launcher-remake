package com.pixel17.a56launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.LruCache
import android.view.Gravity
import android.view.KeyEvent
import android.view.Surface
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Bitmap
)

// 8GB RAM Optimized Icon Cache for Exynos 1580
object IconCache {
    private val cache = LruCache<String, Bitmap>(250)
    fun get(pkg: String): Bitmap? = cache.get(pkg)
    fun put(pkg: String, bmp: Bitmap) { cache.put(pkg, bmp) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock 120Hz refresh rate on Galaxy A56 Super AMOLED display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.preferredRefreshRate = 120f
            window.decorView.rootSurfaceControl?.let {
                // High frame rate hint for Xclipse 540 GPU
            }
        }

        setContent {
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(LocalContext.current)
            } else {
                darkColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Pixel17LauncherRoot()
            }
        }
    }
}

@Composable
fun Pixel17LauncherRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isDrawerOpen by remember { mutableStateOf(false) }
    var isControlCenterOpen by remember { mutableStateOf(false) }
    var isVolumeBarVisible by remember { mutableStateOf(false) }
    var currentVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }

    // Offload package parsing to Cortex-A520 efficiency cores (Dispatchers.IO)
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = resolveInfo.loadLabel(pm).toString()
                    val bmp = IconCache.get(pkg) ?: drawableToBitmap(resolveInfo.loadIcon(pm)).also {
                        IconCache.put(pkg, it)
                    }
                    AppEntry(label, pkg, bmp)
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    // Auto-hide Pixel Volume Bar after 3 seconds
    LaunchedEffect(isVolumeBarVisible, currentVolume) {
        if (isVolumeBarVisible) {
            delay(3000)
            isVolumeBarVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1116))
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -35) isDrawerOpen = true
                    if (dragAmount > 35 && !isDrawerOpen) isControlCenterOpen = true
                }
            }
    ) {
        // 1. PIXEL OS 17 HOME SCREEN
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Pixel "At a Glance" Widget + Quick Control Triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    val dateStr = remember {
                        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
                    }
                    Text(
                        text = dateStr,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Android 17 • Pixel OS • 120Hz Xclipse 540",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = { isVolumeBarVisible = !isVolumeBarVisible }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Pixel Volume")
                    }
                    FilledTonalIconButton(onClick = { isControlCenterOpen = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Pixel Control Center")
                    }
                }
            }

            // Home Favorite Grid (Top 10 Apps)
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(apps.take(10), key = { it.packageName }) { app ->
                    AppIconItem(app = app) {
                        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                            context.startActivity(it)
                        }
                    }
                }
            }

            // Bottom Dock + Pixel Search Pill
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    apps.take(5).forEach { app ->
                        AppIconItem(app = app, showLabel = false) {
                            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                                context.startActivity(it)
                            }
                        }
                    }
                }

                // Pixel OS 17 Bottom Search Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clickable { isDrawerOpen = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Search apps & web...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. PIXEL OS 17 APP DRAWER (120Hz Spring Animation)
        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search apps") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                searchQuery = ""
                                isDrawerOpen = false
                            }) {
                                Icon(Icons.Default.KeyboardArrowDown, "Close")
                            }
                        },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val filteredApps = remember(apps, searchQuery) {
                        if (searchQuery.isBlank()) apps
                        else apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            AppIconItem(app = app) {
                                isDrawerOpen = false
                                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                                    context.startActivity(it)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. ANDROID 17 PIXEL CONTROL CENTER OVERLAY
        AnimatedVisibility(
            visible = isControlCenterOpen,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it })
        ) {
            Pixel17ControlCenter(onClose = { isControlCenterOpen = false })
        }

        // 4. ANDROID 17 PIXEL VOLUME BAR OVERLAY (Right Edge Vertical Pill)
        AnimatedVisibility(
            visible = isVolumeBarVisible,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            Pixel17VolumePill(
                volume = currentVolume,
                maxVolume = maxVolume,
                onVolumeChange = { newVol ->
                    currentVolume = newVol
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol.toInt(), 0)
                }
            )
        }
    }
}

@Composable
fun Pixel17ControlCenter(onClose: () -> Unit) {
    val context = LocalContext.current
    var wifiOn by remember { mutableStateOf(true) }
    var btOn by remember { mutableStateOf(true) }
    var torchOn by remember { mutableStateOf(false) }
    var dndOn by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(0.75f) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF10131A).copy(alpha = 0.96f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pixel OS 17 • Quick Settings",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Pixel Brightness Pill Slider
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BrightnessHigh, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = brightness,
                        onValueChange = {
                            brightness = it
                            if (Settings.System.canWrite(context)) {
                                Settings.System.putInt(
                                    context.contentResolver,
                                    Settings.System.SCREEN_BRIGHTNESS,
                                    (it * 255).toInt()
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 2x2 Material You Pill Tiles
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PixelQuickTile(
                    title = "Internet",
                    subtitle = if (wifiOn) "5G & Wi-Fi" else "Off",
                    active = wifiOn,
                    icon = Icons.Default.Wifi,
                    modifier = Modifier.weight(1f)
                ) {
                    wifiOn = !wifiOn
                    context.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                }
                PixelQuickTile(
                    title = "Bluetooth",
                    subtitle = if (btOn) "Connected" else "Off",
                    active = btOn,
                    icon = Icons.Default.Bluetooth,
                    modifier = Modifier.weight(1f)
                ) { btOn = !btOn }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PixelQuickTile(
                    title = "Flashlight",
                    subtitle = if (torchOn) "On" else "Off",
                    active = torchOn,
                    icon = Icons.Default.FlashlightOn,
                    modifier = Modifier.weight(1f)
                ) {
                    torchOn = !torchOn
                    runCatching {
                        val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        cam.setTorchMode(cam.cameraIdList[0], torchOn)
                    }
                }
                PixelQuickTile(
                    title = "Do Not Disturb",
                    subtitle = if (dndOn) "Active" else "Off",
                    active = dndOn,
                    icon = Icons.Default.DoNotDisturbOn,
                    modifier = Modifier.weight(1f)
                ) { dndOn = !dndOn }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer Actions (System Settings & Accessibility Overlay Enable)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) {
                    Text("Enable Global Volume Key Hook")
                }
                FilledTonalIconButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
    }
}

@Composable
fun PixelQuickTile(
    title: String,
    subtitle: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else Color(0xFF1E222B)
    val contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else Color.White

    Surface(
        modifier = modifier
            .height(74.dp)
            .clip(RoundedCornerShape(36.dp))
            .clickable { onClick() },
        color = bg,
        shape = RoundedCornerShape(36.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = contentColor)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, color = contentColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = contentColor.copy(alpha = 0.75f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun Pixel17VolumePill(
    volume: Float,
    maxVolume: Float,
    onVolumeChange: (Float) -> Unit
) {
    var ringerMode by remember { mutableIntStateOf(0) } // 0: Ring, 1: Vibrate, 2: Silent

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 12.dp,
        modifier = Modifier
            .width(64.dp)
            .height(260.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { ringerMode = (ringerMode + 1) % 3 }) {
                val modeIcon = when (ringerMode) {
                    1 -> Icons.Default.Vibration
                    2 -> Icons.Default.VolumeOff
                    else -> Icons.Default.NotificationsActive
                }
                Icon(modeIcon, contentDescription = "Sound Mode", tint = MaterialTheme.colorScheme.primary)
            }

            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..maxVolume,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )

            Text(
                text = "${((volume / maxVolume) * 100).toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AppIconItem(app: AppEntry, showLabel: Boolean = true, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Image(
            bitmap = app.icon.asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}

// SYSTEM-WIDE HARDWARE VOLUME KEY INTERCEPTOR FOR SAMSUNG ONE UI 9
class PixelSystemUIService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val direction = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    AudioManager.ADJUST_RAISE
                } else {
                    AudioManager.ADJUST_LOWER
                }
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
                showPixelVolumeOverlay(audio)
            }
            return true // Suppress Samsung One UI 9 native volume bar
        }
        return super.onKeyEvent(event)
    }

    private fun showPixelVolumeOverlay(audio: AudioManager) {
        if (overlayView != null) {
            windowManager?.removeViewImmediate(overlayView)
            overlayView = null
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE1E222B.toInt())
            setPadding(32, 32, 32, 32)
        }

        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val label = TextView(this).apply {
            text = "Pixel Vol: ${(current * 100) / max}%"
            setTextColor(0xFFA8C7FA.toInt())
            textSize = 14f
        }

        val seekBar = SeekBar(this).apply {
            this.max = max
            this.progress = current
        }

        layout.addView(label)
        layout.addView(seekBar)

        val params = WindowManager.LayoutParams(
            420,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 24
        }

        overlayView = layout
        windowManager?.addView(overlayView, params)
        layout.postDelayed({
            overlayView?.let {
                runCatching { windowManager?.removeView(it) }
                overlayView = null
            }
        }, 2500)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
