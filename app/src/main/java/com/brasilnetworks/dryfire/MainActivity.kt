package com.brasilnetworks.dryfire

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var statusText: TextView

    private val detector = LaserDetector()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var laserPresenteAntes = false
    private var ultimoTiroMs = 0L
    private var contadorTiros = 0

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
        atualizarStatus()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            iniciarCamera()
        } else {
            pedirCamera.launch(Manifest.permission.CAMERA)
        }
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
                plane.buffer,
                imageProxy.width,
                imageProxy.height,
                plane.rowStride
            )

            if (resultado != null) {
                // ponto detectado no sistema da imagem de análise
                val ponto = floatArrayOf(resultado.x.toFloat(), resultado.y.toFloat())

                // transforma para o sistema do PreviewView (respeita rotação e corte)
                val destino: OutputTransform? = previewView.outputTransform
                if (destino != null) {
                    val origem = ImageProxyTransformFactory().getOutputTransform(imageProxy)
                    val transform = CoordinateTransform(origem, destino)
                    transform.mapPoints(ponto)
                }

                val larguraView = previewView.width.toFloat().coerceAtLeast(1f)
                val alturaView = previewView.height.toFloat().coerceAtLeast(1f)
                val nx = (ponto[0] / larguraView).coerceIn(0f, 1f)
                val ny = (ponto[1] / alturaView).coerceIn(0f, 1f)

                val agora = System.currentTimeMillis()
                val podeContar = !laserPresenteAntes && (agora - ultimoTiroMs > 300)
                if (podeContar) {
                    contadorTiros++
                    ultimoTiroMs = agora
                    runOnUiThread {
                        overlay.adicionarTiro(nx, ny)
                        atualizarStatus()
                    }
                }
                laserPresenteAntes = true
                runOnUiThread { overlay.definirLaser(nx, ny) }
            } else {
                laserPresenteAntes = false
                runOnUiThread { overlay.limparLaser() }
            }
        } finally {
            imageProxy.close()
        }
    }

    private fun atualizarStatus() {
        statusText.text = "Tiros: $contadorTiros"
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }
}
