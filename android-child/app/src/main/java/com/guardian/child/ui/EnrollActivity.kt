package com.guardian.child.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.guardian.child.data.PolicyStore
import com.guardian.child.net.ApiClient
import kotlin.concurrent.thread

/** Child/parent enters the 6-digit pairing code shown on the parent dashboard. */
class EnrollActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.guardian.child.R.layout.activity_enroll)

        val code = findViewById<EditText>(com.guardian.child.R.id.codeInput)
        val btn = findViewById<Button>(com.guardian.child.R.id.enrollBtn)
        val status = findViewById<TextView>(com.guardian.child.R.id.enrollStatus)

        btn.setOnClickListener {
            val pair = code.text.toString().trim()
            if (pair.length != 6) { status.text = "Enter the 6-digit code"; return@setOnClickListener }
            btn.isEnabled = false; status.text = "Pairing…"
            thread {
                try {
                    val e = ApiClient.enroll(pair)
                    PolicyStore(this).saveEnrollment(e.deviceId, e.deviceSecret, e.childName, e.policy, e.rev)
                    runOnUiThread {
                        Toast.makeText(this, "Paired as ${e.childName}", Toast.LENGTH_LONG).show()
                        finish()
                    }
                } catch (ex: Exception) {
                    runOnUiThread { btn.isEnabled = true; status.text = "Failed: ${ex.message}" }
                }
            }
        }
    }
}
