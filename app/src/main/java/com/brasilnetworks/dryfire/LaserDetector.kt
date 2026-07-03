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
 * Detecta o laser por CONTRASTE LOCAL (tophat), não por brilho absoluto.
 * O laser é um ponto muito mais claro que a vizinhança imediata dele,
 * então funciona igual em fundo claro ou escuro.
 */
class LaserDetector {

    data class Resultado(val x: Int, val y: Int, val pixels: Int)

    // quanto o laser "se destaca" do fundo ao redor (para diagnóstico)
    @Volatile var ultimoBrilhoMax = 0.0
        private set

    // limiar de destaque (contraste) para valer como laser
    private var contrasteMinimo = 60.0

    private var opencvOk = false
    private var matRgba: Mat? = null
    private var matGray: Mat? = null
    private var matTophat: Mat? = null
    private var kernel: Mat? = null

    init {
        opencvOk = OpenCVLoader.initLocal()
    }

    /** 0 = menos sensível (exige contraste alto) ... 100 = mais sensível. */
    fun definirSensibilidade(nivel: Int) {
        val n = nivel.coerceIn(0, 100)
        // nível 0 -> exige contraste 110 ; nível 100 -> exige contraste 30
        contrasteMinimo = (110 - n * 0.8).coerceIn(30.0, 110.0)
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
        val k = kernel ?: Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0)
        ).also { kernel = it }

        buffer.rewind()
        if (rowStride == largura * 4) {
            // (sem uso de cache aqui para simplificar; cópia direta)
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

        // TOPHAT = imagem - abertura(imagem) => realça pontos claros pequenos
        // (o laser) e apaga o fundo, seja ele claro ou escuro.
        Imgproc.morphologyEx(gray, tophat, Imgproc.MORPH_TOPHAT, k)

        val mm = Core.minMaxLoc(tophat)
        ultimoBrilhoMax = mm.maxVal   // agora isto é o "contraste" do pico

        if (mm.maxVal >= contrasteMinimo) {
            return Resultado(
                mm.maxLoc.x.toInt() + offsetX,
                mm.maxLoc.y.toInt() + offsetY,
                1
            )
        }
        return null
    }
}
