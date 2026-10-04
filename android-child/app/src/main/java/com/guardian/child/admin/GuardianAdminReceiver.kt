package com.guardian.child.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.guardian.child.data.PolicyStore
import com.guardian.child.net.ApiClient
import kotlin.concurrent.thread

/**
 * Device Admin receiver. Once the parent enables this in setup, the Guardian app
 * can enforce lock policies and, importantly, cannot be uninstalled from Settings
 * until admin is deactivated — and we get notified the moment someone *tries*, so
 * we can alert the parent (a "tamper" event) before the app is removed.
 *
 * For a stronger lock-down, provision as Device Owner (see README) and extend this
 * class to call DevicePolicyManager.setUninstallBlocked(), addUserRestriction(
 * DISALLOW_SAFE_BOOT / DISALLOW_FACTORY_RESET), setAlwaysOnVpnPackage(), etc.
 */
class GuardianAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Fire a tamper alert to the backend before admin is turned off.
        val store = PolicyStore(context)
        val secret = store.deviceSecret
        if (secret != null) {
            thread {
                runCatching { ApiClient.reportEvent(secret, "tamper", "Device-admin disable requested") }
            }
        }
        return "Turning this off removes the protection your parent set up. They will be notified."
    }

    companion object {
        fun component(context: Context) = ComponentName(context, GuardianAdminReceiver::class.java)
    }
}
