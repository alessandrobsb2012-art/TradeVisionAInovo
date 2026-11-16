package com.tradevisionai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var regionOverlay: ChartRegionOverlay
    private lateinit var status: TextView
    private lateinit var timerText: TextView
    private lateinit var captureButton: Button
    private var imageCapture: ImageCapture? = null
    private var timer: CountDownTimer? = null
    private lateinit var cameraExecutor: ExecutorService

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else status.text = "Permissão da câmera necessária."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        buildScreen()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
        startMinuteTimer()
    }

    private fun buildScreen() {
        val bg = Color.rgb(16, 24, 32)
        val green = Color.rgb(54, 201, 143)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "TradeVision AI"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "IQ Option • M1 • Expiração de 1 minuto"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 8)
        }
        val instruction = TextView(this).apply {
            text = "Enquadre o gráfico dentro da área verde. Arraste o quadro para posicionar; arraste o canto inferior direito para redimensionar."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        regionOverlay = ChartRegionOverlay(this)
        val cameraFrame = FrameLayout(this).apply {
            addView(previewView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(regionOverlay, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        status = TextView(this).apply {
            text = "Aguardando câmera..."
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 4)
        }
        timerText = TextView(this).apply {
            text = "Ciclo M1: 60s"
            textSize = 18f
            setTextColor(green)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
        }
        captureButton = Button(this).apply {
            text = "CAPTURAR ÁREA DO GRÁFICO"
            setOnClickListener { captureChartRegion() }
        }
        val disclaimer = TextView(this).apply {
            text = "Esta versão recorta a região selecionada. Ainda não reconhece candles nem gera CALL/PUT. Nenhuma operação é executada."
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        root.addView(title, matchWrap())
        root.addView(subtitle, matchWrap())
        root.addView(instruction, matchWrap())
        root.addView(cameraFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(status, matchWrap())
        root.addView(timerText, matchWrap())
        root.addView(captureButton, matchWrap())
        root.addView(disclaimer, matchWrap())
        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(previewView.display.rotation)
                .build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
                status.text = "Câmera ativa. Ajuste o quadro verde sobre o gráfico."
            } catch (e: Exception) {
                status.text = "Falha ao iniciar câmera: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureChartRegion() {
        val capture = imageCapture ?: run {
            status.text = "Câmera ainda não está pronta."
            return
        }
        captureButton.isEnabled = false
        status.text = "Capturando imagem..."
        val photo = File(cacheDir, "full_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(photo).build()
        capture.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val source = BitmapFactory.decodeFile(photo.absolutePath)
                        ?: throw IllegalStateException("Não foi possível abrir a foto.")
                    val rect = regionOverlay.getNormalizedRect()
                    val crop = mapPreviewRectToBitmap(rect, source.width, source.height,
                        previewView.width, previewView.height)
                    val left = crop.left.toInt().coerceIn(0, source.width - 2)
                    val top = crop.top.toInt().coerceIn(0, source.height - 2)
                    val right = crop.right.toInt().coerceIn(left + 1, source.width)
                    val bottom = crop.bottom.toInt().coerceIn(top + 1, source.height)
                    val cropped = Bitmap.createBitmap(source, left, top, right-left, bottom-top)
                    val result = File(getExternalFilesDir(null),
                        "tradevision_chart_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(result).use {
                        cropped.compress(Bitmap.CompressFormat.JPEG, 94, it)
                    }
                    source.recycle()
                    cropped.recycle()
                    runOnUiThread {
                        status.text = "Recorte salvo (${right-left} × ${bottom-top}). Próxima etapa: leitura dos candles."
                        captureButton.isEnabled = true
                    }
                    photo.delete()
                } catch (e: Exception) {
                    runOnUiThread {
                        status.text = "Erro no recorte: ${e.localizedMessage}"
                        captureButton.isEnabled = true
                    }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    status.text = "Erro ao capturar: ${exception.message}"
                    captureButton.isEnabled = true
                }
            }
        })
    }

    // PreviewView uses FILL_CENTER: convert its visible crop back into source-image coordinates.
    private fun mapPreviewRectToBitmap(
        normalized: RectF, imageW: Int, imageH: Int, viewW: Int, viewH: Int
    ): RectF {
        val imageAspect = imageW.toFloat() / imageH
        val viewAspect = viewW.toFloat() / max(1, viewH)
        var visibleLeft = 0f
        var visibleTop = 0f
        var visibleW = 1f
        var visibleH = 1f
        if (imageAspect > viewAspect) {
            visibleW = viewAspect / imageAspect
            visibleLeft = (1f - visibleW) / 2f
        } else {
            visibleH = imageAspect / viewAspect
            visibleTop = (1f - visibleH) / 2f
        }
        val l = (visibleLeft + normalized.left * visibleW) * imageW
        val t = (visibleTop + normalized.top * visibleH) * imageH
        val r = (visibleLeft + normalized.right * visibleW) * imageW
        val b = (visibleTop + normalized.bottom * visibleH) * imageH
        return RectF(l, t, r, b)
    }

    private fun startMinuteTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(60_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                timerText.text = "Ciclo M1: ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                timerText.text = "Novo ciclo M1"
                startMinuteTimer()
            }
        }.start()
    }

    override fun onDestroy() {
        timer?.cancel()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        super.onDestroy()
    }
}

private class ChartRegionOverlay(context: android.content.Context) : View(context) {
    private val frame = RectF()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(54, 201, 143)
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        style = Paint.Style.FILL
    }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(54, 201, 143)
        style = Paint.Style.FILL
    }
    private var downX = 0f
    private var downY = 0f
    private var start = RectF()
    private var resizing = false
    private val minSize = 100f

    init { isClickable = true }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (frame.isEmpty) {
            frame.set(w * 0.08f, h * 0.22f, w * 0.92f, h * 0.72f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), frame.top, shade)
        canvas.drawRect(0f, frame.bottom, width.toFloat(), height.toFloat(), shade)
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, shade)
        canvas.drawRect(frame.right, frame.top, width.toFloat(), frame.bottom, shade)
        canvas.drawRect(frame, stroke)
        canvas.drawCircle(frame.right, frame.bottom, 15f, handle)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        canvas.drawText("ÁREA DO GRÁFICO", frame.left + 10f, frame.top + 32f, label)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                start = RectF(frame)
                resizing = (event.x > frame.right - 45f && event.y > frame.bottom - 45f)
                return frame.contains(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (resizing) {
                    frame.right = (start.right + dx).coerceIn(frame.left + minSize, width.toFloat())
                    frame.bottom = (start.bottom + dy).coerceIn(frame.top + minSize, height.toFloat())
                } else {
                    val w = start.width()
                    val h = start.height()
                    val left = (start.left + dx).coerceIn(0f, width - w)
                    val top = (start.top + dy).coerceIn(0f, height - h)
                    frame.set(left, top, left + w, top + h)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> return true
        }
        return true
    }

    fun getNormalizedRect(): RectF {
        val w = max(1, width).toFloat()
        val h = max(1, height).toFloat()
        return RectF(
            (frame.left / w).coerceIn(0f, 1f),
            (frame.top / h).coerceIn(0f, 1f),
            (frame.right / w).coerceIn(0f, 1f),
            (frame.bottom / h).coerceIn(0f, 1f)
        )
    }
}
