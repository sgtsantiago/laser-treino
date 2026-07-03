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
 * Analisa SOMENTE a região do alvo (ROI). Com a exposição escura e o
 * recorte, o laser é o único ponto brilhante possível na área analisada.
 */
class LaserDetector {

    data class Resultado(val x: Int, val y: Int, val pixels: Int)

    private var brilhoMinimo = 120.0

    private var opencvOk = false
    private var matRgba: Mat? = null
    private var matGray: Mat? = null
    private var bufferDados: ByteArray? = null

    init {
        opencvOk = OpenCVLoader.initLocal()
    }

    /** 0 = menos sensível ... 100 = mais sensível. */
    fun definirSensibilidade(nivel: Int) {
        val n = nivel.coerceIn(0, 100)
        brilhoMinimo = (220 - n * 1.4).coerceIn(80.0, 220.0)
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

        buffer.rewind()
        if (rowStride == largura * 4) {
            val tam = altura * largura * 4
            val dados = bufferDados?.takeIf { it.size == tam }
                ?: ByteArray(tam).also { bufferDados = it }
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

        // recorta só a região do alvo (se definida e válida)
        var offsetX = 0
        var offsetY = 0
        val area: Mat = if (roi != null &&
            roi.left >= 0 && roi.top >= 0 &&
            roi.right <= largura && roi.bottom <= altura &&
            roi.width() > 8 && roi.height() > 8
        ) {
            offsetX = roi.left
            offsetY = roi.top
            rgba.submat(
                org.opencv.core.Rect(roi.left, roi.top, roi.width(), roi.height())
            )
        } else {
            rgba
        }

        Imgproc.cvtColor(area, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val mm = Core.minMaxLoc(gray)
        if (mm.maxVal >= brilhoMinimo) {
            return Resultado(
                mm.maxLoc.x.toInt() + offsetX,
                mm.maxLoc.y.toInt() + offsetY,
                1
            )
        }
        return null
    }
}
