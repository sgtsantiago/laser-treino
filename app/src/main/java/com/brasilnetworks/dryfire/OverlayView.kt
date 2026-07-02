package com.brasilnetworks.dryfire

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Ponto(val nx: Float, val ny: Float)

    private var laserAtual: Ponto? = null
    private val tiros = mutableListOf<Ponto>()

    private val paintLaser = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val paintTiro = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun definirLaser(nx: Float, ny: Float) {
        laserAtual = Ponto(nx, ny)
        invalidate()
    }

    fun limparLaser() {
        laserAtual = null
        invalidate()
    }

    fun adicionarTiro(nx: Float, ny: Float) {
        tiros.add(Ponto(nx, ny))
        invalidate()
    }

    fun zerar() {
        tiros.clear()
        laserAtual = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        for (t in tiros) {
            canvas.drawCircle(t.nx * w, t.ny * h, 12f, paintTiro)
        }

        laserAtual?.let {
            val cx = it.nx * w
            val cy = it.ny * h
            canvas.drawCircle(cx, cy, 24f, paintLaser)
            canvas.drawLine(cx - 32f, cy, cx + 32f, cy, paintLaser)
            canvas.drawLine(cx, cy - 32f, cx, cy + 32f, paintLaser)
        }
    }
}
