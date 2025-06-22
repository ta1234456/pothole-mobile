package com.example.pothole

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var interpreter: Interpreter
    private lateinit var labels: List<String>
    private val imageSize = 416  // ตาม model YOLO ที่แปลงมา

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        labels = loadLabels()
        interpreter = Interpreter(loadModelFile("pothole.tflite"), Interpreter.Options())

        startCamera()
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun loadLabels(): List<String> {
        return assets.open("labels.txt").bufferedReader().useLines { it.toList() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(imageSize, imageSize))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), { imageProxy ->
                val bitmap = imageProxyToBitmap(imageProxy)
                detectObjects(bitmap)
                imageProxy.close()
            })

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun detectObjects(bitmap: Bitmap) {
        val resized = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
        val input = ByteBuffer.allocateDirect(1 * imageSize * imageSize * 3 * 4).order(ByteOrder.nativeOrder())

        for (y in 0 until imageSize) {
            for (x in 0 until imageSize) {
                val px = resized.getPixel(x, y)
                input.putFloat((Color.red(px) / 255.0f))
                input.putFloat((Color.green(px) / 255.0f))
                input.putFloat((Color.blue(px) / 255.0f))
            }
        }

        val output = Array(1) { Array(2535) { FloatArray(85) } }  // เปลี่ยนตาม model

        interpreter.run(input, output)

        // TODO: decode YOLO output to bounding boxes
        // คุณสามารถใช้ Non-Max Suppression หรือ library เสริม
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
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

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
