package com.jher.crosshair

import android.content.Context
import android.graphics.*
import android.view.View

object CrossState {
    val names = listOf("Dot", "Cross", "Gap Cross", "Ring", "T-Shape", "Diamond")
    const val MIN_SIZE = 4     // dp (hanggang tuldok na lang)
    const val MAX_SIZE = 300   // dp
    var preset = 2
    var bitmap: Bitmap? = null
    var color = Color.parseColor("#FF1E2D")
    var size = 120   // dp
    var x = 0; var y = 0  // px offset from center
}

/** Naka-save na crosshair (preset, kulay, size, at optional custom image). */
class SavedCross(val id: Long, val preset: Int, val color: Int, val size: Int, val hasImage: Boolean) {
    var bitmap: Bitmap? = null
}

/**
 * Kapag walang [fixed], ang live na CrossState ang dino-drawing.
 * Kapag may [fixed], yung naka-save na config ang fixed na dino-drawing (para sa saved tiles).
 */
class CrosshairView(ctx: Context, private val fixed: SavedCross? = null) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private var preset = 0
    private var color = Color.RED

    override fun onDraw(c: Canvas) {
        val f = fixed
        val b = if (f != null) f.bitmap else CrossState.bitmap
        preset = f?.preset ?: CrossState.preset
        color = f?.color ?: CrossState.color
        if (b != null) { c.drawBitmap(b, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), p); return }
        val s = width.toFloat()
        val st = (s * 0.035f).coerceAtLeast(1f)
        val ex = (st * 1.2f + 1f).coerceAtMost(5f)   // outline, lumiliit kapag maliit
        p.color = Color.BLACK; render(c, st, ex)
        p.color = color; render(c, st, 0f)
    }

    private fun render(c: Canvas, st: Float, ex: Float) {
        val s = width.toFloat(); val m = s / 2
        p.style = Paint.Style.STROKE; p.strokeWidth = st + ex
        val dotR = (s * 0.06f).coerceAtLeast(1.2f)
        val smallDotR = (s * 0.04f).coerceAtLeast(1.2f)
        when (preset) {
            0 -> { p.style = Paint.Style.FILL; c.drawCircle(m, m, dotR + ex / 2, p) }
            1 -> { c.drawLine(m, s * .1f, m, s * .9f, p); c.drawLine(s * .1f, m, s * .9f, m, p) }
            2 -> {
                c.drawLine(m, s * .1f, m, s * .38f, p); c.drawLine(m, s * .62f, m, s * .9f, p)
                c.drawLine(s * .1f, m, s * .38f, m, p); c.drawLine(s * .62f, m, s * .9f, m, p)
            }
            3 -> {
                c.drawCircle(m, m, s * .35f, p)
                p.style = Paint.Style.FILL; c.drawCircle(m, m, smallDotR + ex / 2, p)
            }
            4 -> {
                c.drawLine(s * .1f, m, s * .42f, m, p); c.drawLine(s * .58f, m, s * .9f, m, p)
                c.drawLine(m, s * .58f, m, s * .9f, p)
            }
            5 -> {
                val d = Path().apply { moveTo(m, s * .15f); lineTo(s * .85f, m); lineTo(m, s * .85f); lineTo(s * .15f, m); close() }
                c.drawPath(d, p)
                p.style = Paint.Style.FILL; c.drawCircle(m, m, smallDotR + ex / 2, p)
            }
        }
    }
}
