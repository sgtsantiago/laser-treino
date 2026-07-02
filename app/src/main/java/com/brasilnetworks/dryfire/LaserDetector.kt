package com.brasilnetworks.dryfire

import java.nio.ByteBuffer

class LaserDetector {

    data class Resultado(val x: Int, val y: Int, val pixels: Int)

    var limiarVermelho = 200
    var margem = 50
    var minPixels = 4

    fun analisar(buffer: ByteBuffer, largura: Int, altura: Int, rowStride: Int): Resultado? {
        var somaX = 0L
        var somaY = 0L
        var contagem = 0

        val passo = 2

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
