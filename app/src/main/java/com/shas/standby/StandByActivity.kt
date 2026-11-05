package com.shas.standby

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

class StandByActivity : Activity(), SensorEventListener {

    private lateinit var view: StandByView
    private lateinit var sm: SensorManager
    private var night = false

    private val closer = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setDecorFitsSystemWindows(false)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))

        val prefs = getSharedPreferences("standby", MODE_PRIVATE)
        view = StandByView(this)
        view.page = prefs.getInt("page", 0)
        view.accentIdx = prefs.getInt("accent", 0)
        view.onChanged = {
            prefs.edit().putInt("page", view.page).putInt("accent", view.accentIdx).apply()
        }
        setContentView(view)

        registerReceiver(closer, IntentFilter(StandByService.ACTION_CLOSE), RECEIVER_NOT_EXPORTED)
        StandByService.showing = true
        sm = getSystemService(SensorManager::class.java)
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        sm.unregisterListener(this)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private fun hideBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            // Left on purpose (home / app switch) vs. screen simply turned off
            if (getSystemService(PowerManager::class.java).isInteractive) {
                StandByService.dismissed = true
            }
            finish()
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        StandByService.dismissed = true
        finish()
    }

    override fun onDestroy() {
        unregisterReceiver(closer)
        StandByService.showing = false
        super.onDestroy()
    }

    // Night mode: dark room -> dim red, like iOS
    override fun onSensorChanged(e: SensorEvent) {
        val lux = e.values[0]
        val n = if (night) lux < 12f else lux < 3f
        if (n == night) return
        night = n
        view.night = n
        view.invalidate()
        window.attributes = window.attributes.apply {
            screenBrightness =
                if (n) 0.02f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
