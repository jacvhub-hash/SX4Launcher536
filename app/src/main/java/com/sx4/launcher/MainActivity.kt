package com.sx4.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val h = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var car: CarView
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvStatus: TextView
    private lateinit var cover: ImageView

    private val dateFmt = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "MX"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics
        Ui.s = minOf(dm.widthPixels / 1280f, dm.heightPixels / 720f)
        buildUi()
        updateClock()
    }

    override fun onResume() {
        super.onResume()
        h.removeCallbacks(ticker)
        h.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(ticker)
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            h.postDelayed(this, 1000)
        }
    }

    private fun glass(radius: Int = 24, fill: Int = 0x55061018): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = Ui.px(radius.toFloat())
        g.setStroke(Ui.pxi(1), 0x44FFFFFF)
        return g
    }

    private fun tv(t: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(size))
        v.setTextColor(color)
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        return v
    }

    private fun buildUi() {
        root = FrameLayout(this)

        car = CarView(this)
        root.addView(car, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.setPadding(Ui.pxi(24), Ui.pxi(16), Ui.pxi(24), Ui.pxi(16))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL

        // Panel Izquierdo: LOGO Suzuki y texto "SUZUKI SX4" más grande
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.gravity = Gravity.CENTER_HORIZONTAL
        left.background = glass(28)
        left.setPadding(Ui.pxi(12), Ui.pxi(12), Ui.pxi(12), Ui.pxi(12))

        val brand = LinearLayout(this)
        brand.orientation = LinearLayout.HORIZONTAL
        brand.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_suzuki)
        brand.addView(logo, LinearLayout.LayoutParams(Ui.pxi(54), Ui.pxi(54)))
        brand.addView(tv("  SUZUKI SX4", 26f, 0xFFE6ECF2.toInt(), true))
        left.addView(brand)

        tvTime = tv("--:--", 64f, Color.WHITE, true)
        tvTime.gravity = Gravity.CENTER
        left.addView(tvTime)
        tvDate = tv("", 18f, 0xCCFFFFFF.toInt())
        tvDate.gravity = Gravity.CENTER
        left.addView(tvDate)

        top.addView(left, LinearLayout.LayoutParams(Ui.pxi(320), ViewGroup.LayoutParams.WRAP_CONTENT))

        top.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        // Panel Derecho: Música con el logotipo de Bluetooth
        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        right.background = glass(28)
        right.setPadding(Ui.pxi(16), Ui.pxi(16), Ui.pxi(16), Ui.pxi(16))

        val info = LinearLayout(this)
        info.orientation = LinearLayout.HORIZONTAL
        info.gravity = Gravity.CENTER_VERTICAL
        cover = ImageView(this)
        cover.scaleType = ImageView.ScaleType.CENTER_CROP
        cover.setImageResource(R.drawable.logo_suzuki) 
        cover.clipToOutline = true
        cover.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, Ui.px(16f))
            }
        }
        
        val btIndicator = TextView(this)
        btIndicator.text = "📶 BT Audio"
        btIndicator.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(14f))
        btIndicator.setTextColor(Color.parseColor("#6FD3FF"))
        
        info.addView(cover, LinearLayout.LayoutParams(Ui.pxi(80), Ui.pxi(80)))
        
        val txtCol = LinearLayout(this)
        txtCol.orientation = LinearLayout.VERTICAL
        tvTitle = tv("Bluetooth Audio", 23f, Color.WHITE, true)
        tvArtist = tv("Conectado", 17f, 0xDDFFFFFF.toInt())
        txtCol.addView(tvTitle)
        txtCol.addView(tvArtist)
        txtCol.addView(btIndicator)

        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.leftMargin = Ui.pxi(12)
        info.addView(txtCol, tlp)
        right.addView(info)

        tvStatus = tv("Reproduciendo vía Bluetooth", 13f, 0xAAFFFFFF.toInt())
        right.addView(tvStatus)

        top.addView(right, LinearLayout.LayoutParams(Ui.pxi(360), ViewGroup.LayoutParams.WRAP_CONTENT))

        main.addView(top)
        root.addView(main, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun updateClock() {
        val cal = Calendar.getInstance()
        var hr = cal.get(Calendar.HOUR)
        if (hr == 0) hr = 12
        val mm = cal.get(Calendar.MINUTE)
        val ap = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val base = "$hr:${if (mm < 10) "0" else ""}$mm"
        tvTime.text = "$base $ap"
        tvDate.text = dateFmt.format(Date()).replaceFirstChar { it.uppercase() }
    }
}
