package com.alghanim.qibla.data

import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.tan
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.pow

object QiblaCalculator {
    // Kaaba coordinates (Mecca, Saudi Arabia)
    const val KAABA_LATITUDE = 21.422487
    const val KAABA_LONGITUDE = 39.826206
    
    /**
     * Calculates the Qibla bearing (angle in degrees from true North, 0 to 360)
     * from the user's current latitude and longitude.
     */
    fun calculateQiblaBearing(latitude: Double, longitude: Double): Double {
        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)
        val kaabaLatRad = Math.toRadians(KAABA_LATITUDE)
        val kaabaLonRad = Math.toRadians(KAABA_LONGITUDE)

        val deltaLon = kaabaLonRad - lonRad

        val y = sin(deltaLon)
        val x = cos(latRad) * tan(kaabaLatRad) - sin(latRad) * cos(deltaLon)

        var qiblaAngle = Math.toDegrees(atan2(y, x))
        qiblaAngle = (qiblaAngle + 360) % 360
        return qiblaAngle
    }

    /**
     * Calculates the distance in kilometers from the user's current location to the Kaaba.
     */
    fun calculateDistanceToKaaba(latitude: Double, longitude: Double): Double {
        val earthRadius = 6371.0 // kilometers
        val latRad1 = Math.toRadians(latitude)
        val lonRad1 = Math.toRadians(longitude)
        val latRad2 = Math.toRadians(KAABA_LATITUDE)
        val lonRad2 = Math.toRadians(KAABA_LONGITUDE)

        val dLat = latRad2 - latRad1
        val dLon = lonRad2 - lonRad1

        val a = sin(dLat / 2).pow(2.0) +
                cos(latRad1) * cos(latRad2) *
                sin(dLon / 2).pow(2.0)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
