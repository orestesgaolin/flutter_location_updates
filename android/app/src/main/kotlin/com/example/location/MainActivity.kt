package com.example.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel


interface LocationUpdateListener {
    fun onLocationUpdate(location: android.location.Location)
}

class MainActivity : FlutterActivity(), LocationUpdateListener {
    companion object {
        private const val LOG_TAG = "MainActivity"
    }

    private var locationForegroundService: LocationForegroundService? = null
    private var serviceConnection: ServiceConnection? = null
    private var eventSink: EventChannel.EventSink? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // method channel for start and stop functions
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "foreground_service"
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "start" -> {
                    startLocationService()
                    result.success("Location service started")
                }

                "stop" -> {
                    stopLocationService()
                    result.success("Location service stopped")
                }

                else -> {
                    result.notImplemented()
                }
            }
        }

        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "foreground_service_location"
        ).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    Log.d(LOG_TAG, "Event channel listener added")
                    eventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    Log.d(LOG_TAG, "Event channel listener removed")
                    eventSink = null
                }
            }
        )
    }

    private fun startLocationService() {
        Log.d(LOG_TAG, "Starting foreground service")

        val serviceIntent = Intent(context, LocationForegroundService::class.java)
        val message = "Waiting for the location updates";
        ForegroundServiceNotification.createNotification(context, serviceIntent, message)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        serviceConnection = createServiceConnection()
        context.bindService(serviceIntent, serviceConnection!!, Context.BIND_AUTO_CREATE)

        Log.d(LOG_TAG, "Foreground service started")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_TAG, "Destroying MainActivity")
        locationForegroundService?.let {
            serviceConnection?.let { connection ->
                context.unbindService(connection)
                serviceConnection = null
            }
            locationForegroundService = null
        }
    }

    private fun stopLocationService() {
        Log.d(LOG_TAG, "Stopping foreground service")

        locationForegroundService?.stopService()
        locationForegroundService = null
        serviceConnection?.let {
            context.unbindService(it)
            serviceConnection = null
        }

        Log.d(LOG_TAG, "Foreground service stopped")
    }

    private fun createServiceConnection(): ServiceConnection {
        return object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                locationForegroundService =
                    (service as LocationForegroundService.LocalBinder).getInstance()
                locationForegroundService?.setLocationUpdateListener(this@MainActivity)
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                locationForegroundService = null
            }
        }
    }

    override fun onLocationUpdate(location: android.location.Location) {
        Log.d(LOG_TAG, "Location update received: ${location.latitude}, ${location.longitude}")
        eventSink?.success(location.toString())
    }
}


class LocationForegroundService : android.app.Service() {
    companion object {
        private const val LOG_TAG = "LocationFS"
        private const val SERVICE_ID = 1
    }

    private val binder = LocalBinder()

    private var listener: LocationUpdateListener? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    fun setLocationUpdateListener(listener: LocationUpdateListener) {
        this.listener = listener
    }

    private fun sendLocationUpdate(location: android.location.Location) {
        listener?.onLocationUpdate(location)
        ForegroundServiceNotification.updateNotification(
            applicationContext,
            "Location: ${location.latitude}, ${location.longitude}",
        )
    }

    override fun onBind(p0: Intent?): IBinder? = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "Starting service")

        ForegroundServiceNotification.createNotificationChannel(applicationContext)
        val notification =
            ForegroundServiceNotification.createNotification(
                applicationContext,
                intent!!,
                "Service started"
            )
        startForeground(SERVICE_ID, notification)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted
            Log.d(LOG_TAG, "Location permission not granted")
            return super.onStartCommand(intent, flags, startId)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d(LOG_TAG, "Location update received")
                locationResult.locations.forEach { location ->
                    Log.d(LOG_TAG, "Location: ${location.latitude}, ${location.longitude}")
                    sendLocationUpdate(location)
                }
            }
        }

        val locationRequest = LocationRequest.Builder(5000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        return super.onStartCommand(intent, flags, startId)
    }

    fun stopService() {
        Log.d(LOG_TAG, "Stopping service")
        fusedLocationClient.removeLocationUpdates(locationCallback)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()

        Log.d(LOG_TAG, "Service stopped")
    }

    inner class LocalBinder : Binder() {
        fun getInstance(): LocationForegroundService = this@LocationForegroundService
    }
}


class ForegroundServiceNotification {
    companion object {
        private const val LOG_TAG = "ServiceNotification"
        private const val TITLE_TAG = "ServiceNotificationTitle"
        private const val CHANNEL_ID = "ServiceNotification"
        private const val NOTIFICATION_ID = 1

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel1 = NotificationChannel(
                    CHANNEL_ID,
                    "Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
                )
                val manager: NotificationManager = context.getSystemService(
                    NotificationManager::class.java
                )
                manager.createNotificationChannel(channel1)
            }
        }

        fun createNotification(
            context: Context,
            intent: Intent,
            notification: String
        ): Notification? {
            val pendingIntent = getPendingIntentGoToForeground(context)
            intent.putExtra(TITLE_TAG, notification)

            return getBuilder(context, notification)
                .setContentIntent(pendingIntent)
                .build()
        }

        fun updateNotification(context: Context, notification: String) {
            val pendingIntent = getPendingIntentGoToForeground(context)
            val builder = getBuilder(context, notification)
                .setFullScreenIntent(pendingIntent, true)

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }

        private fun getBuilder(context: Context, notification: String): NotificationCompat.Builder {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_service)
                .setOnlyAlertOnce(true)
                .setTicker(notification)

            builder.setContentTitle(notification)

            return builder
        }


        private fun getPendingIntentGoToForeground(context: Context?): PendingIntent? {
            val intent = Intent(context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            var flags = FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 23) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }

            return PendingIntent.getActivity(context, 0, intent, flags)
        }
    }
}
