package com.sx4.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Visor 360 basado en fotos. Carga car_01, car_02, ... car_NN desde res/drawable-nodpi.
 * Al arrastrar el dedo se pasa de una foto a la siguiente con fundido.
 * Con mas fotos alrededor del coche, el giro se ve mas suave.
 */
class CarView(ctx: Context) : View(ctx) {

    private class L(val x: Float, val y: Float, val r: Float)

    private val frames = ArrayList<Bitmap>()

    // Posicion de faros por foto (fraccion del ancho/alto de la imagen).
    private val lights: List<List<L>> = listOf(
        listOf(L(0.435f, 0.475f, 0.075f), L(0.274f, 0.478f, 0.060f), L(0.420f, 0.600f, 0.035f)),
        listOf(L(0.652f, 0.478f, 0.075f), L(0.668f, 0.600f, 0.035f))
    )

    var autoRotate = false
        set(v) { field = v; postInvalidateOnAnimation() }

    private var nightTarget = 0f
    private var nightAmt = 0f
    private var pos = 0f
    private var spin = 0f
    private var dragging = false
    private var lastX = 0f
    private var lastT = 0L
    private var lastFrame = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val path = Path()
    private val dst = RectF()

    init {
        for (i in 1..120) {
            val id = resources.getIdentifier("car_%02d".format(i), "drawable", context.packageName)
            if (id == 0) break
            val o = BitmapFactory.Options()
            o.inPreferredConfig = Bitmap.Config.ARGB_8888
            val b = BitmapFactory.decodeResource(resources, id, o)
            if (b != null) frames.add(b)
        }
    }

    fun setNight(n: Boolean) {
        nightTarget = if (n) 1f else 0f
        postInvalidateOnAnimation()
    }

    fun frameCount(): Int = frames.size

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                lastX = e.x
                lastT = e.eventTime
                spin = 0f
                parent?.requestDisallowInterceptTouchEvent(true)
                postInvalidateOnAnimation()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val dx = e.x - lastX
                val n = max(1, frames.size)
                val ppf = max(px(10f), width * 1.6f / n)
                val df = -dx / ppf
                pos += df
                val dt = max(1L, e.eventTime - lastT) / 1000f
                spin = spin * 0.5f + (df / dt) * 0.5f
                lastX = e.x
                lastT = e.eventTime
                postInvalidateOnAnimation()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                spin = spin.coerceIn(-12f, 12f)
                postInvalidateOnAnimation()
            }
        }
        return true
    }

    private fun drawFrame(c: Canvas, idx: Int, alpha: Int) {
        paint.alpha = alpha
        if (nightAmt > 0.01f) {
            val a = nightAmt
            val cm = ColorMatrix(floatArrayOf(
                1f - 0.72f * a, 0f, 0f, 0f, 0f,
                0f, 1f - 0.68f * a, 0f, 0f, 0f,
                0f, 0f, 1f - 0.50f * a, 0f, 10f * a,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(cm)
        } else {
            paint.colorFilter = null
        }
        c.drawBitmap(frames[idx], null, dst, paint)

        if (nightAmt > 0.01f) {
            val list = lights.getOrNull(idx) ?: return
            val a = (alpha * nightAmt).toInt().coerceIn(0, 255)
            glow.xfermode = screen
            for (l in list) {
                val cx = dst.left + l.x * dst.width()
                val cy = dst.top + l.y * dst.height()
                val rad = l.r * dst.width()
                glow.shader = RadialGradient(
                    cx, cy, rad,
                    intArrayOf(Color.argb(a, 255, 246, 220), Color.argb(a * 4 / 10, 255, 240, 200), Color.argb(0, 255, 240, 200)),
                    floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP
                )
                c.drawCircle(cx, cy, rad, glow)
            }
            glow.shader = null
            glow.xfermode = null
        }
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(1L, 60L) / 1000f
        lastFrame = now
        var animating = false

        if (nightAmt != nightTarget) {
            val d = nightTarget - nightAmt
            nightAmt += if (d > 0) min(d, dt * 1.6f) else -min(-d, dt * 1.6f)
            animating = true
        }

        txt.textAlign = Paint.Align.CENTER
        if (frames.isEmpty()) {
            txt.color = Color.WHITE
            txt.textSize = px(20f)
            c.drawText("Agrega fotos: car_01.jpg, car_02.jpg ...", width / 2f, height / 2f, txt)
            return
        }

        val n = frames.size
        if (!dragging) {
            if (abs(spin) > 0.02f) {
                pos += spin * dt
                spin *= 0.94f.pow(dt * 60f)
                animating = true
            } else {
                spin = 0f
            }
            if (autoRotate && abs(spin) <= 0.02f) {
                pos += max(0.25f, n / 12f) * dt
                animating = true
            }
        } else {
            animating = true
        }

        val bw = frames[0].width.toFloat()
        val bh = frames[0].height.toFloat()
        val sc = min(width / bw, height / bh)
        val dw = bw * sc
        val dh = bh * sc
        val l = (width - dw) / 2f
        val t = (height - dh) / 2f
        dst.set(l, t, l + dw, t + dh)

        path.reset()
        path.addRoundRect(dst, px(26f), px(26f), Path.Direction.CW)
        c.save()
        c.clipPath(path)
        val p = ((pos % n) + n) % n
        val i0 = floor(p).toInt() % n
        val fr = p - floor(p)
        val i1 = (i0 + 1) % n
        drawFrame(c, i0, 255)
        if (n > 1 && fr > 0.01f) drawFrame(c, i1, (fr * 255).toInt())
        c.restore()

        line.style = Paint.Style.STROKE
        line.strokeWidth = px(2f)
        line.color = 0x44FFFFFF
        c.drawRoundRect(dst, px(26f), px(26f), line)

        txt.color = 0x99FFFFFF.toInt()
        txt.textSize = px(15f)
        c.drawText("Desliza para girar  -  " + n + " vistas", width / 2f, height - px(6f), txt)

        if (animating) postInvalidateOnAnimation()
    }
}
