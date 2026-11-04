package com.sx4.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Tacometro de velocidad 0-220 km/h con aguja animada. */
class GaugeView(ctx: Context) : View(ctx) {

    var accent: Int = Color.parseColor("#FFC9D3DD")
        set(v) { field = v; invalidate() }
    var status: String = "Buscando GPS..."
        set(v) { field = v; invalidate() }

    private var target = 0f
    private var shown = 0f
    private var valid = false
    private var forced: Float? = null
    private val ui = Handler(Looper.getMainLooper())

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val startAngle = 135f
    private val sweepAngle = 270f
    private val maxSpeed = 220f

    fun setSpeed(kmh: Float) {
        target = kmh.coerceIn(0f, maxSpeed)
        valid = true
        postInvalidateOnAnimation()
    }

    fun setValid(v: Boolean) {
        valid = v
        postInvalidateOnAnimation()
    }

    /** Barrido de arranque, como un tablero real. */
    fun intro() {
        forced = maxSpeed
        invalidate()
        ui.postDelayed({
            forced = 0f
            invalidate()
            ui.postDelayed({ forced = null; invalidate() }, 900)
        }, 1000)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val r = s / 2f - px(10f)

        val tgt = forced ?: if (valid) target else 0f
        shown += (tgt - shown) * 0.10f
        val moving = abs(tgt - shown) > 0.15f
        if (!moving) shown = tgt

        // pista
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = px(10f)
        p.color = 0x22FFFFFF
        rect.set(cx - r + px(5f), cy - r + px(5f), cx + r - px(5f), cy + r - px(5f))
        c.drawArc(rect, startAngle, sweepAngle, false, p)

        // progreso
        p.color = accent
        p.alpha = 210
        if (shown > 0.5f) c.drawArc(rect, startAngle, sweepAngle * shown / maxSpeed, false, p)
        p.alpha = 255

        // marcas y numeros
        val r1 = r - px(24f)
        p.strokeCap = Paint.Cap.BUTT
        var v = 0
        while (v <= 220) {
            val ang = Math.toRadians((startAngle + sweepAngle * v / maxSpeed).toDouble())
            val major = v % 20 == 0
            val len = px(if (major) 16f else 8f)
            p.strokeWidth = px(if (major) 3f else 2f)
            p.color = if (v <= shown) accent else 0x66FFFFFF
            val ca = cos(ang).toFloat()
            val sa = sin(ang).toFloat()
            c.drawLine(cx + ca * r1, cy + sa * r1, cx + ca * (r1 - len), cy + sa * (r1 - len), p)
            if (major) {
                p.style = Paint.Style.FILL
                p.color = 0xCCFFFFFF.toInt()
                p.textSize = px(15f)
                p.textAlign = Paint.Align.CENTER
                val rl = r1 - px(34f)
                c.drawText(v.toString(), cx + ca * rl, cy + sa * rl + px(5f), p)
                p.style = Paint.Style.STROKE
            }
            v += 10
        }

        // aguja
        val needleAngle = startAngle + sweepAngle * shown / maxSpeed
        c.save()
        c.rotate(needleAngle, cx, cy)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = px(4f)
        p.color = Color.parseColor("#FFFF4B3E")
        c.drawLine(cx - px(18f), cy, cx + r - px(34f), cy, p)
        c.restore()

        // eje
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#FF15181C")
        c.drawCircle(cx, cy, px(15f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = px(3f)
        p.color = accent
        c.drawCircle(cx, cy, px(15f), p)

        // lectura digital
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.color = Color.WHITE
        p.textSize = px(52f)
        val txt = if (!valid && forced == null) "--" else shown.toInt().toString()
        c.drawText(txt, cx, cy + r * 0.55f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = px(16f)
        p.color = 0xAAFFFFFF.toInt()
        c.drawText("km/h", cx, cy + r * 0.55f + px(24f), p)
        p.textSize = px(13f)
        p.color = 0x88FFFFFF.toInt()
        c.drawText(status, cx, cy + r * 0.55f + px(46f), p)

        if (moving) postInvalidateOnAnimation()
    }
}
