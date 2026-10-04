package com.guardian.child.enforce

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.guardian.child.GuardianApp
import com.guardian.child.R

/**
 * Foreground service that keeps the app alive for enforcement and drives periodic
 * location reporting. A persistent (low-importance) notification is required by
 * Android and also keeps the child honestly informed that protection is active.
 */
class GuardianForegroundService : Service() {

    private lateinit var location: LocationReporter

    override fun onCreate() {
        super.onCreate()
        location = LocationReporter(this)
        startForeground(1, buildNotification())
        location.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, GuardianApp.CHANNEL)
            .setContentTitle("Guardian is active")
            .setContentText("Protection and limits set by your parent are on.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

    override fun onDestroy() { location.stop(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
