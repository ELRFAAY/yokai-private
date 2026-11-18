package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.util.AttributeSet
import android.view.View.MeasureSpec.AT_MOST
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.makeMeasureSpec
import android.widget.FrameLayout
import kotlin.math.roundToInt

/**
 * Holds ONE child (the page slider) and stands it up: the child is laid out horizontally with this
 * view's height as its length, then rotated 270 degrees, so its value grows from the bottom to the top.
 * Touches still work because Android maps them through the rotation for us.
 */
class ReaderVerticalSliderLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val thickness = (40 * density).roundToInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val child = getChildAt(0)
        val length = when (MeasureSpec.getMode(heightMeasureSpec)) {
            EXACTLY, AT_MOST -> MeasureSpec.getSize(heightMeasureSpec)
            else -> (200 * density).roundToInt()
        }
        child?.measure(makeMeasureSpec(length, EXACTLY), makeMeasureSpec(thickness, EXACTLY))
        setMeasuredDimension(resolveSize(thickness, widthMeasureSpec), length)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        val cw = child.measuredWidth
        val ch = child.measuredHeight
        val cx = (right - left) / 2
        val cy = (bottom - top) / 2
        child.layout(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2)
        child.pivotX = cw / 2f
        child.pivotY = ch / 2f
        child.rotation = 270f
    }

    override fun shouldDelayChildPressedState(): Boolean = false

}
