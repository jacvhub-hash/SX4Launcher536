package com.sx4.launcher

object Ui {
    var s: Float = 1.0f

    fun px(v: Float): Float = v * s
    fun pxi(v: Float): Int = (v * s).toInt()
    fun pxi(v: Int): Int = (v * s).toInt()
}
