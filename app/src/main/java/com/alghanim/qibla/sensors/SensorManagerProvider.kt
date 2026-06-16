package com.alghanim.qibla.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SensorManagerProvider(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    // Sensor definitions
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    // Flow states
    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth

    private val _pitch = MutableStateFlow(0f)
    val pitch: StateFlow<Float> = _pitch

    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll

    private val _accuracy = MutableStateFlow(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
    val accuracy: StateFlow<Int> = _accuracy

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var isRotationSensorSupported = rotationSensor != null

    fun startListening() {
        if (isRotationSensorSupported) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, magnetSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        _accuracy.value = event.accuracy
        
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            publishOrientation(rotationMatrix)
        } else {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, accelerometerReading, 0, event.values.size)
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, magnetometerReading, 0, event.values.size)
            }

            val rotationMatrix = FloatArray(9)
            val inclinationMatrix = FloatArray(9)
            val success = SensorManager.getRotationMatrix(
                rotationMatrix,
                inclinationMatrix,
                accelerometerReading,
                magnetometerReading
            )
            
            if (success) {
                publishOrientation(rotationMatrix)
            }
        }
    }

    private fun publishOrientation(rotationMatrix: FloatArray) {
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        val azDeg = normalizeDegrees(Math.toDegrees(orientation[0].toDouble()).toFloat())
        val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()

        _azimuth.value = azDeg
        _pitch.value = pitchDeg
        _roll.value = rollDeg
    }

    private fun normalizeDegrees(degrees: Float): Float {
        var normalized = degrees % 360f
        if (normalized < 0f) {
            normalized += 360f
        }
        return normalized
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        _accuracy.value = accuracy
    }
}
