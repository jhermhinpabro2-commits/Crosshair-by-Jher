package com.jher.crosshair

import android.Manifest
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val RED = Color.parseColor("#FF1E2D"); private val BLUE = Color.parseColor("#1E6BFF")
    private lateinit var preview: CrosshairView
    private lateinit var startBtn: TextView

    private val pick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val src = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@registerForActivityResult
        val sc = 256f / maxOf(src.width, src.height)
        CrossState.bitmap = if (sc < 1f) Bitmap.createScaledBitmap(src, (src.width * sc).toInt(), (src.height * sc).toInt(), true) else src
        refresh()
    }

    private fun box(fill: Int, stroke: Int, r: Int) = GradientDrawable().apply { setColor(fill); setStroke(dp(2), stroke); cornerRadius = dp(r).toFloat() }

    private fun button(t: String, stroke: Int, w: Int = LinearLayout.LayoutParams.MATCH_PARENT, act: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD; setPadding(dp(14), dp(12), dp(14), dp(12))
        background = box(Color.parseColor("#1A0A0E"), stroke, 10)
        layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
        setOnClickListener { act() }
    }

    private fun label(t: String) = TextView(this).apply { text = t; setTextColor(BLUE); textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(4), dp(16), 0, dp(4)) }

    private fun refresh() { preview.invalidate(); OverlayService.instance?.applyCross() }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(40), dp(20), dp(24)) }
        root.addView(TextView(this).apply { text = "CROSSHAIR"; textSize = 36f; setTextColor(RED); typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC); letterSpacing = 0.08f; setShadowLayer(18f, 0f, 0f, RED) })
        root.addView(TextView(this).apply { text = "Custom Roblox Overlay"; setTextColor(BLUE); textSize = 13f })

        val card = FrameLayout(this).apply { background = box(Color.parseColor("#14080A"), RED, 12); layoutParams = LinearLayout.LayoutParams(-1, dp(150)).apply { topMargin = dp(16) } }
        preview = CrosshairView(this).apply { layoutParams = FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER) }
        card.addView(preview); root.addView(card)

        root.addView(label("FREE CROSSHAIRS"))
        val presets = LinearLayout(this)
        CrossState.names.forEachIndexed { i, n -> presets.addView(button(n, RED, -2) { CrossState.preset = i; CrossState.bitmap = null; refresh() }) }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(presets) })

        root.addView(label("COLOR"))
        val colors = LinearLayout(this)
        listOf("#FF1E2D", "#1E6BFF", "#FFFFFF", "#22FF55", "#FFE600", "#FF00D4").forEach { c ->
            colors.addView(View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(c)); setStroke(dp(2), Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { setMargins(dp(6), 0, dp(6), 0) }
                setOnClickListener { CrossState.color = Color.parseColor(c); refresh() }
            })
        }
        root.addView(colors)

        root.addView(label("SIZE"))
        root.addView(SeekBar(this).apply {
            max = 270; progress = CrossState.size - 30
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { CrossState.size = p + 30; OverlayService.instance?.applyCross() }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        root.addView(label("CUSTOM"))
        root.addView(button("＋ ADD YOUR OWN IMAGE", BLUE) { pick.launch("image/*") })

        startBtn = button("START OVERLAY", RED) { toggle() }
        root.addView(startBtn.apply { textSize = 18f })
        root.addView(TextView(this).apply { text = "Open Roblox after starting. Use the floating panel to move, resize or minimize."; setTextColor(Color.GRAY); textSize = 11f; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)) })
        root.addView(TextView(this).apply { text = "— by Jher —"; setTextColor(RED); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) })

        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0A0F")); addView(root) })
    }

    override fun onResume() { super.onResume(); startBtn.text = if (OverlayService.instance != null) "STOP OVERLAY" else "START OVERLAY" }

    private fun toggle() {
        if (OverlayService.instance != null) { stopService(Intent(this, OverlayService::class.java)); startBtn.text = "START OVERLAY"; return }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Payagan ang 'Display over other apps'", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
        }
        startForegroundService(Intent(this, OverlayService::class.java)); startBtn.text = "STOP OVERLAY"
    }
}
