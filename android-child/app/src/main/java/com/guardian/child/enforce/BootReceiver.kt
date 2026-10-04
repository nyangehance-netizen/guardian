package com.guardian.child.enforce

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts enforcement + location reporting after the phone reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, GuardianForegroundService::class.java))
        }
    }
}
