package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import com.google.android.material.R as MR
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * R: "glass" panel look shared by the tools card and the side strip: a dark translucent base, a light diagonal sheen
 * and a thin bright edge (real background blur is not possible for a plain view, this is the same look without it).
 */
fun rGlassBackground(density: Float, radiusDp: Float, edgeColor: Int): Drawable {
    val base = GradientDrawable().apply {
        setColor(ColorUtils.setAlphaComponent(Color.BLACK, 150))
        cornerRadius = radiusDp * density
    }
    val sheen = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            ColorUtils.setAlphaComponent(Color.WHITE, 70),
            ColorUtils.setAlphaComponent(Color.WHITE, 14),
            ColorUtils.setAlphaComponent(Color.WHITE, 36),
        ),
    ).apply {
        cornerRadius = radiusDp * density
        setStroke((1 * density).toInt().coerceAtLeast(1), ColorUtils.setAlphaComponent(edgeColor, 120))
    }
    return LayerDrawable(arrayOf(base, sheen))
}

/**
 * R: the app's OLD bottom toolbar (chapters list, web view, reading mode, rotation, crop borders, double page, reading
 * settings) as a vertical glass strip beside the side page bar. The old bar itself stays hidden; every button here
 * simply clicks the matching hidden original, so nothing about what the buttons do changes.
 */
@SuppressLint("ClickableViewAccessibility")
class RSideTools(
    context: Context,
    private val onInteract: () -> Unit,
) : LinearLayout(context) {

    private class Entry(val source: ImageButton?, val button: ImageButton)

    private val density = resources.displayMetrics.density
    private val accent = context.getResourceColor(MR.attr.colorSecondary)
    private val onSurface = context.getResourceColor(MR.attr.colorOnSurface)
    private val entries = ArrayList<Entry>()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutDirection = LAYOUT_DIRECTION_LTR
        setPadding((4 * density).toInt())
        background = rGlassBackground(density, 22f, accent)
        isClickable = true
        isFocusable = true
    }

    /** Adds a button. [source] = the hidden original whose icon and visibility it mirrors (null: fixed [icon]). */
    fun add(description: String, source: ImageButton?, icon: Int, action: () -> Unit) {
        val button = ImageButton(context).apply {
            contentDescription = description
            setColorFilter(onSurface)
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(Color.WHITE, 22))
                setStroke((1 * density).toInt().coerceAtLeast(1), ColorUtils.setAlphaComponent(accent, 140))
            }
            if (icon != 0) setImageResource(icon)
            setOnClickListener {
                onInteract()
                action()
            }
        }
        entries += Entry(source, button)
        // buttons keep a fixed round size; the space between them stretches so the strip is as long as the page bar
        if (childCount > 0) addView(View(context), LayoutParams(1, 0, 1f))
        addView(button, LayoutParams((40 * density).toInt(), (40 * density).toInt()))
    }

    /** Copies the icon and visibility of each hidden original (they change with the reading mode, rotation, ...). */
    fun refresh() {
        entries.forEach { e ->
            val src = e.source ?: return@forEach
            e.button.isVisible = src.visibility != View.GONE
            val d = src.drawable
            if (d != null) {
                e.button.setImageDrawable(d.constantState?.newDrawable(resources)?.mutate() ?: d)
                e.button.setColorFilter(onSurface)
            }
        }
        // the spacers sit between buttons; hide the ones next to a hidden button so the rest stays evenly spread
        var seenVisible = false
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v is ImageButton) {
                if (v.isVisible) seenVisible = true
            } else {
                val next = (i + 1 until childCount).map { getChildAt(it) }.firstOrNull { it is ImageButton }
                v.isVisible = seenVisible && next?.isVisible == true
            }
        }
    }
}
