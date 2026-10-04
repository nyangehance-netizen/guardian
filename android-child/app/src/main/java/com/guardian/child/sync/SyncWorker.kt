package com.guardian.child.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guardian.child.data.PolicyStore
import com.guardian.child.net.ApiClient

/**
 * Periodic worker: pulls the latest policy from the backend and caches it locally,
 * then sends a heartbeat. Enforcement always reads the cached policy, so it keeps
 * working between syncs and while offline.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val store = PolicyStore(applicationContext)
        val secret = store.deviceSecret ?: return Result.success() // not enrolled yet
        return try {
            val (policy, rev) = ApiClient.fetchPolicy(secret)
            if (rev != store.policyRev) {
                store.policyJson = policy.toString()
                store.policyRev = rev
            }
            // lightweight heartbeat so "last seen" stays fresh even without GPS
            runCatching { ApiClient.heartbeat(secret, null, null, null, null, rev) }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
