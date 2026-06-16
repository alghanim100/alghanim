package com.alghanim.qibla

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.alghanim.qibla.location.LocationProvider
import com.alghanim.qibla.sensors.SensorManagerProvider
import com.alghanim.qibla.ui.QiblaScreen
import com.alghanim.qibla.ui.theme.QiblaTheme

class MainActivity : ComponentActivity() {

    private lateinit var sensorManagerProvider: SensorManagerProvider
    private lateinit var locationProvider: LocationProvider

    // State holders for permissions
    private val hasLocationPermissionState = mutableStateOf(false)

    // Permission request launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            hasLocationPermissionState.value = true
            locationProvider.startLocationUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Providers
        sensorManagerProvider = SensorManagerProvider(this)
        locationProvider = LocationProvider(this)

        // Check initial permissions
        checkInitialPermissions()

        // Start location updates immediately if permission is already granted
        if (hasLocationPermissionState.value) {
            locationProvider.startLocationUpdates()
        }

        setContent {
            QiblaTheme {
                // Collect states from providers
                val azimuth by sensorManagerProvider.azimuth.collectAsState()
                val pitch by sensorManagerProvider.pitch.collectAsState()
                val roll by sensorManagerProvider.roll.collectAsState()
                val sensorAccuracy by sensorManagerProvider.accuracy.collectAsState()

                val gpsLatitude by locationProvider.latitude.collectAsState()
                val gpsLongitude by locationProvider.longitude.collectAsState()
                val gpsAccuracy by locationProvider.locationAccuracy.collectAsState()
                val resolvedCity by locationProvider.cityName.collectAsState()
                
                val satellitesInView by locationProvider.satellitesInView.collectAsState()
                val satellitesUsed by locationProvider.satellitesUsed.collectAsState()

                QiblaScreen(
                    azimuth = azimuth,
                    pitch = pitch,
                    roll = roll,
                    sensorAccuracy = sensorAccuracy,
                    gpsLatitude = gpsLatitude,
                    gpsLongitude = gpsLongitude,
                    gpsAccuracy = gpsAccuracy,
                    resolvedCity = resolvedCity,
                    satellitesInView = satellitesInView,
                    satellitesUsed = satellitesUsed,
                    hasLocationPermission = hasLocationPermissionState.value,
                    onRequestLocationPermission = { requestLocationPermission() }
                )
            }
        }
    }

    private fun checkInitialPermissions() {
        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasCoarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        hasLocationPermissionState.value = hasFine || hasCoarse
    }

    private fun requestLocationPermission() {
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    override fun onResume() {
        super.onResume()
        sensorManagerProvider.startListening()
        if (hasLocationPermissionState.value) {
            locationProvider.startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManagerProvider.stopListening()
        locationProvider.stopLocationUpdates()
    }
}