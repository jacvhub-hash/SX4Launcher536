package com.sx4.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

class CarView(ctx: Context) : View(ctx) {

    private val frames: Array<Bitmap?> = arrayOf(
        BitmapFactory.decodeResource(resources, R.drawable.car_angle_0),
        BitmapFactory.decodeResource(resources, R.drawable.car_angle_1),
        BitmapFactory.decodeResource(resources, R.drawable.car_angle_2),
        BitmapFactory.decodeResource(resources, R.drawable.car_angle_3),
        BitmapFactory.decodeResource(resources, R.drawable.car_angle_4),
        BitmapFactory.decodeResource(resources, R.drawable.car_angle_5),
        BitmapFactory.decodeResource(resources, R.drawable.car_angle_6),
        BitmapFactory.decodeResource(resources, R.drawable.car_angle_7)
    )

    private var currentAngleIndex = 0
    private var lastTouchX = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                if (Math.abs(dx) > 20f) {
                    if (dx > 0) {
                        currentAngleIndex = (currentAngleIndex - 1 + frames.size) % frames.size
                    } else {
                        currentAngleIndex = (currentAngleIndex + 1) % frames.size
                    }
                    lastTouchX = event.x
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        c.drawColor(0xFF14171B.toInt())

        val currentBmp = frames[currentAngleIndex % frames.size]
        if (currentBmp != null) {
            val scale = minOf(w / currentBmp.width, h / currentBmp.height) * 0.75f
            val left = (w - currentBmp.width * scale) / 2f
            val top = (h - currentBmp.height * scale) / 2f
            
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            c.save()
            c.translate(left, top)
            c.scale(scale, scale)
            c.drawBitmap(currentBmp, 0f, 0f, paint)
            c.restore()
        }
    }
}
