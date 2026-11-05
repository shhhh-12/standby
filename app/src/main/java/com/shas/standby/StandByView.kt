package com.shas.standby

import android.app.AlarmManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Four screens, swipe up/down to switch (like iOS StandBy clock styles):
 * 0 Digital, 1 Analog, 2 Float, 3 Widgets (analog + calendar).
 * Long-press cycles the accent colour. Dim red in the dark.
 */
class StandByView(context: Context) : View(context) {

    var page = 0
    var accentIdx = 0
    var night = false
    var onChanged: (() -> Unit)? = null

    private val accents = intArrayOf(
        0xFFFF9F0A.toInt(), 0xFFFFFFFF.toInt(), 0xFF30D158.toInt(),
        0xFF0A84FF.toInt(), 0xFFFF375F.toInt(), 0xFFFFD60A.toInt()
    )
    private val nightRed = 0xFFB71C1C.toInt()
    private val grey = 0xFF8E8E93.toInt()

    private val fg get() = if (night) nightRed else Color.WHITE
    private val acc get() = if (night) nightRed else accents[accentIdx % accents.size]
    private val dim get() = if (night) nightRed else grey
    private val cardColor get() = if (night) 0xFF150000.toInt() else 0xFF1C1C1E.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val alarmMgr = context.getSystemService(AlarmManager::class.java)

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (e1 == null) return false
            val dy = e2.y - e1.y
            val dx = e2.x - e1.x
            if (abs(dy) > abs(dx) && abs(dy) > 120f) {
                page = (page + if (dy < 0) 1 else 3) % 4
                onChanged?.invoke()
                invalidate()
                return true
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            accentIdx = (accentIdx + 1) % accents.size
            onChanged?.invoke()
            invalidate()
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        val now = Calendar.getInstance()
        // tiny pixel shift every few minutes to protect the OLED
        val m = now.get(Calendar.MINUTE)
        c.save()
        c.translate(((m % 5) - 2) * 4f, (((m / 5) % 3) - 1) * 4f)
        when (page) {
            0 -> digital(c, now)
            1 -> analogPage(c, now)
            2 -> floatPage(c, now)
            else -> widgets(c, now)
        }
        c.restore()
        postInvalidateDelayed((1000 - now.get(Calendar.MILLISECOND)).toLong())
    }

    private fun fresh(): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.CENTER
        return paint
    }

    private fun hour12(now: Calendar) = (now.get(Calendar.HOUR_OF_DAY) + 11) % 12 + 1

    // ---------- 0: digital ----------
    private fun digital(c: Canvas, now: Calendar) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = "%d:%02d".format(hour12(now), now.get(Calendar.MINUTE))
        val p = fresh()
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        p.color = fg
        p.textSize = h * 0.62f
        val tw = p.measureText(t)
        if (tw > w * 0.9f) p.textSize *= w * 0.9f / tw
        val fm = p.fontMetrics
        c.drawText(t, w / 2, h * 0.47f - (fm.ascent + fm.descent) / 2, p)
        footer(c, now, h * 0.9f)
    }

    private fun footer(c: Canvas, now: Calendar, baseline: Float) {
        var s = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now.time)
        alarmMgr?.nextAlarmClock?.let {
            s += "     \u23F0 " + SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(Date(it.triggerTime))
        }
        val p = fresh()
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        p.color = dim
        p.textSize = height * 0.06f
        c.drawText(s, width / 2f, baseline, p)
    }

    // ---------- 1: analog ----------
    private fun analogPage(c: Canvas, now: Calendar) {
        val r = min(width, height) * 0.46f
        analog(c, width / 2f, height / 2f, r, now, true)
    }

    private fun analog(c: Canvas, cx: Float, cy: Float, r: Float, now: Calendar, numerals: Boolean) {
        val p = fresh()
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 60) {
            val major = i % 5 == 0
            p.strokeWidth = if (major) r * 0.025f else r * 0.012f
            p.color = if (major) fg else dim
            val len = if (major) r * 0.09f else r * 0.04f
            c.save()
            c.rotate(i * 6f, cx, cy)
            c.drawLine(cx, cy - r, cx, cy - r + len, p)
            c.restore()
        }
        if (numerals) {
            val t = fresh()
            t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            t.color = fg
            t.textSize = r * 0.17f
            val fm = t.fontMetrics
            for (n in 1..12) {
                val a = Math.toRadians(n * 30.0 - 90)
                c.drawText(
                    n.toString(),
                    cx + (cos(a) * r * 0.78).toFloat(),
                    cy + (sin(a) * r * 0.78).toFloat() - (fm.ascent + fm.descent) / 2,
                    t
                )
            }
        }
        val h = now.get(Calendar.HOUR)
        val m = now.get(Calendar.MINUTE)
        val s = now.get(Calendar.SECOND)
        val hourLen = if (numerals) 0.42f else 0.5f
        val minLen = if (numerals) 0.64f else 0.8f
        val secLen = if (numerals) 0.7f else 0.85f
        hand(c, cx, cy, ((h + m / 60f) * 30f), r * hourLen, r * 0.07f, fg, 0.1f)
        hand(c, cx, cy, ((m + s / 60f) * 6f), r * minLen, r * 0.05f, fg, 0.1f)
        hand(c, cx, cy, s * 6f, r * secLen, r * 0.02f, acc, 0.2f)
        val d = fresh()
        d.color = acc
        c.drawCircle(cx, cy, r * 0.045f, d)
    }

    private fun hand(
        c: Canvas, cx: Float, cy: Float, angle: Float,
        len: Float, width: Float, color: Int, tail: Float
    ) {
        val p = fresh()
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = width
        p.color = color
        c.save()
        c.rotate(angle, cx, cy)
        c.drawLine(cx, cy + len * tail, cx, cy - len, p)
        c.restore()
    }

    // ---------- 2: float (big stacked digits) ----------
    private fun floatPage(c: Canvas, now: Calendar) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p = fresh()
        p.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        p.textSize = h * 0.52f
        p.color = fg
        c.drawText(hour12(now).toString(), w / 2, h * 0.46f, p)
        p.color = acc
        c.drawText("%02d".format(now.get(Calendar.MINUTE)), w / 2, h * 0.92f, p)
    }

    // ---------- 3: widgets (analog + calendar) ----------
    private fun widgets(c: Canvas, now: Calendar) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rad = h * 0.08f
        val left = RectF(w * 0.04f, h * 0.1f, w * 0.48f, h * 0.9f)
        val right = RectF(w * 0.52f, h * 0.1f, w * 0.96f, h * 0.9f)

        val bg = fresh()
        bg.color = cardColor
        c.drawRoundRect(left, rad, rad, bg)
        c.drawRoundRect(right, rad, rad, bg)

        analog(c, left.centerX(), left.centerY(), min(left.width(), left.height()) * 0.42f, now, false)

        val cx = right.centerX()
        val p = fresh()
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        p.color = acc
        p.textSize = h * 0.075f
        c.drawText(
            SimpleDateFormat("MMMM", Locale.getDefault()).format(now.time).uppercase(),
            cx, right.top + h * 0.17f, p
        )
        p.color = fg
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        p.textSize = h * 0.42f
        c.drawText(now.get(Calendar.DAY_OF_MONTH).toString(), cx, right.centerY() + h * 0.17f, p)
        p.color = dim
        p.textSize = h * 0.075f
        c.drawText(
            SimpleDateFormat("EEEE", Locale.getDefault()).format(now.time),
            cx, right.bottom - h * 0.1f, p
        )
        rect.set(0f, 0f, 0f, 0f)
    }
}
