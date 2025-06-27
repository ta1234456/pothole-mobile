package com.example.demopothole2

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    data class Result(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val score: Float, val label: String
    )
    private var results: List<Result> = emptyList()
    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }

    fun setResults(results: List<Result>) {
        this.results = results
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (r in results) {
            val rect = RectF(r.left, r.top, r.right, r.bottom)
            canvas.drawRect(rect, boxPaint)
            canvas.drawText("${r.label} %.2f".format(r.score), r.left, r.top - 10, textPaint)
        }
    }
}