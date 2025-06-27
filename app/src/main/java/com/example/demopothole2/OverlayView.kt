package com.example.demopothole2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

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
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        style = Paint.Style.FILL
    }

    fun setResults(newResults: List<Result>) {
        results = newResults
        invalidate()  // This will trigger onDraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (result in results) {
            canvas.drawRect(result.left, result.top, result.right, result.bottom, boxPaint)
            canvas.drawText(
                "${result.label} ${"%.2f".format(result.confidence)}",
                result.left,
                result.top - 10,
                textPaint
            )
        }
    }
}