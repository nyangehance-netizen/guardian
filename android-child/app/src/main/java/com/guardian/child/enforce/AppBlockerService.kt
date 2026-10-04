package com.guardian.child.enforce

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.guardian.child.data.Policy
import com.guardian.child.data.PolicyStore
import com.guardian.child.data.Schedule
import com.guardian.child.net.ApiClient
import com.guardian.child.ui.BlockActivity
import java.util.Calendar
import kotlin.concurrent.thread

/**
 * The real-time enforcer. Android delivers a window-state-changed event whenever a
 * new app comes to the foreground; we check that app against the current policy and,
 * if it must be blocked, immediately send the user home and show a full-screen
 * BlockActivity. Reasons it may block:
 *   - app is on the always-blocked list
 *   - a schedule (bedtime / school) is currently active
 *   - the app's daily time limit has been used up (tracked by UsageTracker)
 */
class AppBlockerService : AccessibilityService() {

    private lateinit var store: PolicyStore
    private lateinit var usage: UsageTracker
    private var lastBlockedPkg: String? = null
    private var lastBlockAt = 0L

    override fun onServiceConnected() {
        store = PolicyStore(this)
        usage = UsageTracker(this)
        // Make sure the keep-alive + location service is running.
        startService(Intent(this, GuardianForegroundService::class.java))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg.startsWith("com.android.systemui")) return
        if (!store.isEnrolled) return

        val policy = store.policy()
        usage.onForeground(pkg)

        val reason = blockReason(pkg, policy) ?: run { usage.startTracking(pkg, policy); return }
        block(pkg, reason)
    }

    private fun blockReason(pkg: String, policy: Policy): String? {
        if (pkg in policy.blockedApps) return "app"
        activeSchedule(policy)?.let { return it }           // "bedtime" / "school"
        val limit = policy.appLimits()[pkg]
        if (limit != null && usage.minutesUsedToday(pkg) >= limit) return "limit"
        return null
    }

    private fun activeSchedule(policy: Policy): String? {
        for (name in listOf("bedtime", "school")) {
            val s = policy.schedule(name) ?: continue
            if (isNow(s)) return name
        }
        return null
    }

    private fun block(pkg: String, reason: String) {
        // Debounce so we don't spam while the user is bounced back.
        val nowMs = System.currentTimeMillis()
        if (pkg == lastBlockedPkg && nowMs - lastBlockAt < 1500) return
        lastBlockedPkg = pkg; lastBlockAt = nowMs

        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("reason", reason)
            putExtra("pkg", pkg)
        })

        val secret = store.deviceSecret ?: return
        val kind = if (reason == "limit") "limit_reached" else "blocked_app"
        thread { runCatching { ApiClient.reportEvent(secret, kind, "$reason:$pkg") } }
    }

    override fun onInterrupt() {}

    companion object {
        /** HH:mm window check, handles windows that cross midnight (e.g. 21:00–07:00). */
        fun isNow(s: Schedule): Boolean {
            val cal = Calendar.getInstance()
            val dow = (cal.get(Calendar.DAY_OF_WEEK) - 1) // 0=Sun..6=Sat
            if (s.days.isNotEmpty() && dow !in s.days) return false
            val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val start = toMins(s.start); val end = toMins(s.end)
            return if (start <= end) mins in start until end else (mins >= start || mins < end)
        }
        private fun toMins(hhmm: String): Int {
            val p = hhmm.split(":"); return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
        }
    }
}
