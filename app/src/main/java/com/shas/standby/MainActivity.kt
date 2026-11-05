package com.shas.standby

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("standby", MODE_PRIVATE) }

    override fun onResume() {
        super.onResume()
        setContentView(buildUi())
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun buildUi(): LinearLayout {
        val enabled = prefs.getBoolean("enabled", false)
        val overlay = Settings.canDrawOverlays(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(24), dp(56), dp(24), dp(24))
        }
        fun text(s: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
            this.text = s; textSize = size; setTextColor(color)
            setPadding(0, 0, 0, dp(12))
        }
        fun button(s: String, onClick: () -> Unit) = Button(this).apply {
            text = s; isAllCaps = false; setOnClickListener { onClick() }
        }

        root.addView(text("StandBy", 34f))
        root.addView(text("Shows a clock when your phone is charging and on its side.", 15f, 0xFF8E8E93.toInt()))
        root.addView(text(
            (if (overlay) "✓" else "✗") + " Display over other apps\n" +
                (if (enabled) "✓" else "✗") + " StandBy service running",
            15f
        ))

        root.addView(button("1. Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(button("2. Allow notifications") {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        })
        root.addView(button("3. Don't restrict battery") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        })
        root.addView(button(if (enabled) "Turn StandBy OFF" else "Turn StandBy ON") {
            val svc = Intent(this, StandByService::class.java)
            if (enabled) stopService(svc) else startForegroundService(svc)
            prefs.edit().putBoolean("enabled", !enabled).apply()
            recreate()
        })
        root.addView(button("Preview now") {
            startActivity(Intent(this, StandByActivity::class.java))
        })
        root.addView(text(
            "\nSwipe up/down to change style. Long-press to change colour. Back to exit.",
            13f, 0xFF8E8E93.toInt()
        ).apply { gravity = Gravity.START })
        return root
    }
}
