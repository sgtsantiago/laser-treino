package com.brasilnetworks.dryfire

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var aoPontuar: ((pontosDoTiro: Int, total: Int) -> Unit)? = null

    var modoContagem = false

    var ultimoPonto = 0
        private set

    private data class Tiro(val x: Float, val y: Float, val pontos: Int)

    private var laserAtual: Pair<Float, Float>? = null
    private val tiros = mutableListOf<Tiro>()
    private var total = 0

    private var alvoCx = 0f
    private var alvoCy = 0f
    private var alvoRaio = 0f
    private var inicializado = false

    private val fatorZonaMedia = 0.62f
    private val fatorZonaCentral = 0.30f

    private val paintZonaExterna = Paint().apply {
        color = Color.parseColor("#5532C8FF"); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val paintZonaMedia = Paint().apply {
        color = Color.parseColor("#66FFC107"); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val paintZonaCentral = Paint().apply {
        color = Color.parseColor("#88F44336"); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val paintContorno = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true
    }
    private val paintLaser = Paint().apply {
        color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true
    }
    private val paintTiro = Paint().apply {
        color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true
    }
    private val paintTiroBorda = Paint().apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!inicializado && w > 0 && h > 0) {
            alvoCx = w / 2f
            alvoCy = h / 2f
            alvoRaio = (minOf(w, h) * 0.28f)
            inicializado = true
        }
    }

    private var arrastando = false
    private var ultimoX = 0f
    private var ultimoY = 0f
    private var distanciaInicial = 0f
    private var raioInicial = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (modoContagem) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                arrastando = true
                ultimoX = event.x
                ultimoY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    distanciaInicial = distancia(event)
                    raioInicial = alvoRaio
                    arrastando = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 && distanciaInicial > 0f) {
                    val escala = distancia(event) / distanciaInicial
                    alvoRaio = (raioInicial * escala).coerceIn(40f, width.toFloat())
                    invalidate()
                } else if (arrastando) {
                    alvoCx += event.x - ultimoX
                    alvoCy += event.y - ultimoY
                    ultimoX = event.x
                    ultimoY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                distanciaInicial = 0f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                arrastando = false
                distanciaInicial = 0f
            }
        }
        return true
    }

    private fun distancia(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy)
    }

    fun definirLaser(nx: Float, ny: Float) {
        laserAtual = Pair(nx * width, ny * height)
        invalidate()
    }

    fun limparLaser() {
        laserAtual = null
        invalidate()
    }

    fun registrarTiro(nx: Float, ny: Float) {
        val x = nx * width
        val y = ny * height
        val pontos = calcularPontos(x, y)
        ultimoPonto = pontos
        tiros.add(Tiro(x, y, pontos))
        total += pontos
        aoPontuar?.invoke(pontos, total)
        invalidate()
    }

    fun zerar() {
        tiros.clear()
        total = 0
        ultimoPonto = 0
        laserAtual = null
        invalidate()
    }

    private fun calcularPontos(x: Float, y: Float): Int {
        val larguraMeia = alvoRaio
        val alturaMeia = alvoRaio * 1.5f
        val dx = (x - alvoCx) / larguraMeia
        val dy = (y - alvoCy) / alturaMeia
        val d = hypot(dx, dy)
        return when {
            d <= fatorZonaCentral -> 5
            d <= fatorZonaMedia -> 3
            d <= 1.0f -> 1
            else -> 0
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!inicializado) return

        val lm = alvoRaio
        val am = alvoRaio * 1.5f

        canvas.drawOval(retangulo(lm, am), paintZonaExterna)
        canvas.drawOval(retangulo(lm, am), paintContorno)
        canvas.drawOval(retangulo(lm * fatorZonaMedia, am * fatorZonaMedia), paintZonaMedia)
        canvas.drawOval(retangulo(lm * fatorZonaMedia, am * fatorZonaMedia), paintContorno)
        canvas.drawOval(retangulo(lm * fatorZonaCentral, am * fatorZonaCentral), paintZonaCentral)
        canvas.drawOval(retangulo(lm * fatorZonaCentral, am * fatorZonaCentral), paintContorno)

        for (t in tiros) {
            canvas.drawCircle(t.x, t.y, 10f, paintTiro)
            canvas.drawCircle(t.x, t.y, 10f, paintTiroBorda)
        }

        laserAtual?.let { (cx, cy) ->
            canvas.drawCircle(cx, cy, 22f, paintLaser)
            canvas.drawLine(cx - 30f, cy, cx + 30f, cy, paintLaser)
            canvas.drawLine(cx, cy - 30f, cx, cy + 30f, paintLaser)
        }
    }

    private fun retangulo(lm: Float, am: Float) =
        RectF(alvoCx - lm, alvoCy - am, alvoCx + lm, alvoCy + am)
}
