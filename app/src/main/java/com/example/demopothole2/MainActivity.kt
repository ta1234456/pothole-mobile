package com.example.demopothole2

import android.graphics.Bitmap
import android.media.Image
import android.os.Bundle
import android.util.Size
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import java.nio.ByteOrder
import androidx.core.graphics.scale

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    private lateinit var interpreter: Interpreter
    private lateinit var overlayView: OverlayView

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val MODEL_NAME = "pothole0216.tflite"
        private const val INPUT_SIZE = 640
        private const val NUM_DETECTIONS = 5
        private const val LABEL = "Pothole"
        private const val THRESHOLD = 0.5f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        overlayView = findViewById(R.id.overlayView)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        interpreter = Interpreter(loadModelFile(MODEL_NAME))
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
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(INPUT_SIZE, INPUT_SIZE))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor(), { imageProxy ->
                        processImageProxy(imageProxy)
                    })
                }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxyToBitmap(imageProxy)
        val input = preprocess(bitmap)
        val output = Array(1) { Array(5) { FloatArray(8400) } }
        interpreter.run(input, output)

        val results = mutableListOf<OverlayView.Result>()
        val scaleX = bitmap.width
        val scaleY = bitmap.height

        // YOLOv8 TFLite output: [1, 5, 8400]
        // output[0][0] = x_center, output[0][1] = y_center, output[0][2] = width, output[0][3] = height, output[0][4] = confidence
        val confThreshold = THRESHOLD // Adjust as needed

        for (i in 0 until 8400) {
            val conf = output[0][4][i]
            if (conf > confThreshold) {
                val x = output[0][0][i]
                val y = output[0][1][i]
                val w = output[0][2][i]
                val h = output[0][3][i]

                // Convert normalized xywh to pixel box corners
                val left = (x - w / 2) * scaleX
                val top = (y - h / 2) * scaleY
                val right = (x + w / 2) * scaleX
                val bottom = (y + h / 2) * scaleY
                results.add(OverlayView.Result(left, top, right, bottom, conf, "Pothole"))
            }
        }

        runOnUiThread {
            overlayView.setResults(results)
        }
        imageProxy.close()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val image: Image = imageProxy.image ?: throw IllegalArgumentException("Image is null")
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            imageProxy.width, imageProxy.height, null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val yuv = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
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
                // Normalize to [0,1] if your model expects it (SSD Mobilenet usually does)
                inputBuffer.putFloat(((value shr 16 and 0xFF) / 255.0f))
                inputBuffer.putFloat(((value shr 8 and 0xFF) / 255.0f))
                inputBuffer.putFloat(((value and 0xFF) / 255.0f))
            }
        }
        inputBuffer.rewind()
        return inputBuffer
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}