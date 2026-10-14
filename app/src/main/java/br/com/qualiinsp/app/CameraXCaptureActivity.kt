package br.com.qualiinsp.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import android.text.InputType
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

/** CameraX: captura tradicional e modo de inspeção visual contínua. */
class CameraXCaptureActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var statusLight: TextView
    private lateinit var detail: TextView
    private var cameraReady = false
    private var boundCamera: Camera? = null
    private var torchEnabled = false
    private var gridEnabled = true
    private var measureMode = false
    private var pendingCalibrationMm: Float? = null
    private var millimetersPerPixel: Float? = null
    private var firstMeasurePoint: Pair<Float, Float>? = null
    private lateinit var gridOverlay: GridOverlayView
    private var flashButton: Button? = null
    private var gridButton: Button? = null
    private var measureButton: Button? = null
    private var liveMode = false
    private var videoMode = false
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var actionButton: Button? = null
    private var lastAnalysisMs = 0L
    private var latestStatus = "ATTENTION"
    private var latestReason = "Aguardando análise de imagem"
    private var analysis: ImageAnalysis? = null
    private lateinit var liveModel: LiveModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        liveMode = intent.getBooleanExtra(EXTRA_LIVE_MODE, false)
        videoMode = intent.getBooleanExtra(EXTRA_VIDEO_MODE, false)
        if (liveMode) {
            liveModel = try { LiveModel.fromJson(intent.getStringExtra(EXTRA_LIVE_MODEL) ?: "{}") }
            catch (_: Exception) { LiveModel.empty() }
        }
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        val frame = FrameLayout(this)
        frame.addView(previewView)
        gridOverlay = GridOverlayView(this)
        gridOverlay.visibility = View.VISIBLE
        gridOverlay.setOnTouchListener { _, event ->
            if (measureMode && event.action == MotionEvent.ACTION_UP) {
                handleMeasureTap(event.x, event.y)
                true
            } else false
        }
        frame.addView(gridOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 12, 24, 24)
            setBackgroundColor(0xDD101820.toInt())
        }
        statusLight = TextView(this).apply {
            text = if (liveMode) "🟡  PREPARANDO ANÁLISE" else if (videoMode && !intent.getStringExtra(EXTRA_VIDEO_TRAIN_TYPE).isNullOrBlank()) "GRAVAÇÃO 360° PARA TREINAMENTO" else if (videoMode) "GRAVAÇÃO MULTIÂNGULO" else "Câmera pronta"
            textSize = 19f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding(12, 14, 12, 8)
        }
        detail = TextView(this).apply {
            text = if (liveMode) "Mova a câmera lentamente e enquadre toda a peça. A grade ajuda a localizar regiões; confira detalhes e tolerâncias." else if (videoMode && intent.getStringExtra(EXTRA_VIDEO_TRAIN_TYPE) == "train_defect") "Grave o defeito em 360°: aproxime-se com foco e mostre o defeito de vários ângulos." else if (videoMode && intent.getStringExtra(EXTRA_VIDEO_TRAIN_TYPE) == "train_ok") "Grave a peça conforme em 360°: frente, verso, laterais, topo e base." else if (videoMode) "Grave 360° lentamente: frente, verso, laterais, topo, base e detalhes. Use boa iluminação e mantenha foco." else "Capture uma imagem nítida do produto. Ative o flash se precisar de mais luz."
            textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding(12, 4, 12, 12)
        }
        controls.addView(statusLight, LinearLayout.LayoutParams(-1, -2))
        controls.addView(detail, LinearLayout.LayoutParams(-1, -2))
        val cameraOptions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        flashButton = Button(this).apply {
            text = "⚡ FLASH OFF"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { toggleTorch(this) }
        }
        gridButton = Button(this).apply {
            text = "▦ GRADE ON"
            isAllCaps = false
            setOnClickListener {
                gridEnabled = !gridEnabled
                gridOverlay.visibility = if (gridEnabled) View.VISIBLE else View.GONE
                text = if (gridEnabled) "▦ GRADE ON" else "▦ GRADE OFF"
            }
        }
        measureButton = Button(this).apply {
            text = "📏 MEDIR"
            isAllCaps = false
            setOnClickListener { beginMeasureOrCalibration() }
        }
        cameraOptions.addView(flashButton, LinearLayout.LayoutParams(0, -2, 1f))
        cameraOptions.addView(gridButton, LinearLayout.LayoutParams(0, -2, 1f))
        cameraOptions.addView(measureButton, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(cameraOptions, LinearLayout.LayoutParams(-1, -2))
        actionButton = Button(this).apply {
            text = if (liveMode) "FINALIZAR INSPEÇÃO" else if (videoMode) "● INICIAR GRAVAÇÃO" else "CAPTURAR FOTO"
            isEnabled = false
            setOnClickListener { if (liveMode) finishLiveInspection() else if (videoMode) toggleVideoRecording(this) else takePhoto() }
        }
        controls.addView(actionButton, LinearLayout.LayoutParams(-1, -2))
        val cancelButton = Button(this).apply {
            text = "CANCELAR"
            setOnClickListener { setResult(Activity.RESULT_CANCELED); finish() }
        }
        controls.addView(cancelButton, LinearLayout.LayoutParams(-1, -2))
        frame.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(frame)

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(95)
                    .setTargetRotation(previewView.display?.rotation ?: windowManager.defaultDisplay.rotation)
                    .build()
                provider.unbindAll()
                if (videoMode) {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build()
                    val videoUseCase = VideoCapture.withOutput(recorder)
                    videoCapture = videoUseCase
                    boundCamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoUseCase)
                } else if (liveMode) {
                    analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build().also { useCase ->
                            useCase.setAnalyzer(cameraExecutor) { image -> analyzeFrame(image) }
                        }
                    boundCamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } else {
                    boundCamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }
                cameraReady = true
                actionButton?.isEnabled = true
                flashButton?.isEnabled = true
                if (boundCamera?.cameraInfo?.hasFlashUnit() != true) {
                    flashButton?.text = "⚡ SEM FLASH"
                    flashButton?.isEnabled = false
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Falha ao iniciar câmera: ${e.localizedMessage ?: "erro desconhecido"}", Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_CANCELED); finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleTorch(button: Button) {
        val camera = boundCamera
        if (camera == null || camera.cameraInfo.hasFlashUnit().not()) {
            Toast.makeText(this, "Este aparelho não disponibiliza flash pela câmera selecionada", Toast.LENGTH_LONG).show()
            return
        }
        val requested = !torchEnabled
        camera.cameraControl.enableTorch(requested).addListener({
            torchEnabled = requested
            runOnUiThread { button.text = if (torchEnabled) "⚡ FLASH ON" else "⚡ FLASH OFF" }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun beginMeasureOrCalibration() {
        if (millimetersPerPixel != null) {
            pendingCalibrationMm = null
            firstMeasurePoint = null
            gridOverlay.clearMeasure()
            measureMode = true
            detail.text = "MEDIÇÃO: toque nos dois extremos da dimensão. Estimativa válida somente na mesma distância e plano da calibração."
            measureButton?.text = "TOQUE 2 PONTOS"
            return
        }
        val input = EditText(this).apply {
            hint = "Ex.: 50 (milímetros)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Calibrar escala da câmera")
            .setMessage("Coloque uma referência de comprimento conhecido no mesmo plano do produto. Informe o comprimento real em milímetros; depois toque nos dois extremos dessa referência na imagem. A medição será aproximada.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ ->
                val mm = input.text.toString().replace(',', '.').toFloatOrNull()
                if (mm == null || mm <= 0f) {
                    Toast.makeText(this, "Informe um comprimento válido em mm", Toast.LENGTH_LONG).show()
                } else {
                    pendingCalibrationMm = mm
                    firstMeasurePoint = null
                    gridOverlay.clearMeasure()
                    measureMode = true
                    detail.text = "CALIBRAÇÃO: toque nos dois extremos da referência de ${mm} mm. Não mova a câmera durante a medição."
                    measureButton?.text = "TOQUE 2 PONTOS"
                }
            }.show()
    }

    private fun handleMeasureTap(x: Float, y: Float) {
        val first = firstMeasurePoint
        if (first == null) {
            firstMeasurePoint = x to y
            gridOverlay.setMeasurePoints(x, y, null, null)
            detail.text = "Primeiro ponto marcado. Toque no segundo extremo da referência/dimensão."
            return
        }
        val dx = x - first.first
        val dy = y - first.second
        val pixels = kotlin.math.sqrt(dx * dx + dy * dy)
        if (pixels < 4f) {
            Toast.makeText(this, "Pontos muito próximos; tente novamente", Toast.LENGTH_SHORT).show()
            firstMeasurePoint = null
            gridOverlay.clearMeasure()
            return
        }
        gridOverlay.setMeasurePoints(first.first, first.second, x, y)
        val knownMm = pendingCalibrationMm
        if (knownMm != null) {
            millimetersPerPixel = knownMm / pixels
            pendingCalibrationMm = null
            Toast.makeText(this, "Escala calibrada: ${String.format(java.util.Locale.getDefault(), "%.4f", millimetersPerPixel)} mm/pixel", Toast.LENGTH_LONG).show()
            detail.text = "Escala calibrada. Para medir, mantenha distância e plano iguais; se mover a câmera, calibre novamente."
        } else {
            val mm = pixels * (millimetersPerPixel ?: 0f)
            Toast.makeText(this, "Medida estimada: ${String.format(java.util.Locale.getDefault(), "%.2f", mm)} mm", Toast.LENGTH_LONG).show()
            detail.text = "Medida estimada: ${String.format(java.util.Locale.getDefault(), "%.2f", mm)} mm. Estimativa visual; valide medidas críticas com instrumento calibrado."
        }
        measureMode = false
        firstMeasurePoint = null
        measureButton?.text = "📏 MEDIR"
    }

    private fun toggleVideoRecording(button: Button) {
        if (!cameraReady) return
        if (recording != null) {
            button.isEnabled = false
            button.text = "FINALIZANDO VÍDEO..."
            recording?.stop()
            return
        }
        val capture = videoCapture ?: run { Toast.makeText(this, "Câmera de vídeo indisponível", Toast.LENGTH_LONG).show(); return }
        val file = File(cacheDir, "qualiinsp_video_${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(file).build()
        try {
            recording = capture.output.prepareRecording(this, options).start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        button.text = "■ PARAR E ANALISAR VÍDEO"
                        statusLight.text = "🔴 GRAVANDO PRODUTO"
                        detail.text = "Gire a peça lentamente e mostre todos os lados. Evite movimentos bruscos e reflexos."
                    }
                    is VideoRecordEvent.Status -> {
                        val secs = event.recordingStats.recordedDurationNanos / 1_000_000_000
                        detail.text = "Gravando há ${secs}s • mostre frente, verso, laterais, topo, base e detalhes."
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        button.isEnabled = true
                        if (event.hasError()) {
                            statusLight.text = "🟡 FALHA NA GRAVAÇÃO"
                            detail.text = "Não foi possível finalizar o vídeo. Tente novamente."
                            file.delete()
                            button.text = "● TENTAR NOVAMENTE"
                            Toast.makeText(this, "Erro ao gravar vídeo: ${event.error}", Toast.LENGTH_LONG).show()
                        } else {
                            val result = Intent()
                                .putExtra(EXTRA_VIDEO_PATH, file.absolutePath)
                                .putExtra(EXTRA_VIDEO_TRAIN_TYPE, intent.getStringExtra(EXTRA_VIDEO_TRAIN_TYPE) ?: "")
                                .putExtra(EXTRA_TRAIN_DEFECT_ID, intent.getStringExtra(EXTRA_TRAIN_DEFECT_ID) ?: "")
                            setResult(Activity.RESULT_OK, result)
                            finish()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            recording = null
            Toast.makeText(this, "Falha ao iniciar gravação: ${e.localizedMessage ?: "erro desconhecido"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun analyzeFrame(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (!liveMode || now - lastAnalysisMs < 450) return
            lastAnalysisMs = now
            val bitmap = rgbaToBitmap(image)
            val features = ML.features(bitmap)
            val result = liveModel.classify(features)
            runOnUiThread {
                latestStatus = result.first
                latestReason = result.second
                val (label, bg) = when (latestStatus) {
                    "OK" -> "🟢  CONFORME" to 0xFF197A45.toInt()
                    "BAD" -> "🔴  NÃO CONFORME" to 0xFFB3261E.toInt()
                    else -> "🟡  PRECISA DE AVALIAÇÃO" to 0xFF9A6700.toInt()
                }
                statusLight.text = label
                statusLight.setBackgroundColor(bg)
                detail.text = "$latestReason\nAnálise contínua ativa • resultado preliminar"
            }
            bitmap.recycle()
        } catch (_: Exception) {
            runOnUiThread { statusLight.text = "🟡  PRECISA DE AVALIAÇÃO"; detail.text = "Não foi possível analisar este quadro. Ajuste o foco e a iluminação." }
        } finally { image.close() }
    }

    private fun rgbaToBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        if (cropped !== padded) padded.recycle()
        val scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    private fun finishLiveInspection() {
        if (!cameraReady) return
        analysis?.clearAnalyzer()
        setResult(Activity.RESULT_OK, Intent()
            .putExtra(EXTRA_LIVE_RESULT, true)
            .putExtra(EXTRA_LIVE_STATUS, latestStatus)
            .putExtra(EXTRA_LIVE_REASON, latestReason))
        finish()
    }

    private fun takePhoto() {
        if (!cameraReady) return
        val outputFile = File(cacheDir, "qualiinsp_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_IMAGE_PATH, outputFile.absolutePath)); finish()
            }
            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(this@CameraXCaptureActivity, "Falha ao capturar foto: ${exception.message ?: "erro desconhecido"}", Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onDestroy() {
        recording?.close()
        recording = null
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "qualiinsp.image_path"
        const val EXTRA_VIDEO_MODE = "qualiinsp.video_mode"
        const val EXTRA_VIDEO_PATH = "qualiinsp.video_path"
        const val EXTRA_VIDEO_TRAIN_TYPE = "qualiinsp.video_train_type"
        const val EXTRA_TRAIN_DEFECT_ID = "qualiinsp.train_defect_id"
        const val EXTRA_LIVE_MODE = "qualiinsp.live_mode"
        const val EXTRA_LIVE_MODEL = "qualiinsp.live_model"
        const val EXTRA_LIVE_RESULT = "qualiinsp.live_result"
        const val EXTRA_LIVE_STATUS = "qualiinsp.live_status"
        const val EXTRA_LIVE_REASON = "qualiinsp.live_reason"
    }
}

private data class LiveDefect(val name: String, val threshold: Float, val critical: Boolean, val samples: List<List<Float>>)
private data class LiveModel(val references: List<List<Float>>, val defects: List<LiveDefect>) {
    fun classify(f: List<Float>): Pair<String, String> {
        if (references.isEmpty()) return "ATTENTION" to "Cadastre o padrão e exemplos CONFORME."
        val similarity = 1f - references.minOf { distance(f, it) }
        if (similarity < .45f) return "ATTENTION" to "Produto/padrão não identificado com segurança (${(similarity * 100).toInt()}%)."
        if (defects.isEmpty()) return "ATTENTION" to "Nenhum defeito cadastrado; não é seguro aprovar automaticamente."
        val untrained = defects.filter { it.samples.isEmpty() }
        if (untrained.isNotEmpty()) return "ATTENTION" to "Treinamento incompleto para: ${untrained.joinToString { it.name }}. Capture fotos ou grave cada defeito em 360° antes de liberar a peça."
        val hits = defects.mapNotNull { d ->
            if (d.samples.isEmpty()) null else {
                val confidence = 1f - d.samples.minOf { distance(f, it) }
                if (confidence >= d.threshold) d.name to (confidence to d.critical) else null
            }
        }
        if (hits.isNotEmpty()) return "BAD" to "Possível defeito: ${hits.joinToString { "${it.first} (${(it.second.first * 100).toInt()}%)" }}."
        if (similarity < .60f) return "ATTENTION" to "Semelhança baixa com padrão (${(similarity * 100).toInt()}%); confira a peça."
        return "OK" to "Nenhum defeito configurado reconhecido neste quadro (${(similarity * 100).toInt()}% de similaridade)."
    }
    companion object {
        fun empty() = LiveModel(emptyList(), emptyList())
        fun fromJson(text: String): LiveModel {
            val o = JSONObject(text)
            fun readRows(a: JSONArray?): List<List<Float>> {
                if (a == null) return emptyList()
                return (0 until a.length()).map { i -> val row=a.getJSONArray(i); (0 until row.length()).map { row.getDouble(it).toFloat() } }
            }
            val refs = mutableListOf<List<Float>>()
            val standard = o.optJSONArray("standard")
            if (standard != null && standard.length() > 0) refs.add((0 until standard.length()).map { standard.getDouble(it).toFloat() })
            refs.addAll(readRows(o.optJSONArray("okSamples")))
            val defects = mutableListOf<LiveDefect>()
            val da = o.optJSONArray("defects") ?: JSONArray()
            for (i in 0 until da.length()) {
                val d=da.getJSONObject(i)
                defects.add(LiveDefect(d.optString("name"), d.optDouble("threshold", .35).toFloat(), d.optBoolean("critical"), readRows(d.optJSONArray("samples"))))
            }
            return LiveModel(refs, defects)
        }
    }
}

private fun distance(a: List<Float>, b: List<Float>): Float {
    if (a.size != b.size || a.isEmpty()) return 1f
    var sum=0f
    for (i in a.indices) { val delta=a[i]-b[i]; sum += delta*delta }
    return (sqrt(sum.toDouble()).toFloat()/3.5f).coerceIn(0f,1f)
}


/** Grade visual para orientar o enquadramento; não representa segmentação automática do produto. */
private class GridOverlayView(context: android.content.Context) : View(context) {
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAA55D6FF.toInt()
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private var pointA: Pair<Float, Float>? = null
    private var pointB: Pair<Float, Float>? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 1..4) {
            val x = w * i / 5f
            val y = h * i / 5f
            canvas.drawLine(x, 0f, x, h, gridPaint)
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
        canvas.drawRect(w * .08f, h * .08f, w * .92f, h * .92f, gridPaint)
        val a = pointA
        val b = pointB
        if (a != null) canvas.drawCircle(a.first, a.second, 9f, pointPaint)
        if (a != null && b != null) {
            canvas.drawLine(a.first, a.second, b.first, b.second, pointPaint)
            canvas.drawCircle(b.first, b.second, 9f, pointPaint)
        }
    }

    fun setMeasurePoints(ax: Float, ay: Float, bx: Float?, by: Float?) {
        pointA = ax to ay
        pointB = if (bx != null && by != null) bx to by else null
        invalidate()
    }

    fun clearMeasure() {
        pointA = null
        pointB = null
        invalidate()
    }
}
