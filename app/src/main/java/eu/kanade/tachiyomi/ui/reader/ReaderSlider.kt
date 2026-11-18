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
     * Whether the viewer reads right to left. The bar is vertical now (always fills bottom to top),
     * so this is only used for page labels and never mirrors the slider itself.
     */
    var isRTL: Boolean = false
    private val boundingBox: Rect = Rect()
    private val exclusions = listOf(boundingBox)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (Build.VERSION.SDK_INT >= 29 && changed) {
            boundingBox.set(0, 0, right - left, bottom - top)
            systemGestureExclusionRects = exclusions
        }
    }
}
