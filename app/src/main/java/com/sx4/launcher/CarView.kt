package com.sx4.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import java.util.Random
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Escena de fondo: tu SX4 (foto recortada, mirando a la izquierda) circulando
 * por una carretera. El paisaje se desplaza de derecha a izquierda -> el coche avanza.
 * Capas con paralaje, llantas que giran, luces de noche.
 */
class CarView(ctx: Context) : View(ctx) {

    // ---- recorte del coche (coordenadas en pixeles del PNG car_cut.png)
    private val car: Bitmap? = BitmapFactory.decodeResource(resources, R.drawable.car_cut)
    private val footX = 475f
    private val footY = 292f
    private val headX = 186f
    private val headY = 169f
    private val fogX = 161f
    private val fogY = 264f
    // cx, cy, semieje horizontal, semieje vertical del rin
    private val wheels = arrayOf(
        floatArrayOf(313f, 261f, 40f, 52f),
        floatArrayOf(637f, 220f, 24f, 42f)
    )
    private val slopeDeg = 8.3f

    var running = true
        set(v) { field = v; lastFrame = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }

    private var nightTarget = 0f
    private var nightAmt = 0f
    private var lastFrame = 0L
    private var offFar = 0f
    private var offHill = 0f
    private var offTree = 0f
    private var offRoad = 0f
    private var wheelAngle = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val carPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrim = Paint()
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val wheelPath = Path()
    private val beamPath = Path()
    private val rf = RectF()

    private var farPath = Path()
    private var hillPath = Path()
    private var treePath = Path()
    private var shL: Shader? = null
    private var shR: Shader? = null
    private var shB: Shader? = null
    private var lampGlow: Shader? = null
    private var shadowShader: Shader? = null
    private val starX = FloatArray(70)
    private val starY = FloatArray(70)
    private val starR = FloatArray(70)

    fun setNight(n: Boolean) {
        nightTarget = if (n) 1f else 0f
        postInvalidateOnAnimation()
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val r = Color.red(a) + (Color.red(b) - Color.red(a)) * t
        val g = Color.green(a) + (Color.green(b) - Color.green(a)) * t
        val bl = Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t
        return Color.rgb(r.toInt(), g.toInt(), bl.toInt())
    }

    private fun ridge(w: Float, base: Float, bottom: Float, ks: IntArray, amps: FloatArray, ph: FloatArray): Path {
        val path = Path()
        path.moveTo(0f, bottom)
        var x = 0f
        while (x <= w) {
            var y = base
            for (i in ks.indices) {
                y -= amps[i] * sin(2.0 * Math.PI * ks[i] * x / w + ph[i]).toFloat()
            }
            path.lineTo(x, y)
            x += 8f
        }
        path.lineTo(w, bottom)
        path.close()
        return path
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val s = Ui.s
        val wf = w.toFloat()
        val hf = h.toFloat()
        val horizon = hf * 0.36f
        farPath = ridge(wf, horizon - 18f * s, horizon + 40f * s,
            intArrayOf(2, 5, 11), floatArrayOf(50f * s, 28f * s, 12f * s), floatArrayOf(0.4f, 1.9f, 3.1f))
        hillPath = ridge(wf, horizon + 6f * s, horizon + 60f * s,
            intArrayOf(3, 8, 15), floatArrayOf(20f * s, 10f * s, 5f * s), floatArrayOf(1.2f, 0.3f, 2.2f))
        treePath = Path()
        val rnd = Random(11)
        val tb = horizon + 26f * s
        for (i in 0 until 26) {
            val x = i * wf / 26f + rnd.nextFloat() * 20f * s
            val th = (28f + rnd.nextFloat() * 38f) * s
            val tw = (10f + rnd.nextFloat() * 10f) * s
            rf.set(x - tw, tb - th, x + tw, tb - th * 0.2f)
            treePath.addOval(rf, Path.Direction.CW)
            rf.set(x - 2f * s, tb - th * 0.3f, x + 2f * s, tb)
            treePath.addRect(rf, Path.Direction.CW)
        }
        val dark = 0xB0000000.toInt()
        shL = LinearGradient(0f, 0f, wf * 0.34f, 0f, dark, 0x00000000, Shader.TileMode.CLAMP)
        shR = LinearGradient(wf, 0f, wf * 0.64f, 0f, dark, 0x00000000, Shader.TileMode.CLAMP)
        shB = LinearGradient(0f, hf, 0f, hf * 0.76f, dark, 0x00000000, Shader.TileMode.CLAMP)
        lampGlow = RadialGradient(0f, 0f, 70f * s,
            intArrayOf(Color.argb(210, 255, 236, 180), Color.argb(70, 255, 220, 150), Color.argb(0, 255, 220, 150)),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        shadowShader = RadialGradient(0f, 0f, 300f * s,
            intArrayOf(Color.argb(150, 0, 0, 0), Color.argb(70, 0, 0, 0), Color.argb(0, 0, 0, 0)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        val sr = Random(5)
        for (i in starX.indices) {
            starX[i] = sr.nextFloat() * wf
            starY[i] = sr.nextFloat() * horizon * 0.95f
            starR[i] = (0.8f + sr.nextFloat() * 1.6f)
        }
    }

    private fun drawTile(c: Canvas, path: Path, off: Float, w: Float) {
        val o = ((off % w) + w) % w
        c.save()
        c.translate(o - w, 0f)
        c.drawPath(path, p)
        c.translate(w, 0f)
        c.drawPath(path, p)
        c.restore()
    }

    private fun spinWheel(c: Canvas, wh: FloatArray) {
        val bmp = car ?: return
        val cx = wh[0]
        val cy = wh[1]
        val a = wh[2]
        val b = wh[3]
        rf.set(cx - a * 0.96f, cy - b * 0.96f, cx + a * 0.96f, cy + b * 0.96f)
        wheelPath.reset()
        wheelPath.addOval(rf, Path.Direction.CW)
        for (pass in 0..1) {
            c.save()
            c.clipPath(wheelPath)
            c.translate(cx, cy)
            c.scale(a / b, 1f)
            c.rotate(wheelAngle - pass * 14f)
            c.scale(b / a, 1f)
            c.translate(-cx, -cy)
            carPaint.alpha = if (pass == 0) 255 else 110
            c.drawBitmap(bmp, 0f, 0f, carPaint)
            c.restore()
        }
        carPaint.alpha = 255
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = Ui.s
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(1L, 100L) / 1000f
        lastFrame = now
        var animating = false

        if (nightAmt != nightTarget) {
            val d = nightTarget - nightAmt
            nightAmt += if (d > 0) min(d, dt * 1.6f) else -min(-d, dt * 1.6f)
            animating = true
        }
        val a = nightAmt

        if (running) {
            offFar += 10f * s * dt
            offHill += 30f * s * dt
            offTree += 90f * s * dt
            offRoad += 300f * s * dt
            wheelAngle -= 368f * dt
            if (wheelAngle < -3600f) wheelAngle += 3600f
        }

        val horizon = h * 0.36f
        val theta = Math.toRadians(slopeDeg.toDouble())
        val cosT = cos(theta).toFloat()
        val sinT = sin(theta).toFloat()
        val px = w * 0.535f
        val py = h * 0.70f
        val sc = 0.85f * s

        // ---- cielo
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, horizon + 30f * s,
            mix(Color.parseColor("#5E82A6"), Color.parseColor("#040711"), a),
            mix(Color.parseColor("#CFDCE7"), Color.parseColor("#17233D"), a), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, horizon + 40f * s, p)
        p.shader = null

        // estrellas
        if (a > 0.02f) {
            p.color = Color.WHITE
            p.alpha = (230 * a).toInt()
            for (i in starX.indices) c.drawCircle(starX[i], starY[i], starR[i] * s, p)
            p.alpha = 255
        }
        // sol / luna
        if (a < 0.98f) {
            lightPaint.shader = RadialGradient(w * 0.78f, horizon * 0.5f, 170f * s,
                Color.argb((150 * (1f - a)).toInt(), 255, 240, 200), Color.argb(0, 255, 240, 200), Shader.TileMode.CLAMP)
            c.drawCircle(w * 0.78f, horizon * 0.5f, 170f * s, lightPaint)
            lightPaint.shader = null
        }
        if (a > 0.02f) {
            p.color = Color.argb((255 * a).toInt(), 232, 238, 248)
            c.drawCircle(w * 0.78f, horizon * 0.45f, 22f * s, p)
        }

        // ---- montanas, colinas, arboles (paralaje)
        p.color = mix(Color.parseColor("#51657A"), Color.parseColor("#0D1526"), a)
        drawTile(c, farPath, offFar, w)
        p.color = mix(Color.parseColor("#3E5160"), Color.parseColor("#0A101D"), a)
        drawTile(c, hillPath, offHill, w)
        // campo
        p.color = mix(Color.parseColor("#3A4C40"), Color.parseColor("#070C0B"), a)
        c.drawRect(0f, horizon + 30f * s, w, h, p)
        p.color = mix(Color.parseColor("#2C3B38"), Color.parseColor("#060A12"), a)
        drawTile(c, treePath, offTree, w)

        // ---- carretera (inclinada, en perspectiva 3/4)
        c.save()
        c.rotate(-slopeDeg, px, py)
        p.color = mix(Color.parseColor("#2D3036"), Color.parseColor("#0E1014"), a)
        c.drawRect(-w, py - 118f * s, 2f * w, py + 210f * s, p)
        val lineCol = mix(Color.parseColor("#ECECEC"), Color.parseColor("#6A7079"), a)
        p.color = lineCol
        c.drawRect(-w, py - 118f * s, 2f * w, py - 113f * s, p)       // borde lejano
        c.drawRect(-w, py + 200f * s, 2f * w, py + 206f * s, p)       // borde cercano
        val period = 170f * s
        val dashLen = 90f * s
        val o = ((offRoad % period) + period) % period
        var x = -w - period + o
        while (x < 2f * w) {
            c.drawRect(x, py + 52f * s, x + dashLen, py + 60f * s, p)
            x += period
        }
        // lineas de velocidad en primer plano
        if (running) {
            p.color = Color.argb(34, 255, 255, 255)
            val sp = 330f * s
            val o2 = ((offRoad * 2.2f % sp) + sp) % sp
            var k = 0
            while (k < 3) {
                var x2 = -w - sp + o2 + k * 110f * s
                while (x2 < 2f * w) {
                    c.drawRect(x2, py + 140f * s + k * 18f * s, x2 + 120f * s, py + 142f * s + k * 18f * s, p)
                    x2 += sp
                }
                k++
            }
        }
        c.restore()

        // ---- postes de luz (verticales, a lo largo del borde lejano)
        val postP = 520f * s
        val baseU = ((offRoad % postP) + postP) % postP
        val v = -128f * s
        for (i in -2..2) {
            val u = baseU + i * postP
            val sx = px + u * cosT + v * sinT
            val sy = py - u * sinT + v * cosT
            if (sx < -80f || sx > w + 80f) continue
            p.color = mix(Color.parseColor("#55606B"), Color.parseColor("#1B222B"), a)
            p.strokeWidth = 6f * s
            p.style = Paint.Style.STROKE
            c.drawLine(sx, sy, sx, sy - 150f * s, p)
            c.drawLine(sx, sy - 150f * s, sx - 26f * s, sy - 146f * s, p)
            p.style = Paint.Style.FILL
            if (a > 0.02f) {
                lightPaint.shader = lampGlow
                lightPaint.alpha = (255 * a).toInt()
                lightPaint.xfermode = screen
                c.save()
                c.translate(sx - 26f * s, sy - 142f * s)
                c.drawCircle(0f, 0f, 70f * s, lightPaint)
                c.restore()
                lightPaint.xfermode = null
                lightPaint.shader = null
                lightPaint.alpha = 255
            }
        }

        // ---- sombra bajo el coche
        c.save()
        c.rotate(-slopeDeg, px, py)
        c.translate(px, py + 8f * s)
        c.scale(1f, 0.13f)
        p.shader = shadowShader
        c.drawCircle(0f, 0f, 300f * s, p)
        p.shader = null
        c.restore()

        // ---- coche
        val bmp = car
        if (bmp != null) {
            val k = 1f - 0.55f * a
            val cm = ColorMatrix(floatArrayOf(
                k, 0f, 0f, 0f, 0f,
                0f, 1f - 0.52f * a, 0f, 0f, 0f,
                0f, 0f, 1f - 0.38f * a, 0f, 8f * a,
                0f, 0f, 0f, 1f, 0f
            ))
            carPaint.colorFilter = ColorMatrixColorFilter(cm)
            val t = now / 1000f
            val bob = if (running) sin(t * 10.7f) * 1.3f * s else 0f
            val pitch = if (running) sin(t * 5.3f) * 0.25f else 0f

            c.save()
            c.translate(px, py + bob)
            c.rotate(pitch)
            c.scale(sc, sc)
            c.translate(-footX, -footY)
            carPaint.alpha = 255
            c.drawBitmap(bmp, 0f, 0f, carPaint)
            if (running) for (wh in wheels) spinWheel(c, wh)

            if (a > 0.01f) {
                // haz de los faros sobre la carretera
                beamPath.reset()
                beamPath.moveTo(headX, headY)
                beamPath.lineTo(-330f, 215f)
                beamPath.lineTo(-330f, 420f)
                beamPath.close()
                lightPaint.xfermode = screen
                lightPaint.shader = LinearGradient(headX, 0f, -330f, 0f,
                    Color.argb((130 * a).toInt(), 255, 244, 210), Color.argb(0, 255, 244, 210), Shader.TileMode.CLAMP)
                c.drawPath(beamPath, lightPaint)
                // brillo de faro y faro de niebla
                lightPaint.shader = RadialGradient(headX, headY, 62f,
                    intArrayOf(Color.argb((255 * a).toInt(), 255, 248, 225), Color.argb((110 * a).toInt(), 255, 240, 200), Color.argb(0, 255, 240, 200)),
                    floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(headX, headY, 62f, lightPaint)
                lightPaint.shader = RadialGradient(fogX, fogY, 34f,
                    intArrayOf(Color.argb((230 * a).toInt(), 255, 250, 235), Color.argb((90 * a).toInt(), 255, 245, 215), Color.argb(0, 255, 245, 215)),
                    floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(fogX, fogY, 34f, lightPaint)
                lightPaint.shader = null
                lightPaint.xfermode = null
            }
            c.restore()
        }

        // ---- sombras en los bordes para que los paneles flotantes se lean bien
        scrim.shader = shL
        c.drawRect(0f, 0f, w * 0.34f, h, scrim)
        scrim.shader = shR
        c.drawRect(w * 0.64f, 0f, w, h, scrim)
        scrim.shader = shB
        c.drawRect(0f, h * 0.76f, w, h, scrim)

        if (running) postInvalidateDelayed(33) else if (animating) postInvalidateOnAnimation()
    }
}
