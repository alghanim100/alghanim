package com.alghanim.qibla.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.preference.PreferenceManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.location.Location

private class MapViewState {
    var hasCenteredInitial = false
    var lastCenterTrigger = 0
    var lastLat: Double? = null
    var lastLon: Double? = null
}

enum class QiblaMapType {
    SATELLITE, TERRAIN
}

val googleHybridSource = object : OnlineTileSourceBase(
    "GoogleHybrid", 0, 20, 256, ".png",
    arrayOf("https://mt0.google.com/vt/lyrs=y&hl=en&x=")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + MapTileIndex.getX(pMapTileIndex) +
                "&y=" + MapTileIndex.getY(pMapTileIndex) +
                "&z=" + MapTileIndex.getZoom(pMapTileIndex)
    }
}

val googleTerrainSource = object : OnlineTileSourceBase(
    "GoogleTerrain", 0, 20, 256, ".png",
    arrayOf("https://mt0.google.com/vt/lyrs=p&hl=en&x=")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + MapTileIndex.getX(pMapTileIndex) +
                "&y=" + MapTileIndex.getY(pMapTileIndex) +
                "&z=" + MapTileIndex.getZoom(pMapTileIndex)
    }
}

@Composable
fun NativeQiblaMapView(
    latitude: Double?,
    longitude: Double?,
    azimuth: Float,
    mapType: QiblaMapType = QiblaMapType.SATELLITE,
    centerTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapState = remember { MapViewState() }
    
    // Initialize OsmDroid configuration once
    remember {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
        true
    }
    
    val kaabaLocation = GeoPoint(21.422487, 39.826206)

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                // Hide default zoom buttons
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setMultiTouchControls(true)
                
                // Set initial view
                controller.setZoom(16.0)
                if (latitude != null && longitude != null) {
                    controller.setCenter(GeoPoint(latitude, longitude))
                } else {
                    controller.setCenter(GeoPoint(24.7136, 46.6753)) // Default Riyadh
                }
                
                // Create Kaaba Marker
                val kaabaMarker = Marker(this).apply {
                    id = "kaaba"
                    position = kaabaLocation
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createKaabaDrawable(ctx)
                    infoWindow = null // Disable click popup
                }
                overlays.add(kaabaMarker)
                
                // Create User Marker
                val userMarker = Marker(this).apply {
                    id = "user"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createUserDrawable(ctx)
                    infoWindow = null
                    alpha = 0f // Keep it invisible until we have a location
                }
                overlays.add(userMarker)
                
                // Create Qibla Line
                val qiblaLine = Polyline(this).apply {
                    id = "line"
                    outlinePaint.color = Color.parseColor("#00FFCC")
                    outlinePaint.strokeWidth = 10f
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 20f), 0f)
                }
                overlays.add(qiblaLine)
            }
        },
        update = { mapView ->
            // Update Map Tile Source ONLY if it changed to prevent flickering
            val newSource = if (mapType == QiblaMapType.SATELLITE) googleHybridSource else googleTerrainSource
            if (mapView.tileProvider.tileSource != newSource) {
                mapView.setTileSource(newSource)
            }
            
            if (latitude != null && longitude != null) {
                val userLocation = GeoPoint(latitude, longitude)
                
                // Find and update user marker
                val userMarker = mapView.overlays.find { (it as? Marker)?.id == "user" } as? Marker
                userMarker?.let {
                    if (it.position != userLocation) {
                        it.position = userLocation
                    }
                    if (it.alpha != 1f) {
                        it.alpha = 1f
                    }
                }
                
                // Find and update Qibla Line
                val qiblaLine = mapView.overlays.find { (it as? Polyline)?.id == "line" } as? Polyline
                if (qiblaLine != null && qiblaLine.actualPoints.firstOrNull() != userLocation) {
                    qiblaLine.setPoints(listOf(userLocation, kaabaLocation))
                }
                
                var shouldCenter = false
                
                if (!mapState.hasCenteredInitial) {
                    shouldCenter = true
                    mapState.hasCenteredInitial = true
                } else if (centerTrigger != mapState.lastCenterTrigger) {
                    shouldCenter = true
                    mapState.lastCenterTrigger = centerTrigger
                } else if (mapState.lastLat != null && mapState.lastLon != null) {
                    val dist = FloatArray(1)
                    Location.distanceBetween(mapState.lastLat!!, mapState.lastLon!!, latitude, longitude, dist)
                    if (dist[0] > 10000) {
                        shouldCenter = true
                    }
                }
                
                if (shouldCenter) {
                    mapView.controller.animateTo(userLocation)
                }
                
                mapState.lastLat = latitude
                mapState.lastLon = longitude
                
                // Apply Map Rotation
                // Note: The 'azimuth' passed here is actually `animatedCompassRotation` which is ALREADY -realAzimuth.
                if (mapView.mapOrientation != azimuth) {
                    mapView.mapOrientation = azimuth
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

// Helper function to create custom Kaaba Icon Drawable
private fun createKaabaDrawable(context: Context): android.graphics.drawable.Drawable {
    val size = 60
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // Black Box
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 8f, 8f, paint)
    
    // Gold Border
    paint.apply {
        color = Color.parseColor("#D4AF37")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 8f, 8f, paint)
    
    // Gold line across
    paint.apply {
        style = Paint.Style.FILL
    }
    canvas.drawRect(0f, 15f, size.toFloat(), 22f, paint)
    
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

// Helper function to create custom User Icon Drawable
private fun createUserDrawable(context: Context): android.graphics.drawable.Drawable {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    
    // Outer glow
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4000FFCC")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center, paint)
    
    // Inner cyan circle
    paint.color = Color.parseColor("#00FFCC")
    canvas.drawCircle(center, center, center - 8f, paint)
    
    // White border
    paint.apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(center, center, center - 8f, paint)
    
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}
