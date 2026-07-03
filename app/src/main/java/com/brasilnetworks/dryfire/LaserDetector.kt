package com.brasilnetworks.dryfire

import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

class LaserDetector {

    data class Resultado(val x: Int, val y: Int, val pixels: Int)

    // brilho mínimo (0..255) para considerar que é o laser. A barra controla isto.
    private var brilhoMinimo = 230.0

    private var opencvOk = false
    private var matRgba: Mat? = null
    private var matGray: Mat? = null

    init {
        opencvOk = OpenCVLoader.initLocal()
    }

    /**
     * Sensibilidade de 0 (menos) a 100 (mais). Quanto maior, menor o brilho
     * exigido — detecta pontos mais fracos/menores.
     */
    fun definirSensibilidade(nivel: Int) {
        val n = nivel.coerceIn(0, 100)
        // nível 0 -> exige brilho 250 ; nível 100 -> exige brilho 150
        brilhoMinimo = (250 - n).toDouble().coerceIn(150.0, 250.0)
    }

    fun analisar(buffer: ByteBuffer, largura: Int, altura: Int, rowStride: Int): Resultado? {
        if (!opencvOk) return null

        // monta a Mat RGBA a partir do buffer da câmera
        val rgba = matRgba ?: Mat(altura, largura, CvType.CV_8UC4).also { matRgba = it }
        val gray = matGray ?: Mat().also { matGray = it }

        buffer.rewind()
        val linha = ByteArray(largura * 4)
        for (y in 0 until altura) {
            buffer.position(y * rowStride)
            buffer.get(linha, 0, largura * 4)
            rgba.put(y, 0, linha)
        }

        // converte para cinza e suaviza (o desfoque junta o ponto do laser num pico só)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(9.0, 9.0), 0.0)

        // acha o pixel mais brilhante
        val mm = Core.minMaxLoc(gray)
        if (mm.maxVal >= brilhoMinimo) {
            return Resultado(mm.maxLoc.x.toInt(), mm.maxLoc.y.toInt(), 1)
        }
        return null
    }
}
