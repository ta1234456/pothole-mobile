package com.example.demopothole2

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Result(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val confidence: Float,
        val label: String
    )

    private var results: List<Result> = emptyList()
    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 42f
        style = Paint.Style.FILL
        isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }
    private val textBgPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        alpha = 180
    }

    fun setResults(results: List<Result>) {
        this.results = results
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (result in results) {
            // Draw bounding box
            canvas.drawRect(result.left, result.top, result.right, result.bottom, boxPaint)

            // Draw label and confidence
            val label = "${result.label} %.2f".format(result.confidence)
            val textBounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            val x = result.left
            val y = result.top - 8f

            // Draw background rectangle for text
            canvas.drawRect(
                x,
                y - textBounds.height(),
                x + textBounds.width() + 16f,
                y + 8f,
                textBgPaint
            )
            // Draw text
            canvas.drawText(label, x + 8f, y, textPaint)
        }
    }
}