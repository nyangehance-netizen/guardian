package com.guardian.child.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local persistence for enrollment credentials and the last-known policy.
 * Stored in SharedPreferences. The policy is cached so enforcement keeps working
 * offline; the SyncWorker refreshes it whenever the device has network.
 */
class PolicyStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("guardian", Context.MODE_PRIVATE)

    var deviceId: String?
        get() = prefs.getString("device_id", null)
        set(v) = prefs.edit().putString("device_id", v).apply()

    var deviceSecret: String?
        get() = prefs.getString("device_secret", null)
        set(v) = prefs.edit().putString("device_secret", v).apply()

    var childName: String?
        get() = prefs.getString("child_name", null)
        set(v) = prefs.edit().putString("child_name", v).apply()

    val isEnrolled: Boolean get() = deviceSecret != null

    var policyRev: Int
        get() = prefs.getInt("policy_rev", 0)
        set(v) = prefs.edit().putInt("policy_rev", v).apply()

    var policyJson: String
        get() = prefs.getString("policy_json", "{}") ?: "{}"
        set(v) = prefs.edit().putString("policy_json", v).apply()

    fun policy(): Policy = Policy(JSONObject(policyJson))

    fun saveEnrollment(id: String, secret: String, name: String, policy: JSONObject, rev: Int) {
        deviceId = id; deviceSecret = secret; childName = name
        policyJson = policy.toString(); policyRev = rev
    }
}

/** Typed view over the policy JSON the backend sends. */
class Policy(private val o: JSONObject) {
    private val webFilter get() = o.optJSONObject("webFilter") ?: JSONObject()
    private val appRules get() = o.optJSONObject("appRules") ?: JSONObject()
    private val schedules get() = o.optJSONObject("schedules") ?: JSONObject()

    val webFilterEnabled get() = webFilter.optBoolean("enabled", false)
    val safeSearch get() = webFilter.optBoolean("safeSearch", false)
    val blockCategories get() = webFilter.optJSONArray("blockCategories").toStringList()
    val blocklist get() = webFilter.optJSONArray("blocklist").toStringList()
    val allowlist get() = webFilter.optJSONArray("allowlist").toStringList()

    val blockedApps get() = appRules.optJSONArray("blocked").toStringList()

    /** package -> minutes allowed per day */
    fun appLimits(): Map<String, Int> {
        val m = HashMap<String, Int>()
        val limits = appRules.optJSONObject("limits") ?: return m
        for (k in limits.keys()) m[k] = limits.optInt(k)
        return m
    }

    fun schedule(name: String): Schedule? {
        val s = schedules.optJSONObject(name) ?: return null
        if (!s.optBoolean("enabled", false)) return null
        val days = s.optJSONArray("days").toStringList().mapNotNull { it.toIntOrNull() }.toSet()
        return Schedule(s.optString("start", "00:00"), s.optString("end", "00:00"), days)
    }

    fun locationIntervalSec(): Int =
        (o.optJSONObject("location")?.optInt("reportIntervalSec", 300)) ?: 300
}

data class Schedule(val start: String, val end: String, val days: Set<Int>)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }.filter { it.isNotEmpty() }
}
