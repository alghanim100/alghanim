package com.alghanim.qibla.ui

import android.content.Context
import android.hardware.GeomagneticField
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alghanim.qibla.BuildConfig
import com.alghanim.qibla.R
import com.alghanim.qibla.data.CityPreset
import com.alghanim.qibla.data.PRESET_CITIES
import com.alghanim.qibla.data.QiblaCalculator
import com.alghanim.qibla.ui.components.NativeQiblaMapView
import com.alghanim.qibla.ui.components.QiblaMapType
import com.alghanim.qibla.ui.theme.*
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class QiblaViewMode {
    COMPASS, MAP
}

private enum class AppFontOption(
    val key: String,
    val arabicName: String,
    val englishName: String,
    val family: FontFamily
) {
    DEFAULT("default", "النظام", "System", FontFamily.Default),
    CAIRO("cairo", "القاهرة", "Cairo", FontFamily(Font(R.font.cairo))),
    SANS("sans", "بسيط", "Sans", FontFamily.SansSerif),
    SERIF("serif", "كلاسيكي", "Serif", FontFamily.Serif),
    MONO("mono", "ثابت", "Mono", FontFamily.Monospace);

    fun displayName(isArabic: Boolean): String = if (isArabic) arabicName else englishName

    companion object {
        fun fromKey(key: String?): AppFontOption = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

private enum class AppFontSizeOption(
    val key: String,
    val arabicName: String,
    val englishName: String,
    val scale: Float
) {
    SMALL("small", "صغير", "Small", 0.9f),
    NORMAL("normal", "متوسط", "Medium", 1.0f),
    LARGE("large", "كبير", "Large", 1.12f),
    EXTRA_LARGE("extra_large", "كبير جدًا", "Extra large", 1.25f);

    fun displayName(isArabic: Boolean): String = if (isArabic) arabicName else englishName

    companion object {
        fun fromKey(key: String?): AppFontSizeOption = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(
    // Sensor values
    azimuth: Float,
    pitch: Float,
    roll: Float,
    sensorAccuracy: Int,
    
    // GPS values
    gpsLatitude: Double?,
    gpsLongitude: Double?,
    gpsAccuracy: Float,
    resolvedCity: String?,
    satellitesInView: Int,
    satellitesUsed: Int,
    
    // Permission status
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val baseDensity = LocalDensity.current
    val settingsPreferences = remember(context) {
        context.getSharedPreferences("qibla_settings", Context.MODE_PRIVATE)
    }
    
    // UI State
    var isArabic by remember { mutableStateOf(true) }
    var viewMode by remember { mutableStateOf(QiblaViewMode.COMPASS) }
    var currentMapType by remember { mutableStateOf(QiblaMapType.SATELLITE) }
    var showCityDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var centerTrigger by remember { mutableStateOf(0) }
    var selectedFontOption by remember {
        mutableStateOf(AppFontOption.fromKey(settingsPreferences.getString("font_option", null)))
    }
    var selectedFontSizeOption by remember {
        mutableStateOf(AppFontSizeOption.fromKey(settingsPreferences.getString("font_size_option", null)))
    }
    
    // Manual Location Overrides
    var useManualLocation by remember { mutableStateOf(false) }
    var manualLatitude by remember { mutableStateOf(24.7136) } // Riyadh default
    var manualLongitude by remember { mutableStateOf(46.6753) }
    var manualCityName by remember { mutableStateOf("Riyadh, Saudi Arabia") }
    var manualCityNameAr by remember { mutableStateOf("الرياض، السعودية") }

    // Selected active coordinates
    val activeLatitude = if (useManualLocation) manualLatitude else gpsLatitude
    val activeLongitude = if (useManualLocation) manualLongitude else gpsLongitude

    val magneticDeclination = remember(activeLatitude, activeLongitude) {
        if (activeLatitude != null && activeLongitude != null) {
            GeomagneticField(
                activeLatitude.toFloat(),
                activeLongitude.toFloat(),
                0f,
                System.currentTimeMillis()
            ).declination
        } else {
            0f
        }
    }

    val trueAzimuth = remember(azimuth, magneticDeclination) {
        normalizeDegrees(azimuth + magneticDeclination)
    }

    // Calculated values
    val qiblaBearing = remember(activeLatitude, activeLongitude) {
        if (activeLatitude != null && activeLongitude != null) {
            QiblaCalculator.calculateQiblaBearing(activeLatitude, activeLongitude).toFloat()
        } else {
            135f // Default towards South East if no coordinates
        }
    }
    
    val distanceToKaaba = remember(activeLatitude, activeLongitude) {
        if (activeLatitude != null && activeLongitude != null) {
            QiblaCalculator.calculateDistanceToKaaba(activeLatitude, activeLongitude)
        } else {
            0.0
        }
    }

    // Relative angle to Qibla (angle between device's heading and Mecca)
    // Adjusting for shortest rotation path
    var rawDiff = qiblaBearing - trueAzimuth
    if (rawDiff > 180f) rawDiff -= 360f
    if (rawDiff < -180f) rawDiff += 360f
    
    val isAligned = activeLatitude != null && activeLongitude != null && Math.abs(rawDiff) < 3.0f

    // Smooth compass and Qibla needle animation
    // Using custom short-path interpolation to prevent full 360 spins when crossing North
    var targetCompassRotation by remember { mutableStateOf(0f) }
    LaunchedEffect(trueAzimuth) {
        var diff = -trueAzimuth - targetCompassRotation
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f
        targetCompassRotation += diff
    }
    
    val animatedCompassRotation by animateFloatAsState(
        targetValue = targetCompassRotation,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "compassRotation"
    )

    // Trigger haptic feedback exactly when entering alignment state
    var lastAlignmentState by remember { mutableStateOf(false) }
    LaunchedEffect(isAligned) {
        if (isAligned && !lastAlignmentState) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        lastAlignmentState = isAligned
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * selectedFontSizeOption.scale
        ),
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = selectedFontOption.family)
    ) {
        Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isArabic) "القبلة الذكية" else "Smart Qibla",
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        fontSize = 22.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SlateBackground
                ),
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = if (isArabic) "الإعدادات" else "Settings",
                            tint = GoldPrimary
                        )
                    }

                    // Language Switch
                    TextButton(onClick = { isArabic = !isArabic }) {
                        Text(
                            text = if (isArabic) "English" else "العربية",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        },
        containerColor = SlateBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Location Status & Satellite Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isArabic) "الموقع الحالي:" else "Current Location:",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val resolvedText = if (useManualLocation) {
                            if (isArabic) manualCityNameAr else manualCityName
                        } else {
                            resolvedCity ?: if (gpsLatitude != null) {
                                String.format(Locale.US, "%.4f, %.4f", gpsLatitude, gpsLongitude)
                            } else {
                                if (isArabic) "جاري تحديد الموقع..." else "Acquiring GPS..."
                            }
                        }
                        
                        Text(
                            text = resolvedText,
                            color = TextLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        // Satellite count badge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = "Satellites",
                                tint = GoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (useManualLocation) {
                                    if (isArabic) "وضع غير متصل" else "Offline mode"
                                } else {
                                    "$satellitesUsed / $satellitesInView Sat"
                                },
                                color = if (satellitesUsed > 0 || useManualLocation) MintGlow else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Switch Location Source / Manually Select
                        Text(
                            text = if (isArabic) "تغيير الموقع ⚙" else "Change location ⚙",
                            color = GoldPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { showCityDialog = true }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }

            // Compass Calibration Warning
            if (sensorAccuracy <= 1) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1212)),
                    border = BorderStroke(1.dp, Color(0xFFFF4444))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFFF4444),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isArabic) "دقة البوصلة ضعيفة" else "Low Compass Accuracy",
                                color = Color(0xFFFF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isArabic) "يرجى تحريك الهاتف على شكل رقم (8) لمعايرتها" else "Please move your phone in a figure-8 motion to calibrate",
                                color = Color(0xFFFFBDBD),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Segmented mode picker: Compass vs Qibla Map
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .background(SlateSurface, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Button(
                    onClick = { viewMode = QiblaViewMode.COMPASS },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewMode == QiblaViewMode.COMPASS) EmeraldSecondary else Color.Transparent,
                        contentColor = if (viewMode == QiblaViewMode.COMPASS) GoldPrimary else TextMuted
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp),
                    elevation = null
                ) {
                    Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isArabic) "بوصلة" else "Compass", fontSize = 12.sp)
                }
                
                Button(
                    onClick = { viewMode = QiblaViewMode.MAP },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewMode == QiblaViewMode.MAP) EmeraldSecondary else Color.Transparent,
                        contentColor = if (viewMode == QiblaViewMode.MAP) GoldPrimary else TextMuted
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp),
                    elevation = null
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isArabic) "خريطة" else "Map", fontSize = 12.sp)
                }
            }

            // Main Display Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SlateSurface)
                    .border(1.dp, WarmGold.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                when (viewMode) {
                    QiblaViewMode.MAP -> {
                        // Interactive Map Mode (Native OsmDroid)
                        Box(modifier = Modifier.fillMaxSize()) {
                            NativeQiblaMapView(
                                latitude = activeLatitude,
                                longitude = activeLongitude,
                                azimuth = animatedCompassRotation, // Native smooth map rotation
                                mapType = currentMapType,
                                centerTrigger = centerTrigger
                            )
                            
                            // Map Controls Column
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Map Type Toggle Button
                                SmallFloatingActionButton(
                                    onClick = { 
                                        currentMapType = if (currentMapType == QiblaMapType.SATELLITE) 
                                            QiblaMapType.TERRAIN else QiblaMapType.SATELLITE 
                                    },
                                    containerColor = SlateSurface.copy(alpha = 0.9f),
                                    contentColor = WarmGold
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers, 
                                        contentDescription = "Toggle Map Type",
                                        tint = WarmGold
                                    )
                                }

                                // My Location Button
                                SmallFloatingActionButton(
                                    onClick = { centerTrigger++ },
                                    containerColor = SlateSurface.copy(alpha = 0.9f),
                                    contentColor = MintGlow
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation, 
                                        contentDescription = "My Location",
                                        tint = MintGlow
                                    )
                                }
                            }
                        }
                    }
                    QiblaViewMode.COMPASS -> {
                        // Compass Mode
                        CompassView(
                            compassRotation = animatedCompassRotation,
                            qiblaBearing = qiblaBearing,
                            isAligned = isAligned,
                            isArabic = isArabic,
                            gpsCoordinatesAvailable = activeLatitude != null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Guidance Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .shadow(if (isAligned) 8.dp else 0.dp, spotColor = MintGlow),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAligned) EmeraldSecondary else SlateSurface
                ),
                border = if (isAligned) BorderStroke(1.5.dp, MintGlow) else null
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (activeLatitude == null) {
                        Text(
                            text = if (isArabic) "بانتظار إحداثيات الموقع أو حدد يدوياً" else "Waiting for coordinates or select manually",
                            color = TextLight,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRequestLocationPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                        ) {
                            Text(text = if (isArabic) "السماح بالوصول للموقع" else "Request Location Permission", color = SlateBackground)
                        }
                    } else {
                        Text(
                            text = if (isAligned) {
                                if (isArabic) "أنت باتجاه القبلة الصحيح تماماً 🕋" else "You are facing the Qibla perfectly 🕋"
                            } else {
                                val direction = if (rawDiff > 0) {
                                    if (isArabic) "أدر الهاتف يميناً ↻" else "Turn phone right ↻"
                                } else {
                                    if (isArabic) "أدر الهاتف يساراً ↺" else "Turn phone left ↺"
                                }
                                direction
                            },
                            color = if (isAligned) MintGlow else TextLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        if (!isAligned) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isArabic) "الفارق الزاوي: ${String.format(Locale.US, "%.1f", Math.abs(rawDiff))} درجة" else "Angle offset: ${String.format(Locale.US, "%.1f", Math.abs(rawDiff))}°",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Quick Stats Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Distance Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isArabic) "المسافة للكعبة" else "Distance to Kaaba",
                            color = TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (activeLatitude != null) "${String.format(Locale.US, "%,.0f", distanceToKaaba)} كم" else "---",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                
                // Qibla Bearing Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isArabic) "زاوية القبلة" else "Qibla Angle",
                            color = TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${String.format(Locale.US, "%.1f", qiblaBearing)}°",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                // Heading Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isArabic) "اتجاه الهاتف" else "Phone Heading",
                            color = TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${String.format(Locale.US, "%.0f", trueAzimuth)}°",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

        // Manual Location Picker Dialog
        if (showCityDialog) {
            Dialog(onDismissRequest = { showCityDialog = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = SlateSurface
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isArabic) "تغيير موقع تحديد القبلة" else "Set Calculation Location",
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isArabic) "اختر مدينة جاهزة:" else "Select a Preset City:",
                            color = TextLight,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // List of cities
                        LazyColumn(
                            modifier = Modifier
                                .height(200.dp)
                                .fillMaxWidth()
                                .border(1.dp, TextMuted.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        ) {
                            items(PRESET_CITIES) { city ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            manualLatitude = city.latitude
                                            manualLongitude = city.longitude
                                            manualCityName = city.nameEn
                                            manualCityNameAr = city.nameAr
                                            useManualLocation = true
                                            showCityDialog = false
                                            centerTrigger++
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isArabic) city.nameAr else city.nameEn,
                                        color = TextLight,
                                        fontSize = 14.sp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (useManualLocation) {
                            Button(
                                onClick = {
                                    useManualLocation = false
                                    showCityDialog = false
                                    centerTrigger++
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecondary)
                            ) {
                                Text(
                                    text = if (isArabic) "إعادة التفعيل التلقائي بالـ GPS" else "Restore GPS Auto Mode",
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = { showCityDialog = false }) {
                            Text(text = if (isArabic) "إغلاق" else "Close", color = TextMuted)
                        }
                    }
                }
            }
        }

        if (showSettingsDialog) {
            QiblaSettingsDialog(
                isArabic = isArabic,
                selectedFontOption = selectedFontOption,
                selectedFontSizeOption = selectedFontSizeOption,
                onApply = { fontOption, fontSizeOption ->
                    selectedFontOption = fontOption
                    selectedFontSizeOption = fontSizeOption
                    settingsPreferences.edit()
                        .putString("font_option", fontOption.key)
                        .putString("font_size_option", fontSizeOption.key)
                        .apply()
                    showSettingsDialog = false
                },
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

@Composable
private fun QiblaSettingsDialog(
    isArabic: Boolean,
    selectedFontOption: AppFontOption,
    selectedFontSizeOption: AppFontSizeOption,
    onApply: (AppFontOption, AppFontSizeOption) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingFontOption by remember(selectedFontOption) { mutableStateOf(selectedFontOption) }
    var pendingFontSizeOption by remember(selectedFontSizeOption) { mutableStateOf(selectedFontSizeOption) }
    val fontSizeOptions = AppFontSizeOption.entries
    val selectedFontSizeIndex = fontSizeOptions.indexOf(pendingFontSizeOption).coerceAtLeast(0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            color = SlateSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, WarmGold.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "الإعدادات" else "Settings",
                        color = GoldPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isArabic) "نوع الخط" else "Font family",
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppFontOption.entries.chunked(2).forEach { rowOptions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowOptions.forEach { option ->
                                    val isSelected = option == pendingFontOption

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clickable { pendingFontOption = option },
                                        color = if (isSelected) EmeraldSecondary.copy(alpha = 0.65f) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) MintGlow.copy(alpha = 0.55f) else WarmGold.copy(alpha = 0.18f)
                                        )
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = option.displayName(isArabic),
                                                color = if (isSelected) MintGlow else TextLight,
                                                fontFamily = option.family,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                if (rowOptions.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "حجم الخط" else "Font size",
                            color = TextMuted,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = pendingFontSizeOption.displayName(isArabic),
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Slider(
                        value = selectedFontSizeIndex.toFloat(),
                        onValueChange = { value ->
                            val index = value.roundToInt().coerceIn(0, fontSizeOptions.lastIndex)
                            pendingFontSizeOption = fontSizeOptions[index]
                        },
                        valueRange = 0f..fontSizeOptions.lastIndex.toFloat(),
                        steps = (fontSizeOptions.size - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = GoldPrimary,
                            activeTrackColor = GoldPrimary,
                            inactiveTrackColor = TextMuted.copy(alpha = 0.35f)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = fontSizeOptions.first().displayName(isArabic), color = TextMuted, fontSize = 11.sp)
                        Text(text = fontSizeOptions.last().displayName(isArabic), color = TextMuted, fontSize = 11.sp)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = EmeraldSecondary.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MintGlow.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = if (isArabic) "بسم الله الرحمن الرحيم" else "Sample Qibla text",
                        modifier = Modifier.padding(10.dp),
                        color = TextLight,
                        fontFamily = pendingFontOption.family,
                        fontSize = (16f * pendingFontSizeOption.scale).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, WarmGold.copy(alpha = 0.16f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isArabic) "Qibla - الإصدار ${BuildConfig.VERSION_NAME}" else "Qibla - Version ${BuildConfig.VERSION_NAME}",
                            color = TextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isArabic) "برمجة: علي الغانم" else "Developer: Ali Alghanim",
                            color = TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "© 2026 Ali Alghanim. All rights reserved.",
                            color = TextMuted,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                TextButton(
                    onClick = { onApply(pendingFontOption, pendingFontSizeOption) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(text = if (isArabic) "تم" else "Done", color = GoldPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun normalizeDegrees(degrees: Float): Float {
    var normalized = degrees % 360f
    if (normalized < 0f) {
        normalized += 360f
    }
    return normalized
}

@Composable
fun CompassView(
    compassRotation: Float,
    qiblaBearing: Float,
    isAligned: Boolean,
    isArabic: Boolean,
    gpsCoordinatesAvailable: Boolean
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = Math.min(size.width, size.height) / 2f
        
        // 1. Draw glowing background if aligned
        if (isAligned) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(MintGlow.copy(alpha = 0.35f), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // 2. Draw outer boundary ring
        drawCircle(
            color = if (isAligned) MintGlow else GoldPrimary,
            radius = radius,
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )
        
        // Secondary subtle inner rings
        drawCircle(
            color = WarmGold.copy(alpha = 0.2f),
            radius = radius * 0.75f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = WarmGold.copy(alpha = 0.1f),
            radius = radius * 0.5f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        // Rotate scope according to compass values
        // Compass card rotation matches compass heading
        rotate(degrees = compassRotation, pivot = center) {
            // Draw Cardinal Directions
            val textPaint = android.graphics.Paint().apply {
                color = if (isAligned) android.graphics.Color.parseColor("#00FFCC") else android.graphics.Color.parseColor("#D4AF37")
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 18.dp.toPx()
                isFakeBoldText = true
            }

            // Draw labels N, E, S, W
            drawContext.canvas.nativeCanvas.drawText(
                if (isArabic) "ش" else "N",
                center.x,
                center.y - radius + 24.dp.toPx(),
                textPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                if (isArabic) "ج" else "S",
                center.x,
                center.y + radius - 12.dp.toPx(),
                textPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                if (isArabic) "ق" else "E",
                center.x + radius - 16.dp.toPx(),
                center.y + 6.dp.toPx(),
                textPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                if (isArabic) "غ" else "W",
                center.x - radius + 18.dp.toPx(),
                center.y + 6.dp.toPx(),
                textPaint
            )

            // Draw degree ticks (every 10 degrees)
            for (angle in 0 until 360 step 10) {
                if (angle % 90 == 0) continue // Skip cardinal directions
                
                val angleRad = Math.toRadians(angle.toDouble())
                val tickLength = if (angle % 30 == 0) 12.dp.toPx() else 6.dp.toPx()
                val tickWidth = if (angle % 30 == 0) 2.dp.toPx() else 1.dp.toPx()
                
                val startX = center.x + (radius - 8.dp.toPx()) * sin(angleRad).toFloat()
                val startY = center.y - (radius - 8.dp.toPx()) * cos(angleRad).toFloat()
                
                val endX = center.x + (radius - 8.dp.toPx() - tickLength) * sin(angleRad).toFloat()
                val endY = center.y - (radius - 8.dp.toPx() - tickLength) * cos(angleRad).toFloat()

                drawLine(
                    color = WarmGold.copy(alpha = 0.5f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickWidth
                )
            }

            // Draw Kaaba icon on the compass card outer perimeter at the Qibla Bearing angle
            if (gpsCoordinatesAvailable) {
                val qiblaRad = Math.toRadians(qiblaBearing.toDouble())
                val kaabaX = center.x + (radius - 32.dp.toPx()) * sin(qiblaRad).toFloat()
                val kaabaY = center.y - (radius - 32.dp.toPx()) * cos(qiblaRad).toFloat()

                // Draw Kaaba representation on Canvas at Qibla coordinates
                rotate(degrees = qiblaBearing, pivot = Offset(kaabaX, kaabaY)) {
                    val kaabaSize = 24.dp.toPx()
                    // Draw black block
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(kaabaX - kaabaSize / 2f, kaabaY - kaabaSize / 2f),
                        size = Size(kaabaSize, kaabaSize)
                    )
                    // Draw golden belt (Kiswah decoration)
                    drawRect(
                        color = GoldPrimary,
                        topLeft = Offset(kaabaX - kaabaSize / 2f, kaabaY - kaabaSize / 4f),
                        size = Size(kaabaSize, kaabaSize / 6f)
                    )
                }
            }
        }

        // 3. Draw stationary needle pointing to Mecca relative to the top of the phone
        // The needle points at: qiblaBearing - azimuth (which is: qiblaBearing + compassRotation)
        val relativeAngle = qiblaBearing + compassRotation
        
        rotate(degrees = relativeAngle, pivot = center) {
            val needlePath = Path().apply {
                // North pointing triangular needle (Top pointing)
                moveTo(center.x, center.y - radius + 36.dp.toPx())
                lineTo(center.x - 12.dp.toPx(), center.y)
                lineTo(center.x, center.y + 16.dp.toPx())
                close()
            }
            
            drawPath(
                path = needlePath,
                brush = Brush.verticalGradient(
                    colors = if (isAligned) {
                        listOf(MintGlow, MintGlow.copy(alpha = 0.6f))
                    } else {
                        listOf(GoldPrimary, WarmGold)
                    }
                )
            )

            val needleBottomPath = Path().apply {
                moveTo(center.x, center.y - radius + 36.dp.toPx())
                lineTo(center.x + 12.dp.toPx(), center.y)
                lineTo(center.x, center.y + 16.dp.toPx())
                close()
            }

            drawPath(
                path = needleBottomPath,
                color = if (isAligned) MintGlow.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.25f)
            )
            
            // Draw a circle in the center of the compass
            drawCircle(
                color = if (isAligned) MintGlow else GoldPrimary,
                radius = 8.dp.toPx(),
                center = center
            )
            drawCircle(
                color = SlateBackground,
                radius = 3.dp.toPx(),
                center = center
            )
        }
    }
}


