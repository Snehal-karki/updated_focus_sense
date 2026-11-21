package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.FocusSenseApplication
import com.example.MainActivity
import com.example.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            _isTrackingActive.value = false
            return START_NOT_STICKY
        }

        val app = application as? FocusSenseApplication
        val currentUser = app?.repository?.currentUser?.value

        // Only authenticated child devices are allowed to run location tracking service
        if (currentUser == null || currentUser.role != "child") {
            Log.w(TAG, "LocationTrackingService ignored: active user is not an authenticated child (${currentUser?.role})")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            _isTrackingActive.value = false
            return START_NOT_STICKY
        }

        val actualChildId = currentUser.userId
        startForeground(NOTIFICATION_ID, buildForegroundNotification(currentUser.name))
        _isTrackingActive.value = true

        startLocationUpdates(actualChildId)
        return START_STICKY
    }

    private fun buildForegroundNotification(childName: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = if (childName.isNotBlank()) childName else "Child"

        return NotificationCompat.Builder(this, FocusSenseApplication.CHANNEL_LOCATION)
            .setContentTitle("FocusSense Safety Active: $displayName")
            .setContentText("Real-time location monitoring is active for parental safety.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(childId: String) {
        val hasFine = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine) {
            Log.w(TAG, "ACCESS_FINE_LOCATION permission missing. Cannot collect GPS telemetry.")
            return
        }

        val app = application as? FocusSenseApplication

        // Location request every 5 minutes (300,000 ms) balanced for battery conservation
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 300000L)
            .setMinUpdateIntervalMillis(120000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val lastLoc = result.lastLocation ?: return
                scope.launch {
                    val placeLabel = com.example.util.LocationGeocoderHelper.resolvePlaceLabel(
                        applicationContext,
                        lastLoc.latitude,
                        lastLoc.longitude
                    )
                    app?.repository?.recordLocation(
                        childId = childId,
                        lat = lastLoc.latitude,
                        lng = lastLoc.longitude,
                        accuracy = lastLoc.accuracy,
                        locationName = placeLabel
                    )
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            mainLooper
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        _isTrackingActive.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LocationTrackingService"
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val EXTRA_CHILD_ID = "EXTRA_CHILD_ID"
        const val NOTIFICATION_ID = 2001

        private val _isTrackingActive = MutableStateFlow(false)
        val isTrackingActive = _isTrackingActive.asStateFlow()
    }
}
