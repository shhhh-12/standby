package com.shas.standby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * Watches two things: is the phone charging, and is it lying on its side (landscape).
 * When both are true it opens the StandBy screen; when either stops, it closes it.
 */
class StandByService : Service(), SensorEventListener {

    companion object {
        const val ACTION_CLOSE = "com.shas.standby.CLOSE"
        private const val CHANNEL = "standby"

        /** User left the screen on purpose; don't reopen until conditions reset. */
        @Volatile var dismissed = false
        @Volatile var showing = false
    }

    private var charging = false
    private var landscape = false
    private var lx = 0f
    private var ly = 0f
    private var lastLaunch = 0L
    private var anchor: View? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            }
            evaluate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "StandBy", NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("StandBy is on")
            .setContentText("Shows when charging in landscape")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        addOverlayAnchor()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val sticky = registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        charging = (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        val sm = getSystemService(SensorManager::class.java)
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /** A 1px invisible overlay keeps the "display over other apps" exemption active on Android 15. */
    private fun addOverlayAnchor() {
        if (!Settings.canDrawOverlays(this)) return
        try {
            val v = View(this)
            val lp = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            getSystemService(WindowManager::class.java).addView(v, lp)
            anchor = v
        } catch (_: Exception) {
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        lx = 0.8f * lx + 0.2f * e.values[0]
        ly = 0.8f * ly + 0.2f * e.values[1]
        // gravity along the long axis (x) = phone is on its side. Hysteresis avoids flicker.
        landscape = if (landscape) abs(lx) > 5f && abs(ly) < 6f
        else abs(lx) > 7f && abs(ly) < 3.5f
        evaluate()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun evaluate() {
        val want = charging && landscape
        if (!want) {
            dismissed = false
            if (showing) sendBroadcast(Intent(ACTION_CLOSE).setPackage(packageName))
            return
        }
        val pm = getSystemService(PowerManager::class.java)
        val now = SystemClock.elapsedRealtime()
        if (!dismissed && !showing && pm.isInteractive && now - lastLaunch > 3000) {
            lastLaunch = now
            startActivity(
                Intent(this, StandByActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(receiver)
        getSystemService(SensorManager::class.java).unregisterListener(this)
        anchor?.let {
            try { getSystemService(WindowManager::class.java).removeView(it) } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
