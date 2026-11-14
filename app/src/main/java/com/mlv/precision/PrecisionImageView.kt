package com.mlv.precision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Manual tracing surface. Taps 1+2 define line A; taps 3+4 define line B.
 * Coordinates are always stored relative to the source bitmap so resizing
 * the view never changes the angle calculation.
 */
class PrecisionImageView(context: Context, private val onChanged: (Int, Double?) -> Unit) : View(context) {
    private var bitmap: Bitmap? = null
    private val points = mutableListOf<PointF>()
    private val drawingRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val aqua = Color.rgb(68, 231, 216)
    private val amber = Color.rgb(255, 190, 96)
    private val gray = Color.rgb(132, 163, 177)
    private val dp = resources.displayMetrics.density

    init {
        setBackgroundColor(Color.rgb(16, 35, 44))
        contentDescription = "Área de imagen. Selecciona cuatro puntos: dos para cada línea."
        isClickable = true
    }

    val pointCount: Int get() = points.size
    val hasImage: Boolean get() = bitmap != null

    fun setPhoto(photo: Bitmap) {
        bitmap?.let { if (it !== photo && !it.isRecycled) it.recycle() }
        bitmap = photo
        points.clear()
        invalidate()
        notifyChange()
    }

    fun undo() {
        if (points.isNotEmpty()) {
            points.removeAt(points.lastIndex)
            invalidate()
            notifyChange()
        }
    }

    fun clearPoints() {
        points.clear()
        invalidate()
        notifyChange()
    }

    fun angle(): Double? {
        if (points.size != 4) return null
        fun c(i: Int) = Coordinate(points[i].x.toDouble(), points[i].y.toDouble())
        return AngleMath.acuteAngleDegrees(c(0), c(1), c(2), c(3))
    }

    private fun notifyChange() = onChanged(points.size, angle())

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap
        if (bmp == null) {
            paint.color = gray
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 18 * dp
            canvas.drawText("ABRE UNA FOTOGRAFÍA", width / 2f, height / 2f - 5 * dp, paint)
            paint.textSize = 12 * dp
            canvas.drawText("y marca dos líneas con cuatro toques", width / 2f, height / 2f + 20 * dp, paint)
            return
        }
        val frame = imageRect(bmp)
        canvas.drawBitmap(bmp, null, frame, paint)
        val ratio = frame.width() / bmp.width.toFloat()
        drawMarks(canvas, frame.left, frame.top, ratio, 3.5f * dp / ratio, 7f * dp / ratio)
    }

    private fun imageRect(bmp: Bitmap): RectF {
        val xPad = 8 * dp
        val yPad = 8 * dp
        val availableW = max(1f, width - 2f * xPad)
        val availableH = max(1f, height - 2f * yPad)
        val scale = min(availableW / bmp.width, availableH / bmp.height)
        val imageWidth = bmp.width * scale
        val imageHeight = bmp.height * scale
        drawingRect.set((width - imageWidth) / 2f, (height - imageHeight) / 2f,
            (width + imageWidth) / 2f, (height + imageHeight) / 2f)
        return drawingRect
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null || points.size >= 4) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                val bmp = bitmap ?: return true
                val rect = imageRect(bmp)
                if (rect.contains(event.x, event.y)) {
                    val x = ((event.x - rect.left) / rect.width() * bmp.width)
                        .coerceIn(0f, bmp.width.toFloat())
                    val y = ((event.y - rect.top) / rect.height() * bmp.height)
                        .coerceIn(0f, bmp.height.toFloat())
                    points.add(PointF(x, y))
                    invalidate()
                    notifyChange()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawMarks(canvas: Canvas, offsetX: Float, offsetY: Float,
                          scale: Float, lineWidth: Float, radius: Float) {
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = lineWidth
        paint.strokeCap = Paint.Cap.ROUND
        if (points.size >= 2) {
            paint.color = aqua
            canvas.drawLine(points[0].x, points[0].y, points[1].x, points[1].y, paint)
        }
        if (points.size >= 4) {
            paint.color = amber
            canvas.drawLine(points[2].x, points[2].y, points[3].x, points[3].y, paint)
        }
        points.forEachIndexed { idx, point ->
            paint.style = Paint.Style.FILL
            paint.color = if (idx < 2) aqua else amber
            canvas.drawCircle(point.x, point.y, radius, paint)
            paint.color = Color.rgb(8, 25, 34)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = radius * 1.6f
            paint.isFakeBoldText = true
            canvas.drawText((idx + 1).toString(), point.x,
                point.y + radius * 0.53f, paint)
        }
        paint.isFakeBoldText = false
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    /** Result is an independent photo with drawn overlays, not just a screenshot. */
    fun annotatedBitmap(): Bitmap? {
        val bmp = bitmap ?: return null
        val angle = angle() ?: return null
        val output = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(output)
        c.drawBitmap(bmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val dim = min(bmp.width, bmp.height).toFloat()
        drawMarks(c, 0f, 0f, 1f, max(3f, dim * 0.005f), max(6f, dim * 0.011f))

        val info = "MLV PRECISION | Angulo aparente: %.2f deg | Diferencia 90: %.2f deg"
            .format(java.util.Locale.US, angle, AngleMath.deviationFromSquareDegrees(angle))
        val label = "MEDICION SOBRE FOTO | NO CORRIGE PERSPECTIVA"
        val barHeight = max(52f, dim * 0.105f)
        paint.color = Color.argb(225, 8, 26, 34)
        c.drawRect(0f, bmp.height - barHeight, bmp.width.toFloat(), bmp.height.toFloat(), paint)
        paint.color = Color.WHITE
        paint.textSize = max(11f, min(bmp.width / 40f, barHeight * 0.27f))
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.LEFT
        c.drawText(info, 8f, bmp.height - barHeight * 0.52f, paint)
        paint.color = aqua
        paint.textSize *= 0.8f
        c.drawText(label, 8f, bmp.height - barHeight * 0.14f, paint)
        paint.typeface = null
        return output
    }
}
