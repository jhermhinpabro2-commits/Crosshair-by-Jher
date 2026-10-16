package com.jher.crosshair

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val RED = Color.parseColor("#FF1E2D")
    private val BLUE = Color.parseColor("#1E6BFF")
    private val CARD = Color.parseColor("#D90E0A12")      // semi-transparent para kita ang logo background
    private val CHIP = Color.parseColor("#1A0A0E")
    private val CHIP_ON = Color.parseColor("#55FF1E2D")

    private val palette = listOf(
        "#FF1E2D", "#FF6A00", "#FFE600", "#B6FF00", "#22FF55",
        "#00FFC8", "#00E5FF", "#1E6BFF", "#6A1EFF", "#B400FF",
        "#FF00D4", "#FF7AA8", "#FFFFFF", "#B0B0B0", "#8B5A2B"
    )

    private lateinit var preview: CrosshairView
    private lateinit var previewLabel: TextView
    private lateinit var startBtn: TextView
    private lateinit var seek: SeekBar
    private lateinit var sizeLabel: TextView
    private lateinit var hexInput: EditText
    private lateinit var savedRow: LinearLayout
    private val chips = mutableListOf<TextView>()
    private val swatches = mutableListOf<Pair<View, Int>>()

    private val pick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val src = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@registerForActivityResult
        val sc = 256f / maxOf(src.width, src.height)
        CrossState.bitmap = if (sc < 1f) Bitmap.createScaledBitmap(src, (src.width * sc).toInt(), (src.height * sc).toInt(), true) else src
        refresh()
    }

    // ---------- helpers ----------
    private fun box(fill: Int, stroke: Int, r: Int, sw: Int = 2) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(sw), stroke); cornerRadius = dp(r).toFloat()
    }

    private fun lp(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

    private fun button(t: String, stroke: Int, w: Int = LinearLayout.LayoutParams.MATCH_PARENT, act: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD; setPadding(dp(14), dp(12), dp(14), dp(12))
        background = box(CHIP, stroke, 10)
        layoutParams = lp(w, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 4, 0, 4)
        setOnClickListener { act() }
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; setTextColor(BLUE); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f; setPadding(dp(2), 0, 0, dp(10))
    }

    /** Card na may title at laman. */
    private fun card(title: String, vararg content: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = box(CARD, Color.parseColor("#33FFFFFF"), 14, 1)
        layoutParams = lp(-1, -2, 0, 0, 0, 14)
        addView(label(title))
        content.forEach { addView(it) }
    }

    private fun hexOf(c: Int) = String.format("#%06X", c and 0xFFFFFF)

    private fun parseHex(s: String): Int? {
        var t = s.trim().removePrefix("#")
        if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
        if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return (0xFF000000L or t.toLong(16)).toInt()
    }

    // ---------- state -> UI ----------
    private fun refresh() {
        preview.invalidate()
        OverlayService.instance?.applyCross()
        updateChips(); updateSwatches(); updateLabels()
    }

    private fun updateLabels() {
        sizeLabel.text = "${CrossState.size} dp"
        previewLabel.text = "${CrossState.size} dp  •  ${hexOf(CrossState.color)}"
    }

    private fun updateChips() {
        chips.forEachIndexed { i, tv ->
            val on = CrossState.bitmap == null && i == CrossState.preset
            tv.background = box(if (on) CHIP_ON else CHIP, if (on) Color.WHITE else RED, 10)
        }
    }

    private fun updateSwatches() {
        swatches.forEach { (v, c) ->
            val on = c == (CrossState.color and 0xFFFFFF)
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor(hexOf(c)))
                setStroke(dp(if (on) 3 else 1), if (on) Color.WHITE else Color.parseColor("#66FFFFFF"))
            }
            v.scaleX = if (on) 1.15f else 1f; v.scaleY = if (on) 1.15f else 1f
        }
    }

    private fun applyHex() {
        val c = parseHex(hexInput.text.toString())
        if (c == null) { Toast.makeText(this, "Mali ang hex. Halimbawa: #FF2244", Toast.LENGTH_SHORT).show(); return }
        CrossState.color = c; hexInput.setText(hexOf(c)); hexInput.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(hexInput.windowToken, 0)
        refresh()
    }

    private fun syncFromState() {
        seek.progress = CrossState.size - CrossState.MIN_SIZE
        hexInput.setText(hexOf(CrossState.color))
        refresh()
    }

    // ---------- saved crosshairs ----------
    private fun saveCurrent() {
        val sc = SavedCross(System.currentTimeMillis(), CrossState.preset, CrossState.color, CrossState.size, CrossState.bitmap != null)
        sc.bitmap = CrossState.bitmap
        Store.add(this, sc)
        refreshSaved()
        Toast.makeText(this, "Na-save ang crosshair", Toast.LENGTH_SHORT).show()
    }

    private fun applySaved(sc: SavedCross) {
        CrossState.preset = sc.preset; CrossState.color = sc.color; CrossState.size = sc.size
        CrossState.bitmap = if (sc.hasImage) sc.bitmap else null
        syncFromState()
    }

    private fun tile(sc: SavedCross): View {
        val t = FrameLayout(this).apply {
            background = box(CHIP, BLUE, 10)
            layoutParams = lp(dp(84), dp(100), 0, 0, 8, 0)
            setOnClickListener { applySaved(sc) }
        }
        t.addView(CrosshairView(this, sc).apply { layoutParams = FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(10) } })
        t.addView(TextView(this).apply {
            text = "${sc.size} dp"; setTextColor(Color.WHITE); textSize = 11f; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(8) }
        })
        t.addView(TextView(this).apply {
            text = "✕"; setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(RED) }
            layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END).apply { topMargin = dp(4); rightMargin = dp(4) }
            setOnClickListener { Store.remove(this@MainActivity, sc.id); refreshSaved() }
        })
        return t
    }

    private fun refreshSaved() {
        savedRow.removeAllViews()
        val list = Store.load(this)
        if (list.isEmpty()) {
            savedRow.addView(TextView(this).apply { text = "Wala pang naka-save. Pindutin ang SAVE CROSSHAIR."; setTextColor(Color.GRAY); textSize = 12f })
        } else list.reversed().forEach { savedRow.addView(tile(it)) }
    }

    // ---------- UI ----------
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(44), dp(18), dp(28)) }

        root.addView(TextView(this).apply {
            text = "CROSSHAIR"; textSize = 36f; setTextColor(RED)
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC); letterSpacing = 0.08f; setShadowLayer(18f, 0f, 0f, RED)
        })
        root.addView(TextView(this).apply { text = "Custom Roblox Overlay"; setTextColor(BLUE); textSize = 13f; setPadding(dp(2), 0, 0, dp(16)) })

        // Preview
        val pv = FrameLayout(this).apply {
            background = box(CARD, RED, 14)
            layoutParams = lp(-1, dp(170), 0, 0, 0, 14)
        }
        preview = CrosshairView(this).apply { layoutParams = FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER) }
        previewLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#AAFFFFFF")); textSize = 11f; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(8) }
        }
        pv.addView(preview); pv.addView(previewLabel); root.addView(pv)

        // Presets
        val presets = LinearLayout(this)
        CrossState.names.forEachIndexed { i, n ->
            val chip = button(n, RED, -2) { CrossState.preset = i; CrossState.bitmap = null; refresh() }
            chip.layoutParams = lp(-2, -2, 0, 0, 8, 0)
            chips.add(chip); presets.addView(chip)
        }
        root.addView(card("FREE CROSSHAIRS", HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(presets) }))

        // Colors: 15 swatches, 5 kada row
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        palette.chunked(5).forEach { rowColors ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = lp(-1, -2, 0, 2, 0, 2) }
            rowColors.forEach { hex ->
                val c = Color.parseColor(hex) and 0xFFFFFF
                val dot = View(this).apply { layoutParams = FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER) }
                val cell = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
                    addView(dot)
                    setOnClickListener { CrossState.color = Color.parseColor(hex); hexInput.setText(hexOf(CrossState.color)); refresh() }
                }
                swatches.add(Pair(dot, c)); row.addView(cell)
            }
            grid.addView(row)
        }
        hexInput = EditText(this).apply {
            hint = "#FF2244"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); textSize = 15f
            setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(7), InputFilter.AllCaps())
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(dp(14), dp(10), dp(14), dp(10)); background = box(CHIP, BLUE, 10)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, dp(4), dp(8), dp(4)) }
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { applyHex(); true } else false }
        }
        val hexRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = lp(-1, -2, 0, 8, 0, 0)
            addView(hexInput); addView(button("SET", BLUE, -2) { applyHex() })
        }
        root.addView(card("COLOR", grid, hexRow))

        // Size
        sizeLabel = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.END; typeface = Typeface.DEFAULT_BOLD }
        seek = SeekBar(this).apply {
            max = CrossState.MAX_SIZE - CrossState.MIN_SIZE; progress = CrossState.size - CrossState.MIN_SIZE
            progressTintList = ColorStateList.valueOf(RED); thumbTintList = ColorStateList.valueOf(RED)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                    if (!u) return
                    CrossState.size = p + CrossState.MIN_SIZE
                    OverlayService.instance?.applyCross(); updateLabels()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        root.addView(card("SIZE", sizeLabel, seek))

        // Custom + save
        root.addView(card("CUSTOM", button("＋ ADD YOUR OWN IMAGE", BLUE) { pick.launch("image/*") }, button("💾 SAVE CROSSHAIR", RED) { saveCurrent() }))

        // Saved
        savedRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(card("SAVED", HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(savedRow) }))

        // Start
        startBtn = button("START OVERLAY", RED) { toggle() }
        root.addView(startBtn.apply { textSize = 18f; setPadding(dp(14), dp(16), dp(14), dp(16)) })
        root.addView(TextView(this).apply { text = "Open Roblox after starting. Use the floating panel to move, resize or minimize."; setTextColor(Color.parseColor("#B0B0B0")); textSize = 11f; gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), dp(6)) })
        root.addView(TextView(this).apply { text = "— by Jher —"; setTextColor(RED); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })

        // Background: dimmed na logo sa likod ng buong app
        val bg = ImageView(this).apply {
            setImageResource(R.drawable.app_bg); scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.30f
        }
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0A0A0F")) }
        frame.addView(bg, FrameLayout.LayoutParams(-1, -1))
        frame.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(root) }, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)

        hexInput.setText(hexOf(CrossState.color))
        updateChips(); updateSwatches(); updateLabels(); refreshSaved()
    }

    override fun onResume() {
        super.onResume()
        startBtn.text = if (OverlayService.instance != null) "STOP OVERLAY" else "START OVERLAY"
        // i-sync kung binago ang size/kulay sa floating panel
        seek.progress = CrossState.size - CrossState.MIN_SIZE
        updateLabels(); preview.invalidate()
    }

    private fun toggle() {
        if (OverlayService.instance != null) { stopService(Intent(this, OverlayService::class.java)); startBtn.text = "START OVERLAY"; return }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Payagan ang 'Display over other apps'", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
        }
        startForegroundService(Intent(this, OverlayService::class.java)); startBtn.text = "STOP OVERLAY"
    }
}
