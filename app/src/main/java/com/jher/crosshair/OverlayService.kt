package com.jher.crosshair

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

class OverlayService : Service() {
    companion object { var instance: OverlayService? = null }

    private lateinit var wm: WindowManager
    private lateinit var cross: CrosshairView
    private lateinit var crossLp: WindowManager.LayoutParams
    private lateinit var panel: LinearLayout
    private lateinit var bubble: TextView
    private lateinit var panelLp: WindowManager.LayoutParams
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private val h = Handler(Looper.getMainLooper())
    private val RED = Color.parseColor("#FF1E2D"); private val BLUE = Color.parseColor("#1E6BFF")

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate(); instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startFg()
        cross = CrosshairView(this)
        crossLp = lp(dp(CrossState.size), dp(CrossState.size), false).apply { gravity = Gravity.CENTER }
        wm.addView(cross, crossLp); applyCross()
        buildPanel(); buildBubble()
        panelLp = lp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, true).apply { gravity = Gravity.TOP or Gravity.START; x = dp(10); y = dp(120) }
        bubbleLp = lp(dp(46), dp(46), true).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(panel, panelLp)
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("cx", "Crosshair", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "cx").setContentTitle("CROSSHAIR is running").setContentText("by Jher").setSmallIcon(android.R.drawable.ic_menu_compass).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
    }

    private fun lp(w: Int, hh: Int, touch: Boolean): WindowManager.LayoutParams {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touch) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(w, hh, if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE, f, PixelFormat.TRANSLUCENT)
    }

    fun applyCross() {
        crossLp.width = dp(CrossState.size); crossLp.height = dp(CrossState.size)
        crossLp.x = CrossState.x; crossLp.y = CrossState.y
        wm.updateViewLayout(cross, crossLp); cross.invalidate()
    }

    private fun box(color: Int, stroke: Int, r: Int) = GradientDrawable().apply { setColor(color); setStroke(dp(2), stroke); cornerRadius = dp(r).toFloat() }

    private fun btn(t: String, color: Int = RED, act: () -> Unit): TextView = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        background = box(Color.parseColor("#1A0A0E"), color, 8)
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        var run: Runnable? = null
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { act(); run = object : Runnable { override fun run() { act(); h.postDelayed(this, 50) } }; h.postDelayed(run!!, 350) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> run?.let { h.removeCallbacks(it) }
            }
            true
        }
    }

    private fun move(dx: Int, dy: Int) { CrossState.x += dx; CrossState.y += dy; applyCross() }
    private fun resize(d: Int) { CrossState.size = (CrossState.size + d).coerceIn(30, 400); applyCross() }

    private fun row(vararg v: View) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; v.forEach { addView(it) } }

    private fun buildPanel() {
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(8)); background = box(Color.parseColor("#E60A0A0F"), RED, 14)
        }
        val title = TextView(this).apply { text = "⠿ CROSSHAIR"; setTextColor(RED); textSize = 12f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(dp(4), dp(4), dp(10), dp(4)) }
        val mini = btn("—", BLUE) { }.apply {
            setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_UP) minimize(); true }
        }
        val close = btn("✕") { }.apply {
            setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_UP) stopSelf(); true }
        }
        drag(title, panel, { panelLp }, null)
        panel.addView(row(title, mini, close))
        panel.addView(row(btn("▲") { move(0, -2) }))
        panel.addView(row(btn("◀") { move(-2, 0) }, btn("●", BLUE) { CrossState.x = 0; CrossState.y = 0; applyCross() }, btn("▶") { move(2, 0) }))
        panel.addView(row(btn("▼") { move(0, 2) }))
        panel.addView(row(btn("−", BLUE) { resize(-4) }, btn("+", BLUE) { resize(4) }))
        panel.addView(TextView(this).apply { text = "by Jher"; setTextColor(BLUE); textSize = 10f; gravity = Gravity.CENTER })
    }

    private fun buildBubble() {
        bubble = TextView(this).apply {
            text = "✛"; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#E60A0A0F")); setStroke(dp(2), RED) }
        }
        drag(bubble, bubble, { bubbleLp }) { restore() }
    }

    private fun minimize() {
        bubbleLp.x = panelLp.x; bubbleLp.y = panelLp.y
        wm.removeView(panel); wm.addView(bubble, bubbleLp)
    }
    private fun restore() {
        panelLp.x = bubbleLp.x; panelLp.y = bubbleLp.y
        wm.removeView(bubble); wm.addView(panel, panelLp)
    }

    private fun drag(handle: View, target: View, get: () -> WindowManager.LayoutParams, tap: (() -> Unit)?) {
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        handle.setOnTouchListener { _, e ->
            val p = get()
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) + Math.abs(dy) > dp(6)) moved = true
                    p.x = sx + dx; p.y = sy + dy; wm.updateViewLayout(target, p)
                }
                MotionEvent.ACTION_UP -> if (!moved) tap?.invoke()
            }
            true
        }
    }

    override fun onDestroy() {
        instance = null
        listOf(cross, panel, bubble).forEach { try { wm.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }
}
