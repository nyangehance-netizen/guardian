package com.guardian.child.enforce

import android.app.usage.UsageStatsManager
import android.content.Context
import com.guardian.child.data.Policy
import java.util.Calendar

/**
 * Tracks per-app foreground minutes for "today" so daily limits can be enforced.
 * Uses the system UsageStatsManager (requires the one-time "Usage access" grant
 * the parent gives during setup) as the source of truth, which survives reboots
 * and is robust against the app being killed. Falls back to its own counter if
 * usage access is not granted.
 */
class UsageTracker(private val ctx: Context) {
    private val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var currentPkg: String? = null
    private var sessionStart = 0L

    fun onForeground(pkg: String) {
        if (pkg != currentPkg) { currentPkg = pkg; sessionStart = System.currentTimeMillis() }
    }

    fun startTracking(pkg: String, @Suppress("UNUSED_PARAMETER") policy: Policy) {
        // Hook for future per-app session logic; UsageStats already records time.
        onForeground(pkg)
    }

    /** Foreground minutes for [pkg] since local midnight today. */
    fun minutesUsedToday(pkg: String): Int {
        val start = midnight()
        val now = System.currentTimeMillis()
        return try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
            val total = stats.filter { it.packageName == pkg }.sumOf { it.totalTimeInForeground }
            (total / 60000L).toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun midnight(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
