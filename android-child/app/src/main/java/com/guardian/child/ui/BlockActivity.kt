package com.guardian.child.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Full-screen interstitial shown when a blocked app or active schedule is hit. */
class BlockActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.guardian.child.R.layout.activity_block)

        val reason = intent.getStringExtra("reason") ?: "app"
        val title = findViewById<TextView>(com.guardian.child.R.id.blockTitle)
        val body = findViewById<TextView>(com.guardian.child.R.id.blockBody)

        when (reason) {
            "bedtime" -> { title.text = "Bedtime"; body.text = "This app is paused during bedtime hours." }
            "school" -> { title.text = "School hours"; body.text = "This app is paused during school hours." }
            "limit" -> { title.text = "Time's up"; body.text = "You've used all your time for this app today." }
            else -> { title.text = "Blocked"; body.text = "Your parent has blocked this app." }
        }
    }

    // Back button shouldn't dismiss into the blocked app; send home instead.
    override fun onBackPressed() { finish() }
}
