package com.alghanim.qibla.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

class LocationProvider(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Location States
    private val _latitude = MutableStateFlow<Double?>(null)
    val latitude: StateFlow<Double?> = _latitude

    private val _longitude = MutableStateFlow<Double?>(null)
    val longitude: StateFlow<Double?> = _longitude

    private val _locationAccuracy = MutableStateFlow(0f)
    val locationAccuracy: StateFlow<Float> = _locationAccuracy

    private val _cityName = MutableStateFlow<String?>(null)
    val cityName: StateFlow<String?> = _cityName

    // Satellite States
    private val _satellitesInView = MutableStateFlow(0)
    val satellitesInView: StateFlow<Int> = _satellitesInView

    private val _satellitesUsed = MutableStateFlow(0)
    val satellitesUsed: StateFlow<Int> = _satellitesUsed

    private var isListening = false

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val count = status.satelliteCount
            var used = 0
            for (i in 0 until count) {
                if (status.usedInFix(i)) {
                    used++
                }
            }
            _satellitesInView.value = count
            _satellitesUsed.value = used
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (isListening) return
        
        try {
            // Register for satellite updates
            locationManager.registerGnssStatusCallback(gnssStatusCallback, Handler(Looper.getMainLooper()))
            
            // Register for GPS Updates (fine accuracy)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L, // 2 seconds
                    1f,    // 1 meter
                    this
                )
            }
            
            // Register for Network Updates (coarse accuracy fallback)
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    1f,
                    this
                )
            }
            
            // Get last known location for immediate setup
            val lastGpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLoc = if (lastGpsLoc != null && lastNetLoc != null) {
                if (lastGpsLoc.accuracy < lastNetLoc.accuracy) lastGpsLoc else lastNetLoc
            } else {
                lastGpsLoc ?: lastNetLoc
            }
            
            bestLoc?.let {
                _latitude.value = it.latitude
                _longitude.value = it.longitude
                _locationAccuracy.value = it.accuracy
                resolveCityName(it.latitude, it.longitude)
            }
            
            isListening = true
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(this)
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
            isListening = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        _latitude.value = location.latitude
        _longitude.value = location.longitude
        _locationAccuracy.value = location.accuracy
        resolveCityName(location.latitude, location.longitude)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    private fun resolveCityName(lat: Double, lng: Double) {
        coroutineScope.launch {
            try {
                // Determine layout locale or system locale
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        if (addresses.isNotEmpty()) {
                            val address = addresses[0]
                            val city = address.locality ?: address.subAdminArea ?: address.adminArea
                            val country = address.countryName
                            val result = if (city != null) "$city, $country" else country ?: "Unknown Location"
                            _cityName.value = result
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val city = address.locality ?: address.subAdminArea ?: address.adminArea
                        val country = address.countryName
                        val result = if (city != null) "$city, $country" else country ?: "Unknown Location"
                        _cityName.value = result
                    }
                }
            } catch (e: IOException) {
                // In case network issue blocks Geocoder, set to null so UI displays coordinates
                _cityName.value = null
            }
        }
    }
}
