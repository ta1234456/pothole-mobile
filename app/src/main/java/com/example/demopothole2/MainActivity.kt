package com.example.demopothole2

import android.Manifest
import android.graphics.Bitmap
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    private lateinit var interpreter: Interpreter
    private lateinit var overlayView: OverlayView

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val MODEL_NAME = "15k/best_float32.tflite"
        private const val INPUT_SIZE = 640
        private const val NUM_BOXES = 8400
        private const val LABEL = "Pothole"
        private const val THRESHOLD = 0.2f
        private const val NMS_IOU_THRESHOLD = 0.5f
    }

    private var lastDetectionState: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        overlayView = findViewById(R.id.overlayView)

        if (allPermissionsGranted()) {
            interpreter = Interpreter(loadModelFile(MODEL_NAME))
            logModelShape()
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun logModelShape() {
        val inputShape = interpreter.getInputTensor(0).shape()
        val inputType = interpreter.getInputTensor(0).dataType()
        Log.d("MODEL_CHECK", "Input shape: ${inputShape.contentToString()} Type: $inputType")
        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputType = interpreter.getOutputTensor(0).dataType()
        Log.d("MODEL_CHECK", "Output shape: ${outputShape.contentToString()} Type: $outputType")
    }

    private fun loadModelFile(filename: String): ByteBuffer {
        val fileDescriptor = assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = findViewById<PreviewView>(R.id.viewFinder).surfaceProvider
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(INPUT_SIZE, INPUT_SIZE))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap() // CameraX 1.3+; else use safe YUV to RGB conversion
            val input = preprocess(bitmap)
            // Model output shape: [1, 5, 8400]
            val output = Array(1) { Array(5) { FloatArray(NUM_BOXES) } }
            interpreter.run(input, output)

            val results = mutableListOf<OverlayView.Result>()
            val scaleX = bitmap.width
            val scaleY = bitmap.height

            for (i in 0 until NUM_BOXES) {
                val conf = output[0][4][i]
                if (conf > THRESHOLD) {
                    val x = output[0][0][i]
                    val y = output[0][1][i]
                    val w = output[0][2][i]
                    val h = output[0][3][i]
                    val left = (x - w / 2) * scaleX
                    val top = (y - h / 2) * scaleY
                    val right = (x + w / 2) * scaleX
                    val bottom = (y + h / 2) * scaleY
                    results.add(OverlayView.Result(left, top, right, bottom, conf, LABEL))
                }
            }

            // Apply NMS to filter overlapping boxes
            val nmsResults = nms(results, NMS_IOU_THRESHOLD)

            val found = nmsResults.isNotEmpty()

            runOnUiThread {
                overlayView.setResults(nmsResults)
                // Only show toast if detection state changes
                if (lastDetectionState == null || lastDetectionState != found) {
                    lastDetectionState = found
                    if (found) {
                        Toast.makeText(this, "พบหลุมบนถนน", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "ไม่พบหลุมบนถนน", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PROCESS_IMAGE", "Error processing image: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun nms(
        boxes: List<OverlayView.Result>,
        iouThreshold: Float = 0.5f
    ): List<OverlayView.Result> {
        val picked = mutableListOf<OverlayView.Result>()
        val sorted = boxes.sortedByDescending { it.confidence }.toMutableList()
        while (sorted.isNotEmpty()) {
            val candidate = sorted.removeAt(0)
            picked.add(candidate)
            val it = sorted.iterator()
            while (it.hasNext()) {
                val box = it.next()
                if (iou(candidate, box) > iouThreshold) {
                    it.remove()
                }
            }
        }
        return picked
    }

    private fun iou(a: OverlayView.Result, b: OverlayView.Result): Float {
        val xA = max(a.left, b.left)
        val yA = max(a.top, b.top)
        val xB = min(a.right, b.right)
        val yB = min(a.bottom, b.bottom)
        val interArea = max(0f, xB - xA) * max(0f, yB - yA)
        val boxAArea = (a.right - a.left) * (a.bottom - a.top)
        val boxBArea = (b.right - b.left) * (b.bottom - b.top)
        return if (boxAArea + boxBArea - interArea == 0f) 0f
        else interArea / (boxAArea + boxBArea - interArea)
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val scaledBitmap = bitmap.scale(INPUT_SIZE, INPUT_SIZE)
        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaledBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val value = intValues[pixel++]
                inputBuffer.putFloat(((value shr 16 and 0xFF) / 255.0f))
                inputBuffer.putFloat(((value shr 8 and 0xFF) / 255.0f))
                inputBuffer.putFloat(((value and 0xFF) / 255.0f))
            }
        }
        inputBuffer.rewind()
        return inputBuffer
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                interpreter = Interpreter(loadModelFile(MODEL_NAME))
                logModelShape()
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}