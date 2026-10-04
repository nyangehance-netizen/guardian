package com.guardian.child.enforce

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.guardian.child.data.PolicyStore
import com.guardian.child.net.ApiClient
import kotlin.concurrent.thread

/**
 * Periodically sends a heartbeat (battery + location + foreground app hint) to the
 * backend so the parent dashboard shows live status and location. Uses the platform
 * LocationManager (no Google Play Services dependency).
 */
class LocationReporter(private val ctx: Context) : LocationListener {
    private val store = PolicyStore(ctx)
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var last: Location? = null

    fun start() {
        if (!hasLocationPermission()) { beat(); return }
        val intervalMs = store.policy().locationIntervalSec() * 1000L
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 25f, this)
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, intervalMs, 25f, this)
            last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        beat()
    }

    fun stop() = runCatching { lm.removeUpdates(this) }.let {}

    override fun onLocationChanged(loc: Location) { last = loc; beat() }

    private fun beat() {
        val secret = store.deviceSecret ?: return
        val battery = (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val loc = last
        thread {
            runCatching {
                ApiClient.heartbeat(secret, battery, loc?.latitude, loc?.longitude, null, store.policyRev)
            }
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
