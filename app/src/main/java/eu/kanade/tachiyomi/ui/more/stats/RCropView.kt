package eu.kanade.tachiyomi.ui.more.stats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max

/**
 * R: a small crop / rotate editor for the profile pictures. The picture moves and zooms (drag + pinch) under a fixed
 * frame with the wanted shape ([aspect] = width / height, [round] = circle frame); [rotate] turns it 90 degrees;
 * [render] returns what is inside the frame.
 */
class RCropView(context: Context, private var bitmap: Bitmap, private val aspect: Float, private val round: Boolean) : View(context) {
    private val matrix = Matrix()
    private val frame = RectF()
    private val shade = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var minScale = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private val scaler = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                zoomBy(d.scaleFactor, d.focusX, d.focusY)
                return true
            }
        },
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = 16f * resources.displayMetrics.density
        var fw = w - 2 * pad
        var fh = fw / aspect
        if (fh > h - 2 * pad) {
            fh = h - 2 * pad
            fw = fh * aspect
        }
        frame.set((w - fw) / 2f, (h - fh) / 2f, (w + fw) / 2f, (h + fh) / 2f)
        fit()
    }

    /** Scale so the picture covers the frame, centered. */
    private fun fit() {
        if (frame.isEmpty) return
        minScale = max(frame.width() / bitmap.width, frame.height() / bitmap.height)
        matrix.reset()
        matrix.postScale(minScale, minScale)
        matrix.postTranslate(
            frame.centerX() - bitmap.width * minScale / 2f,
            frame.centerY() - bitmap.height * minScale / 2f,
        )
        invalidate()
    }

    fun rotate() {
        val m = Matrix().apply { postRotate(90f) }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        fit()
    }

    private fun values() = FloatArray(9).also { matrix.getValues(it) }

    private fun zoomBy(factor: Float, px: Float, py: Float) {
        val cur = values()[Matrix.MSCALE_X]
        val target = (cur * factor).coerceIn(minScale, minScale * 6f)
        val f = target / cur
        matrix.postScale(f, f, px, py)
        clamp()
        invalidate()
    }

    /** Keeps the frame fully covered by the picture. */
    private fun clamp() {
        val v = values()
        val s = v[Matrix.MSCALE_X]
        val left = v[Matrix.MTRANS_X]
        val top = v[Matrix.MTRANS_Y]
        val right = left + bitmap.width * s
        val bottom = top + bitmap.height * s
        var dx = 0f
        var dy = 0f
        if (left > frame.left) dx = frame.left - left
        if (right < frame.right) dx = frame.right - right
        if (top > frame.top) dy = frame.top - top
        if (bottom < frame.bottom) dy = frame.bottom - bottom
        matrix.postTranslate(dx, dy)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = e.x
                lastY = e.y
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> if (dragging && !scaler.isInProgress && e.pointerCount == 1) {
                matrix.postTranslate(e.x - lastX, e.y - lastY)
                clamp()
                lastX = e.x
                lastY = e.y
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val i = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(i)
                lastY = e.getY(i)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        c.drawBitmap(bitmap, matrix, bmpPaint)
        val outside = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            if (round) addOval(frame, Path.Direction.CW) else addRect(frame, Path.Direction.CW)
        }
        c.drawPath(outside, shade)
        if (round) c.drawOval(frame, line) else c.drawRect(frame, line)
    }

    /** The part of the picture inside the frame, [outWidth] pixels wide. */
    fun render(outWidth: Int): Bitmap {
        val outH = (outWidth / aspect).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(outWidth, outH, Bitmap.Config.ARGB_8888)
        val m = Matrix(matrix)
        m.postTranslate(-frame.left, -frame.top)
        val k = outWidth / frame.width()
        m.postScale(k, k)
        Canvas(out).apply {
            drawColor(Color.BLACK)
            drawBitmap(bitmap, m, bmpPaint)
        }
        return out
    }
}
