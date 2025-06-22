package com.example.pothole

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.Manifest
import android.graphics.RectF
import android.util.Log
import android.util.Size
import androidx.core.graphics.scale
import androidx.core.graphics.get
import org.tensorflow.lite.support.common.FileUtil

//class MainActivity : AppCompatActivity() {
//
//    private lateinit var interpreter: Interpreter
//    private lateinit var labels: List<String>
//    private val imageSize = 416  // ตาม model YOLO ที่แปลงมา
//    private lateinit var overlay: OverlayView
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//        labels = loadLabels()
//        interpreter = Interpreter(loadModelFile("tflite/best_float16.tflite"), Interpreter.Options())
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//            != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
//        } else {
//            startCamera()
//        }
//    }
//
//    private fun loadModelFile(modelName: String): MappedByteBuffer {
//        val fileDescriptor = assets.openFd(modelName)
//        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
//        val fileChannel = inputStream.channel
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
//    }
//
//    private fun loadLabels(): List<String> {
//        return assets.open("labels.txt").bufferedReader().useLines { it.toList() }
//    }
//
//    private fun startCamera() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//
//        cameraProviderFuture.addListener({
//            val cameraProvider = cameraProviderFuture.get()
//            val preview = Preview.Builder().build().also {
//                it.surfaceProvider = findViewById<PreviewView>(R.id.viewFinder).surfaceProvider
//            }
//
//            val imageAnalysis = ImageAnalysis.Builder()
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build()
//
//            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), { imageProxy ->
//                val bitmap = imageProxyToBitmap(imageProxy)
//                detectObjects(bitmap)
//                imageProxy.close()
//            })
//
//            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//
//            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
//
//        }, ContextCompat.getMainExecutor(this))
//    }
//    fun decodeYoloOutput(
//        output: Array<Array<FloatArray>>,
//        confThreshold: Float = 0.5f,
//        imageWidth: Int,
//        imageHeight: Int
//    ): List<DetectionResult> {
//        val results = mutableListOf<DetectionResult>()
//        val predictions = output[0]
//
//        for (i in predictions.indices) {
//            val row = predictions[i]
//            val x = row[0]
//            val y = row[1]
//            val w = row[2]
//            val h = row[3]
//            val confidence = row[4]
//            val classId = row[5].toInt()
//
//            if (confidence >= confThreshold) {
//                val left = (x - w / 2f) * imageWidth
//                val top = (y - h / 2f) * imageHeight
//                val right = (x + w / 2f) * imageWidth
//                val bottom = (y + h / 2f) * imageHeight
//
//                results.add(DetectionResult(RectF(left, top, right, bottom), confidence, classId))
//            }
//        }
//
//        return results
//    }
//    private fun detectObjects(bitmap: Bitmap) {
//        val resized = bitmap.scale(672, 672)
//        val input = ByteBuffer.allocateDirect(1 * 672 * 672 * 3 * 4).order(ByteOrder.nativeOrder())
//
//        for (y in 0 until imageSize) {
//            for (x in 0 until imageSize) {
//                val px = resized[x, y]
//                input.putFloat((Color.red(px) / 255.0f))
//                input.putFloat((Color.green(px) / 255.0f))
//                input.putFloat((Color.blue(px) / 255.0f))
//            }
//        }
//
//        val output = Array(1) { Array(5) { FloatArray(9261) } }  // เปลี่ยนตาม model
//        val outputShape = interpreter.getOutputTensor(0).shape()
//        Log.d("TFLite", "Output shape: ${outputShape.joinToString()}")
//        interpreter.run(input, output)
//
//        // TODO: decode YOLO output to bounding boxes
//        // คุณสามารถใช้ Non-Max Suppression หรือ library เสริม
//
//
//    }
//
//    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
//        val yBuffer = image.planes[0].buffer
//        val uBuffer = image.planes[1].buffer
//        val vBuffer = image.planes[2].buffer
//
//        val ySize = yBuffer.remaining()
//        val uSize = uBuffer.remaining()
//        val vSize = vBuffer.remaining()
//
//        val nv21 = ByteArray(ySize + uSize + vSize)
//        yBuffer.get(nv21, 0, ySize)
//        vBuffer.get(nv21, ySize, vSize)
//        uBuffer.get(nv21, ySize + vSize, uSize)
//
//        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
//        val out = ByteArrayOutputStream()
//        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
//        val imageBytes = out.toByteArray()
//        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
//    }
//}
@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    private lateinit var tflite: Interpreter
    private lateinit var overlay: OverlayView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlay = findViewById(R.id.overlay)

        // โหลดโมเดล
        val model = FileUtil.loadMappedFile(this, "tflite/best_float16.tflite")
        val options = Interpreter.Options()
        tflite = Interpreter(model, options)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        } else {
            startCamera()
        }
    }

    fun decodeYoloOutput(
        output: Array<Array<FloatArray>>,
        confThreshold: Float = 0.5f,
        imageWidth: Int,
        imageHeight: Int
    ): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        val predictions = output[0]

        for (i in predictions.indices) {
            val row = predictions[i]
            val x = row[0]
            val y = row[1]
            val w = row[2]
            val h = row[3]
            val confidence = row[4]
            val classId = row[5].toInt()

            if (confidence >= confThreshold) {
                val left = (x - w / 2f) * imageWidth
                val top = (y - h / 2f) * imageHeight
                val right = (x + w / 2f) * imageWidth
                val bottom = (y + h / 2f) * imageHeight

                results.add(DetectionResult(RectF(left, top, right, bottom), confidence, classId))
            }
        }

        return results
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = findViewById<PreviewView>(R.id.viewFinder).surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(672, 672))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                val bitmap = imageProxy.toBitmap()
                val scaledBitmap = bitmap.scale(672, 672)

                val inputBuffer = convertBitmapToByteBuffer(scaledBitmap)

                val outputBuffer = Array(1) { Array(5) { FloatArray(9261) } }

                tflite.run(inputBuffer, outputBuffer)

                val boxes = decodeYoloOutput(
                    outputBuffer,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height
                )
                val mappedBoxes = boxes.map { detection ->
                    val scaleX = overlay.width.toFloat() / scaledBitmap.width
                    val scaleY = overlay.height.toFloat() / scaledBitmap.height

                    val rect = detection.boundingBox
                    val mappedRect = RectF(
                        rect.left * scaleX,
                        rect.top * scaleY,
                        rect.right * scaleX,
                        rect.bottom * scaleY
                    )
                    detection.copy(boundingBox = mappedRect)
                }
                overlay.setResults(mappedBoxes)
                imageProxy.close()
            }

            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }
    fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = 672
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = bitmap[x, y]

                byteBuffer.putFloat((Color.red(pixel) / 255.0f))
                byteBuffer.putFloat((Color.green(pixel) / 255.0f))
                byteBuffer.putFloat((Color.blue(pixel) / 255.0f))
            }
        }
        return byteBuffer
    }
}

