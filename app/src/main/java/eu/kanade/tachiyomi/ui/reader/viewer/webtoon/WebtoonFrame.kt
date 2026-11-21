package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * Frame layout which contains a [WebtoonRecyclerView]. It's needed to handle touch events,
 * because the recyclerview is scaled and its touch events are translated, which breaks the
 * detectors.
 *
 * TODO consider integrating this class into [WebtoonViewer].
 */
class WebtoonFrame(context: Context) : FrameLayout(context) {

    /**
     * Scale detector, either with pinch or quick scale.
     */
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    /**
     * Fling detector.
     */
    private val flingDetector = GestureDetector(context, FlingListener())

    var enableZoomOut = false
        set(value) {
            field = value
            recycler?.canZoomOut = value
        }

    var doubleTapZoom = true
        set(value) {
            field = value
            recycler?.doubleTapZoom = value
            scaleDetector.isQuickScaleEnabled = value
        }

    /**
     * Recycler view added in this frame.
     */
    private val recycler: WebtoonRecyclerView?
        get() = getChildAt(0) as? WebtoonRecyclerView

    /**
     * Dispatches a touch event to the detectors.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        flingDetector.onTouchEvent(ev)
        handleHold(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ---- R: hold-to-scroll (finger kept down in the bottom half) ----

    /** Returns +1 / -1 / 0 for a touch at the given fractions of the frame. */
    var holdDirection: ((Float, Float) -> Int)? = null
    var holdPxPerSecond: (() -> Float)? = null
    var holdScrollBy: ((Int) -> Unit)? = null

    private var holdDir = 0
    private var holding = false
    private var holdDownX = 0f
    private var holdDownY = 0f
    private var holdRemainder = 0f
    private var holdLastNanos = 0L
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val holdStart = Runnable {
        holding = true
        holdLastNanos = 0L
        holdRemainder = 0f
        Choreographer.getInstance().postFrameCallback(holdFrame)
    }

    private val holdFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!holding) return
            if (holdLastNanos != 0L) {
                val seconds = (frameTimeNanos - holdLastNanos) / 1_000_000_000f
                holdRemainder += holdDir * (holdPxPerSecond?.invoke() ?: 0f) * seconds
                val px = holdRemainder.toInt()
                if (px != 0) {
                    holdRemainder -= px
                    holdScrollBy?.invoke(px)
                }
            }
            holdLastNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun stopHold() {
        removeCallbacks(holdStart)
        holding = false
    }

    private fun handleHold(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopHold()
                holdDownX = ev.x
                holdDownY = ev.y
                val dir = if (width > 0 && height > 0) holdDirection?.invoke(ev.x / width, ev.y / height) ?: 0 else 0
                if (dir != 0) {
                    holdDir = dir
                    postDelayed(holdStart, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!holding && hypot(ev.x - holdDownX, ev.y - holdDownY) > touchSlop) removeCallbacks(holdStart)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopHold()
        }
    }

    override fun onDetachedFromWindow() {
        stopHold()
        super.onDetachedFromWindow()
    }

    /**
     * Scale listener used to delegate events to the recycler view.
     */
    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            recycler?.onScaleBegin()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            recycler?.onScale(detector.scaleFactor)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            recycler?.onScaleEnd()
        }
    }

    /**
     * Fling listener used to delegate events to the recycler view.
     */
    inner class FlingListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            return recycler?.zoomFling(velocityX.toInt(), velocityY.toInt()) ?: false
        }
    }
}
