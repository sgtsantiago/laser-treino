package com.brasilnetworks.dryfire

import java.nio.ByteBuffer

class LaserDetector {

    data class Resultado(val x: Int, val y: Int, val pixels: Int)

    var limiarVermelho = 170
    var margem = 35
    var minPixels = 1

    /**
     * Ajusta a sensibilidade de 0 (menos sensível) a 100 (mais sensível).
     * Mais sensível = detecta pontos menores e mais fracos (mira de longe).
     */
    fun definirSensibilidade(nivel: Int) {
        val n = nivel.coerceIn(0, 100)
        // quanto maior o nível, menores os limiares e o mínimo de pixels
        limiarVermelho = (220 - (n * 0.9).toInt()).coerceIn(120, 220)  // 220..130
        margem = (60 - (n * 0.4).toInt()).coerceIn(20, 60)            // 60..20
        minPixels = when {
            n >= 80 -> 1
            n >= 50 -> 2
            n >= 25 -> 3
            else -> 5
        }
    }

    fun analisar(buffer: ByteBuffer, largura: Int, altura: Int, rowStride: Int): Resultado? {
        var somaX = 0L
        var somaY = 0L
        var contagem = 0

        val passo = 1

        var y = 0
        while (y < altura) {
            val base = y * rowStride
            var x = 0
            while (x < largura) {
                val i = base + x * 4
                val r = buffer.get(i).toInt() and 0xFF
                val g = buffer.get(i + 1).toInt() and 0xFF
                val b = buffer.get(i + 2).toInt() and 0xFF

                if (r >= limiarVermelho && (r - g) >= margem && (r - b) >= margem) {
                    somaX += x
                    somaY += y
                    contagem++
                }
                x += passo
            }
            y += passo
        }

        if (contagem < minPixels) return null
        return Resultado((somaX / contagem).toInt(), (somaY / contagem).toInt(), contagem)
    }
}
