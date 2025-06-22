package com.example.pothole

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var results: List<DetectionResult> = emptyList()
    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val backgroundPaint = Paint().apply {
        color = Color.RED
        alpha = 160
        style = Paint.Style.FILL
    }

    private val labels = listOf("Pothole") // Customize with your actual labels list if needed

    fun setResults(results: List<DetectionResult>) {
        this.results = results
        invalidate()  // Trigger redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (result in results) {
            val box = result.boundingBox

            // Draw bounding box
            canvas.drawRect(box, boxPaint)

            // Prepare label text with confidence percentage
            val label = if (result.classId < labels.size) {
                labels[result.classId]
            } else {
                "Unknown"
            }
            val conf = String.format("%.2f", result.confidence * 100)
            val text = "$label $conf%"

            // Draw label background
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.textSize

            val left = box.left
            val top = box.top - textHeight.coerceAtLeast(40f)

            val rectBackground = RectF(left, top, left + textWidth + 10, top + textHeight + 10)
            canvas.drawRect(rectBackground, backgroundPaint)

            // Draw label text
            canvas.drawText(text, left + 5, top + textHeight, textPaint)
        }
    }
}

data class DetectionResult(
    val boundingBox: RectF,
    val confidence: Float,
    val classId: Int
)


