package com.guardian.child.net

import com.guardian.child.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin HTTP client over the Guardian backend, using only the JDK + org.json.
 * All calls are blocking — invoke them from a background thread / coroutine /
 * WorkManager worker, never the main thread.
 */
object ApiClient {
    private val base = BuildConfig.API_BASE

    data class Enrollment(val deviceId: String, val deviceSecret: String, val childName: String, val policy: JSONObject, val rev: Int)

    /** Exchange a 6-digit pairing code for long-lived device credentials. */
    fun enroll(pairCode: String): Enrollment {
        val res = post("/api/enroll", JSONObject().put("pairCode", pairCode), auth = null)
        return Enrollment(
            res.getString("deviceId"),
            res.getString("deviceSecret"),
            res.optString("childName", "child"),
            res.getJSONObject("policy"),
            res.getInt("rev"),
        )
    }

    /** Pull the latest policy for this device. Returns (policyJson, rev). */
    fun fetchPolicy(secret: String): Pair<JSONObject, Int> {
        val res = get("/api/sync/policy", secret)
        return res.getJSONObject("policy") to res.getInt("rev")
    }

    fun heartbeat(secret: String, battery: Int?, lat: Double?, lng: Double?, foregroundApp: String?, rev: Int) {
        val b = JSONObject()
            .put("battery", battery ?: JSONObject.NULL)
            .put("lat", lat ?: JSONObject.NULL)
            .put("lng", lng ?: JSONObject.NULL)
            .put("foregroundApp", foregroundApp ?: JSONObject.NULL)
            .put("policyRev", rev)
        post("/api/sync/heartbeat", b, secret)
    }

    fun reportEvent(secret: String, kind: String, detail: String?) {
        post("/api/sync/event", JSONObject().put("kind", kind).put("detail", detail ?: JSONObject.NULL), secret)
    }

    /* ------------------------------ transport ------------------------------ */
    private fun get(path: String, auth: String?): JSONObject = request("GET", path, null, auth)
    private fun post(path: String, body: JSONObject, auth: String?): JSONObject = request("POST", path, body, auth)

    private fun request(method: String, path: String, body: JSONObject?, auth: String?): JSONObject {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Content-Type", "application/json")
            if (auth != null) setRequestProperty("Authorization", "Bearer $auth")
            if (body != null) { doOutput = true; outputStream.use { it.write(body.toString().toByteArray()) } }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        if (code !in 200..299) throw ApiException(code, text)
        return JSONObject(text)
    }
}

class ApiException(val code: Int, val bodyText: String) : Exception("HTTP $code: $bodyText")
