package com.brasilnetworks.dryfire

import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

/**
 * Detecta o PULSO do laser comparando cada frame com o anterior.
 * O que "acende de repente" e é pontual = disparo. Coisas paradas
 * (parede, reflexos, luzes) não geram diferença e são ignoradas.
 */
class LaserDetector {

    data class Resultado(val x: Int, val y: Int, val pixels: Int)

    // quanto o ponto precisa "acender" em relação ao frame anterior (0..255)
    private var deltaMinimo = 60.0

    private var opencvOk = false
    private var matRgba: Mat? = null
    private var matGray: Mat? = null
    private var matAnterior: Mat? = null
    private var matDiff: Mat? = null

    init {
        opencvOk = OpenCVLoader.initLocal()
    }

    /** 0 = menos sensível (exige flash forte) ... 100 = mais sensível (flash fraco). */
    fun definirSensibilidade(nivel: Int) {
        val n = nivel.coerceIn(0, 100)
        deltaMinimo = (120 - n * 0.9).coerceIn(30.0, 120.0)
    }

    fun analisar(buffer: ByteBuffer, largura: Int, altura: Int, rowStride: Int): Resultado? {
        if (!opencvOk) return null

        val rgba = matRgba ?: Mat(altura, largura, CvType.CV_8UC4).also { matRgba = it }
        val gray = matGray ?: Mat().also { matGray = it }
        val diff = matDiff ?: Mat().also { matDiff = it }

        // copia o frame da câmera para o OpenCV
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

        // escala de cinza + leve desfoque (estabiliza o ruído)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        // primeiro frame: só guarda como referência
        val anterior = matAnterior
        if (anterior == null) {
            matAnterior = gray.clone()
            return null
        }

        // o que ficou MAIS CLARO em relação ao frame anterior
        Core.subtract(gray, anterior, diff)

        // atualiza a referência para o próximo ciclo
        gray.copyTo(anterior)

        // maior "acendimento" da cena
        val mm = Core.minMaxLoc(diff)
        if (mm.maxVal >= deltaMinimo) {
            val x = mm.maxLoc.x.toInt()
            val y = mm.maxLoc.y.toInt()
            // confirma que o ponto está de fato claro no frame atual (evita ruído)
            val brilho = gray.get(y, x)
            if (brilho != null && brilho[0] >= 150.0) {
                return Resultado(x, y, 1)
            }
        }
        return null
    }
}
