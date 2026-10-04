package com.guardian.child.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.guardian.child.admin.GuardianAdminReceiver
import com.guardian.child.data.PolicyStore
import com.guardian.child.enforce.GuardianForegroundService
import com.guardian.child.enforce.WebFilterVpnService

/**
 * Setup + status screen. Walks the parent through granting the permissions that
 * give Guardian its teeth. Each is a deliberate, visible OS consent step — which
 * is exactly why a child can't quietly undo them without the parent's passcode.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var store: PolicyStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.guardian.child.R.layout.activity_main)
        store = PolicyStore(this)

        findViewById<Button>(com.guardian.child.R.id.btnEnroll).setOnClickListener {
            startActivity(Intent(this, EnrollActivity::class.java))
        }
        findViewById<Button>(com.guardian.child.R.id.btnAdmin).setOnClickListener { requestDeviceAdmin() }
        findViewById<Button>(com.guardian.child.R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(com.guardian.child.R.id.btnUsage).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        findViewById<Button>(com.guardian.child.R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
        findViewById<Button>(com.guardian.child.R.id.btnVpn).setOnClickListener { requestVpn() }
        findViewById<Button>(com.guardian.child.R.id.btnStart).setOnClickListener {
            startForegroundService(Intent(this, GuardianForegroundService::class.java))
        }
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    private fun refreshStatus() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = dpm.isAdminActive(GuardianAdminReceiver.component(this))
        val overlay = Settings.canDrawOverlays(this)
        val text = buildString {
            append(if (store.isEnrolled) "✓ Paired as ${store.childName}\n" else "✗ Not paired\n")
            append(if (admin) "✓ Device admin active\n" else "✗ Device admin off\n")
            append(if (overlay) "✓ Overlay permission\n" else "✗ Overlay permission off\n")
            append("Accessibility & Usage access: check in Settings")
        }
        findViewById<TextView>(com.guardian.child.R.id.statusText).text = text
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, GuardianAdminReceiver.component(this@MainActivity))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Lets Guardian apply the protection your parent set, and warns them if someone tries to remove it.")
        }
        startActivity(intent)
    }

    private fun requestVpn() {
        val prep = VpnService.prepare(this)
        if (prep != null) startActivityForResult(prep, REQ_VPN)
        else onActivityResult(REQ_VPN, RESULT_OK, null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) {
            startService(Intent(this, WebFilterVpnService::class.java))
        }
    }

    companion object { private const val REQ_VPN = 1001 }
}
