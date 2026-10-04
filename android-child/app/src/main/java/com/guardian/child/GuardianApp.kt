package com.guardian.child

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.*
import com.guardian.child.sync.SyncWorker
import java.util.concurrent.TimeUnit

class GuardianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
        scheduleSync()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Guardian protection", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    /** Pull policy + push a heartbeat roughly every 15 minutes (WorkManager minimum). */
    private fun scheduleSync() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("guardian-sync", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    companion object { const val CHANNEL = "guardian" }
}
