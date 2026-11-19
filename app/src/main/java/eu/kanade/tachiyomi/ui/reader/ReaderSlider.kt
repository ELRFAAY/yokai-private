package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import com.google.android.material.slider.Slider

/**
 * Seekbar to show current chapter progress.
 */
class ReaderSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Slider(context, attrs) {

    /**
     * True when this slider stands upright in the vertical bar (R switch r_vertical_nav): it always
     * fills from the bottom to the top and is never mirrored for right-to-left readers.
     */
    var vertical: Boolean = false

    private var rtl = false

    /**
     * Whether the viewer reads right to left (the horizontal bar then draws from right to left).
     */
    var isRTL: Boolean
        set(value) {
            rtl = value
            if (!vertical) layoutDirection = if (value) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
        }
        get() = rtl
    private val boundingBox: Rect = Rect()
    private val exclusions = listOf(boundingBox)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (Build.VERSION.SDK_INT >= 29 && changed) {
            if (vertical) boundingBox.set(0, 0, right - left, bottom - top) else boundingBox.set(left, top, right, bottom)
            systemGestureExclusionRects = exclusions
        }
    }
}
