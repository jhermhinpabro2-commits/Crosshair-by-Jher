package com.jher.crosshair

import android.content.Context
import android.graphics.*
import android.view.View

object CrossState {
    val names = listOf("Dot", "Cross", "Gap Cross", "Ring", "T-Shape", "Diamond")
    var preset = 2
    var bitmap: Bitmap? = null
    var color = Color.parseColor("#FF1E2D")
    var size = 120
    var x = 0; var y = 0
}

class CrosshairView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    override fun onDraw(c: Canvas) {
        val b = CrossState.bitmap
        if (b != null) { c.drawBitmap(b, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), p); return }
        p.color = Color.BLACK; render(c, 5f)
        p.color = CrossState.color; render(c, 0f)
    }

    private fun render(c: Canvas, ex: Float) {
        val s = width.toFloat(); val m = s / 2
        val st = (s * 0.035f).coerceAtLeast(2f)
        p.style = Paint.Style.STROKE; p.strokeWidth = st + ex
        when (CrossState.preset) {
            0 -> { p.style = Paint.Style.FILL; c.drawCircle(m, m, s * 0.06f + ex / 2, p) }
            1 -> { c.drawLine(m, s * .1f, m, s * .9f, p); c.drawLine(s * .1f, m, s * .9f, m, p) }
            2 -> {
                c.drawLine(m, s * .1f, m, s * .38f, p); c.drawLine(m, s * .62f, m, s * .9f, p)
                c.drawLine(s * .1f, m, s * .38f, m, p); c.drawLine(s * .62f, m, s * .9f, m, p)
            }
            3 -> {
                c.drawCircle(m, m, s * .35f, p)
                p.style = Paint.Style.FILL; c.drawCircle(m, m, s * .04f + ex / 2, p)
            }
            4 -> {
                c.drawLine(s * .1f, m, s * .42f, m, p); c.drawLine(s * .58f, m, s * .9f, m, p)
                c.drawLine(m, s * .58f, m, s * .9f, p)
            }
            5 -> {
                val d = Path().apply { moveTo(m, s * .15f); lineTo(s * .85f, m); lineTo(m, s * .85f); lineTo(s * .15f, m); close() }
                c.drawPath(d, p)
                p.style = Paint.Style.FILL; c.drawCircle(m, m, s * .04f + ex / 2, p)
            }
        }
    }
}
