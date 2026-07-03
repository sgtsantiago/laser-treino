package com.brasilnetworks.dryfire

import android.graphics.Rect
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

/**
 * Detecta o laser combinando:
 *  1) CONTRASTE LOCAL (tophat) - ponto claro que se destaca do fundo
 *  2) MUDANÇA NO TEMPO - o ponto acabou de surgir (não estava no frame anterior)
 *
 * Só o laser satisfaz os dois: bordas/quinas têm contraste mas são estáticas.
 */
class LaserDetector {

    data class Resultado(val x: Int, val y: Int, val pixels: Int)

    @Volatile var ultimoBrilhoMax = 0.0
        private set

    private var limiar = 45.0

    private var opencvOk = false
    private var matRgba: Mat? = null
    private var matGray: Mat? = null
    private var matTophat: Mat? = null
    private var matAnterior: Mat? = null
    private var matDiff: Mat? = null
    private var matCombinado: Mat? = null
    private var kernel: Mat? = null

    init {
        opencvOk = OpenCVLoader.initLocal()
    }

    /** 0 = menos sensível ... 100 = mais sensível. */
    fun definirSensibilidade(nivel: Int) {
        val n = nivel.coerceIn(0, 100)
        limiar = (90 - n * 0.6).coerceIn(30.0, 90.0)
    }

    fun analisar(
        buffer: ByteBuffer,
        largura: Int,
        altura: Int,
        rowStride: Int,
        roi: Rect? = null
    ): Resultado? {
        if (!opencvOk) return null

        val rgba = matRgba ?: Mat(altura, largura, CvType.CV_8UC4).also { matRgba = it }
        val gray = matGray ?: Mat().also { matGray = it }
        val tophat = matTophat ?: Mat().also { matTophat = it }
        val diff = matDiff ?: Mat().also { matDiff = it }
        val combinado = matCombinado ?: Mat().also { matCombinado = it }
        val k = kernel ?: Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0)
        ).also { kernel = it }

        buffer.rewind()
        if (rowStride == largura * 4) {
            val dados = ByteArray(altura * largura * 4)
            buffer.get(dados)
            rgba.put(0, 0, dados)
        } else {
            val linha = ByteArray(largura * 4)
            for (y in 0 until altura) {
                buffer.position(y * rowStride)
                buffer.get(linha, 0, largura * 4)
                rgba.put(y, 0, linha)
            }
        }

        var offsetX = 0
        var offsetY = 0
        val area: Mat = if (roi != null &&
            roi.left >= 0 && roi.top >= 0 &&
            roi.right <= largura && roi.bottom <= altura &&
            roi.width() > 16 && roi.height() > 16
        ) {
            offsetX = roi.left
            offsetY = roi.top
            rgba.submat(org.opencv.core.Rect(roi.left, roi.top, roi.width(), roi.height()))
        } else {
            rgba
        }

        Imgproc.cvtColor(area, gray, Imgproc.COLOR_RGBA2GRAY)

        // 1) contraste local (realça pontos claros pequenos = laser)
        Imgproc.morphologyEx(gray, tophat, Imgproc.MORPH_TOPHAT, k)

        // 2) diferença em relação ao frame anterior (o que mudou agora)
        val anterior = matAnterior
        if (anterior == null || anterior.rows() != gray.rows() || anterior.cols() != gray.cols()) {
            matAnterior?.release()
            matAnterior = gray.clone()
            ultimoBrilhoMax = 0.0
            return null
        }
        Core.absdiff(gray, anterior, diff)
        gray.copyTo(anterior)

        // combina: precisa ter contraste (tophat) E ter mudado (diff)
        Core.min(tophat, diff, combinado)

        val mm = Core.minMaxLoc(combinado)
        ultimoBrilhoMax = mm.maxVal

        if (mm.maxVal >= limiar) {
            return Resultado(
                mm.maxLoc.x.toInt() + offsetX,
                mm.maxLoc.y.toInt() + offsetY,
                1
            )
        }
        return null
    }
}
