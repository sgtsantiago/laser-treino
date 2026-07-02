package com.brasilnetworks.dryfire

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private enum class Estado { CALIBRANDO, CONTAGEM, ATIRANDO, RESULTADO, PAUSADO }

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var statusText: TextView
    private lateinit var painelConfig: LinearLayout
    private lateinit var edtDisparos: EditText
    private lateinit var edtTempo: EditText
    private lateinit var btnStart: Button
    private lateinit var btnPause: Button
    private lateinit var telaDestaque: LinearLayout
    private lateinit var txtGrande: TextView
    private lateinit var txtDetalhe: TextView

    private val detector = LaserDetector()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var estado = Estado.CALIBRANDO

    private val tom = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var playerTiro: MediaPlayer? = null

    private var maxDisparos = 10
    private var maxTempoSeg = 0

    private var disparosFeitos = 0
    private var pontosSerie = 0
    private var inicioSerieMs = 0L

    private var laserPresenteAntes = false
    private var ultimoTiroMs = 0L

    private val checaTempo = object : Runnable {
        override fun run() {
            if (estado == Estado.ATIRANDO) {
                val decorrido = (System.currentTimeMillis() - inicioSerieMs) / 1000.0
                atualizarPlacar(decorrido)
                if (maxTempoSeg > 0 && decorrido >= maxTempoSeg) {
                    finalizarSerie()
                    return
                }
                handler.postDelayed(this, 100)
            }
        }
    }

    private val pedirCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) iniciarCamera()
            else statusText.text = "Permissão de câmera negada"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        statusText = findViewById(R.id.statusText)
        painelConfig = findViewById(R.id.painelConfig)
        edtDisparos = findViewById(R.id.edtDisparos)
        edtTempo = findViewById(R.id.edtTempo)
        btnStart = findViewById(R.id.btnStart)
        btnPause = findViewById(R.id.btnPause)
        telaDestaque = findViewById(R.id.telaDestaque)
        txtGrande = findViewById(R.id.txtDestaqueGrande)
        txtDetalhe = findViewById(R.id.txtDestaqueDetalhe)

        playerTiro = MediaPlayer.create(this, R.raw.gunshot)

        findViewById<Button>(R.id.btnDisparosMais).setOnClickListener {
            edtDisparos.setText((lerInt(edtDisparos, 10) + 1).toString())
        }
        findViewById<Button>(R.id.btnDisparosMenos).setOnClickListener {
            edtDisparos.setText((lerInt(edtDisparos, 10) - 1).coerceAtLeast(1).toString())
        }
        findViewById<Button>(R.id.btnTempoMais).setOnClickListener {
            edtTempo.setText((lerInt(edtTempo, 0) + 1).toString())
        }
        findViewById<Button>(R.id.btnTempoMenos).setOnClickListener {
            edtTempo.setText((lerInt(edtTempo, 0) - 1).coerceAtLeast(0).toString())
        }

        btnStart.setOnClickListener { aoApertarStart() }
        btnPause.setOnClickListener { aoApertarPause() }

        overlay.aoPontuar = { _, totalSerie ->
            tocarTiro()
            pontosSerie = totalSerie
            disparosFeitos++
            val decorrido = (System.currentTimeMillis() - inicioSerieMs) / 1000.0
            atualizarPlacar(decorrido)
            if (disparosFeitos >= maxDisparos) {
                finalizarSerie()
            }
        }

        entrarCalibracao()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            iniciarCamera()
        } else {
            pedirCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun entrarCalibracao() {
        estado = Estado.CALIBRANDO
        overlay.modoContagem = false
        overlay.zerar()
        painelConfig.visibility = View.VISIBLE
        btnStart.visibility = View.VISIBLE
        btnPause.visibility = View.GONE
        telaDestaque.visibility = View.GONE
        statusText.text = "Calibre o alvo e aperte START"
    }

    private fun aoApertarStart() {
        maxDisparos = lerInt(edtDisparos, 10).coerceAtLeast(1)
        maxTempoSeg = lerInt(edtTempo, 0).coerceAtLeast(0)
        painelConfig.visibility = View.GONE
        iniciarContagemRegressiva()
    }

    private fun aoApertarPause() {
        handler.removeCallbacksAndMessages(null)
        entrarCalibracao()
    }

    private fun iniciarContagemRegressiva() {
        estado = Estado.CONTAGEM
        telaDestaque.visibility = View.VISIBLE
        txtDetalhe.text = "Prepare-se"
        contar(3)
    }

    private fun contar(n: Int) {
        if (n <= 0) {
            tom.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 250)
            iniciarSerie()
            return
        }
        txtGrande.text = n.toString()
        tom.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        handler.postDelayed({ contar(n - 1) }, 1000)
    }

    private fun iniciarSerie() {
        estado = Estado.ATIRANDO
        disparosFeitos = 0
        pontosSerie = 0
        inicioSerieMs = System.currentTimeMillis()
        overlay.zerar()
        overlay.modoContagem = true
        telaDestaque.visibility = View.GONE
        btnPause.visibility = View.VISIBLE
        painelConfig.visibility = View.VISIBLE
        atualizarPlacar(0.0)
        handler.postDelayed(checaTempo, 100)
    }

    private fun finalizarSerie() {
        if (estado != Estado.ATIRANDO) return
        estado = Estado.RESULTADO
        handler.removeCallbacks(checaTempo)
        overlay.modoContagem = false

        val tempo = (System.currentTimeMillis() - inicioSerieMs) / 1000.0
        val fator = if (tempo > 0) pontosSerie / tempo else 0.0

        tom.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)

        btnPause.visibility = View.GONE
        painelConfig.visibility = View.GONE
        telaDestaque.visibility = View.VISIBLE
        txtGrande.text = "$pontosSerie pts"
        txtDetalhe.text = "Tempo: %.2f s\nFator de tiro: %.2f".format(tempo, fator)

        handler.postDelayed({
            if (estado == Estado.RESULTADO) {
                iniciarContagemRegressiva()
            }
        }, 3000)
    }

    private fun atualizarPlacar(tempoSeg: Double) {
        val ultimo = overlay.ultimoPonto
        statusText.text = "Último: $ultimo | Total: $pontosSerie | " +
                "Tiro: $disparosFeitos/$maxDisparos | Tempo: %.1fs".format(tempoSeg)
    }

    private fun iniciarCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val cameraProvider = future.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                processarFrame(imageProxy)
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(TransformExperimental::class)
    private fun processarFrame(imageProxy: ImageProxy) {
        try {
            val plane = imageProxy.planes[0]
            val resultado = detector.analisar(
                plane.buffer, imageProxy.width, imageProxy.height, plane.rowStride
            )

            if (resultado != null) {
                val px = resultado.x.toFloat()
                val py = resultado.y.toFloat()
                val origem = ImageProxyTransformFactory().getOutputTransform(imageProxy)

                runOnUiThread {
                    val ponto = floatArrayOf(px, py)
                    val destino: OutputTransform? = previewView.outputTransform
                    if (destino != null) {
                        CoordinateTransform(origem, destino).mapPoints(ponto)
                    }
                    val lv = previewView.width.toFloat().coerceAtLeast(1f)
                    val av = previewView.height.toFloat().coerceAtLeast(1f)
                    val nx = (ponto[0] / lv).coerceIn(0f, 1f)
                    val ny = (ponto[1] / av).coerceIn(0f, 1f)

                    if (estado == Estado.ATIRANDO) {
                        val agora = System.currentTimeMillis()
                        val podeContar = !laserPresenteAntes && (agora - ultimoTiroMs > 300)
                        if (podeContar) {
                            ultimoTiroMs = agora
                            overlay.registrarTiro(nx, ny)
                        }
                        laserPresenteAntes = true
                        overlay.definirLaser(nx, ny)
                    }
                }
            } else {
                laserPresenteAntes = false
                if (estado == Estado.ATIRANDO) {
                    runOnUiThread { overlay.limparLaser() }
                }
            }
        } finally {
            imageProxy.close()
        }
    }

    private fun lerInt(edt: EditText, padrao: Int): Int {
        return edt.text.toString().toIntOrNull() ?: padrao
    }

    private fun tocarTiro() {
        try {
            playerTiro?.let {
                if (it.isPlaying) it.seekTo(0) else it.start()
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        analysisExecutor.shutdown()
        tom.release()
        playerTiro?.release()
    }
}
