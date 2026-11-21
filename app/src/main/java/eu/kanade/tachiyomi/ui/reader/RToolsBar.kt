package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.R as MR
import com.google.android.material.slider.Slider
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.getResourceColor
import kotlin.math.abs

/**
 * R: the reader tools bar. Four plain icons (auto-scroll speed, brightness, volume, battery); tapping one opens a small
 * panel under the bar with its slider/switch, tapping it again closes the panel. The bar sits under the toolbar and
 * shows/hides with the reader menu. A fling UP on the bar opens the chapters list, a fling LEFT or RIGHT opens the
 * reader settings.
 */
@SuppressLint("ClickableViewAccessibility")
class RToolsBar(
    context: Context,
    private val prefs: PreferencesHelper,
    private val batteryPercent: () -> Int,
    private val onFlingUp: () -> Unit,
    private val onFlingSide: () -> Unit,
    private val onInteract: () -> Unit,
    private val onLock: () -> Unit,
    private val onPrevChapter: () -> Unit = {},
    private val onNextChapter: () -> Unit = {},
    private val onOpenChapters: () -> Unit = {},
) : LinearLayout(context) {

    private enum class Tool { SPEED, BRIGHTNESS, VOLUME, BATTERY, LOCK }

    private val density = resources.displayMetrics.density
    private val accent = context.getResourceColor(MR.attr.colorSecondary)
    private val onSurface = context.getResourceColor(MR.attr.colorOnSurface)
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var open: Tool? = null

    private val buttons = LinkedHashMap<Tool, ImageButton>()
    private val panel = LinearLayout(context)
    private val panelLabel = TextView(context)
    private val slider = Slider(context)
    private val batterySwitch = MaterialSwitch(context)
    private val chapterTitle = TextView(context)
    private val pageCounter = TextView(context)
    private lateinit var prevButton: ImageButton
    private lateinit var nextButton: ImageButton

    /** The current chapter's name in the chapter row (tap it = the chapters list). */
    fun setChapterTitle(text: CharSequence) {
        chapterTitle.text = text
    }

    /** "5 / 14" in the page counter under the chapter row. */
    fun setPageText(text: CharSequence) {
        pageCounter.text = text
    }

    /** Dims the previous / next chapter button when there is no such chapter. */
    fun setNavEnabled(hasPrev: Boolean, hasNext: Boolean) {
        prevButton.alpha = if (hasPrev) 1f else 0.35f
        nextButton.alpha = if (hasNext) 1f else 0.35f
    }

    init {
        orientation = VERTICAL
        layoutDirection = LAYOUT_DIRECTION_LTR
        val side = (14 * density).toInt()
        setPadding(side, (6 * density).toInt(), side, (6 * density).toInt())
        // R: a floating rounded card (like the owner's reference): dark, theme-tinted outline
        background = GradientDrawable().apply {
            setColor(ColorUtils.setAlphaComponent(Color.BLACK, 190))
            cornerRadius = 28 * density
            setStroke((1 * density).toInt(), ColorUtils.setAlphaComponent(accent, 90))
        }

        // chapter row (like the owner's reference): previous chapter | current chapter (tap = list) | next chapter,
        // then the page counter
        val navRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        prevButton = navButton(R.drawable.ic_skip_previous_24, "الفصل السابق") { onPrevChapter() }
        nextButton = navButton(R.drawable.ic_skip_next_24, "الفصل التالي") { onNextChapter() }
        chapterTitle.apply {
            setTextColor(onSurface)
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            textDirection = TEXT_DIRECTION_FIRST_STRONG
            background = pill(ColorUtils.setAlphaComponent(Color.WHITE, 18), 18f)
            setPadding((12 * density).toInt(), (9 * density).toInt(), (8 * density).toInt(), (9 * density).toInt())
            setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down_24dp, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(onSurface)
            setOnClickListener {
                onInteract()
                onOpenChapters()
            }
        }
        navRow.addView(prevButton, LayoutParams((44 * density).toInt(), (44 * density).toInt()))
        navRow.addView(
            chapterTitle,
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (6 * density).toInt()
                marginEnd = (6 * density).toInt()
            },
        )
        navRow.addView(nextButton, LayoutParams((44 * density).toInt(), (44 * density).toInt()))
        addView(navRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        pageCounter.apply {
            setTextColor(onSurface)
            textSize = 13f
            gravity = Gravity.CENTER
            background = pill(ColorUtils.setAlphaComponent(accent, 50), 14f)
            setPadding((14 * density).toInt(), (3 * density).toInt(), (14 * density).toInt(), (3 * density).toInt())
        }
        addView(
            pageCounter,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (4 * density).toInt()
                bottomMargin = (4 * density).toInt()
            },
        )

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((2 * density).toInt())
        }
        addTool(row, Tool.SPEED, R.drawable.ic_r_speed, "سرعة التمرير التلقائي")
        addTool(row, Tool.BRIGHTNESS, R.drawable.ic_brightness_day_24dp, "السطوع")
        addTool(row, Tool.VOLUME, R.drawable.ic_r_volume, "الصوت")
        addTool(row, Tool.BATTERY, R.drawable.ic_r_battery, "البطارية")
        addTool(row, Tool.LOCK, R.drawable.ic_lock_24dp, "قفل الشاشة")
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        panel.apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pill(ColorUtils.setAlphaComponent(accent, 30), 20f)
            setPadding((14 * density).toInt(), (4 * density).toInt(), (14 * density).toInt(), (4 * density).toInt())
            isVisible = false
        }
        panelLabel.apply {
            setTextColor(onSurface)
            textSize = 14f
            minWidth = (84 * density).toInt()
            layoutDirection = LAYOUT_DIRECTION_RTL
            textAlignment = TEXT_ALIGNMENT_VIEW_START
        }
        slider.apply {
            trackActiveTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        batterySwitch.apply {
            text = "ساعة وبطارية في الركن"
            setTextColor(onSurface)
            isVisible = false
            layoutDirection = LAYOUT_DIRECTION_RTL
            setOnCheckedChangeListener { _, checked ->
                prefs.rClockBattery().set(checked)
                onInteract()
            }
        }
        panel.addView(panelLabel, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(slider, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(batterySwitch, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(
            panel,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * density).toInt()
            },
        )

        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            onInteract()
            when (open) {
                Tool.SPEED -> {
                    prefs.rAutoScrollSpeed().set(value.toInt())
                    panelLabel.text = "السرعة ${value.toInt()}"
                }
                Tool.BRIGHTNESS -> {
                    prefs.customBrightness().set(true)
                    prefs.customBrightnessValue().set(value.toInt())
                    panelLabel.text = "السطوع ${value.toInt()}"
                }
                Tool.VOLUME -> {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, value.toInt(), 0)
                    panelLabel.text = "الصوت ${value.toInt()}"
                }
                else -> Unit
            }
        }

        // a fling up opens the chapters list, a fling left/right opens the reader settings
        val detector = android.view.GestureDetector(
            context,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                    if (abs(vy) > abs(vx) && vy < -600f) {
                        onFlingUp()
                        return true
                    }
                    if (abs(vx) > abs(vy) * 1.2f && abs(vx) > 900f) {
                        onFlingSide()
                        return true
                    }
                    return false
                }
            },
        )
        setOnTouchListener { _, ev ->
            onInteract()
            detector.onTouchEvent(ev)
            false
        }
        row.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            false
        }
    }

    /** Round button with a thin outline; filled with the accent tint while its panel is open. */
    private fun roundButton(active: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (active) ColorUtils.setAlphaComponent(accent, 70) else ColorUtils.setAlphaComponent(Color.WHITE, 18))
        setStroke((1 * density).toInt(), ColorUtils.setAlphaComponent(if (active) accent else Color.WHITE, 110))
    }

    private fun pill(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
    }

    /** Small round button of the chapter row. */
    private fun navButton(icon: Int, description: String, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon)
        setColorFilter(onSurface)
        contentDescription = description
        background = roundButton(false)
        val pad = (10 * density).toInt()
        setPadding(pad, pad, pad, pad)
        setOnClickListener {
            onInteract()
            action()
        }
    }

    private fun addTool(row: LinearLayout, tool: Tool, icon: Int, description: String) {
        val button = ImageButton(context).apply {
            setImageResource(icon)
            setColorFilter(onSurface)
            contentDescription = description
            background = roundButton(false)
            val pad = (13 * density).toInt()
            setPadding(pad, pad, pad, pad)
            setOnClickListener { toggle(tool) }
        }
        buttons[tool] = button
        row.addView(
            button,
            LayoutParams(0, (50 * density).toInt(), 1f).apply {
                marginStart = (6 * density).toInt()
                marginEnd = (6 * density).toInt()
            },
        )
    }

    private fun toggle(tool: Tool) {
        onInteract()
        if (tool == Tool.LOCK) {
            close()
            onLock()
            return
        }
        if (open == tool) {
            close()
            return
        }
        open = tool
        buttons.forEach { (t, b) ->
            b.setColorFilter(if (t == tool) accent else onSurface)
            b.background = roundButton(t == tool)
        }
        panel.isVisible = true
        slider.isVisible = tool != Tool.BATTERY && tool != Tool.LOCK
        batterySwitch.isVisible = tool == Tool.BATTERY
        when (tool) {
            Tool.SPEED -> {
                slider.valueFrom = 20f
                slider.valueTo = 240f
                slider.stepSize = 10f
                val v = prefs.rAutoScrollSpeed().get().coerceIn(20, 240)
                slider.value = (v / 10 * 10).toFloat().coerceIn(20f, 240f)
                panelLabel.text = "السرعة ${slider.value.toInt()}"
            }
            Tool.BRIGHTNESS -> {
                slider.valueFrom = -75f
                slider.valueTo = 100f
                slider.stepSize = 1f
                slider.value = prefs.customBrightnessValue().get().coerceIn(-75, 100).toFloat()
                panelLabel.text = "السطوع ${slider.value.toInt()}"
            }
            Tool.VOLUME -> {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                slider.valueFrom = 0f
                slider.valueTo = max.toFloat()
                slider.stepSize = 1f
                slider.value = audio.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max).toFloat()
                panelLabel.text = "الصوت ${slider.value.toInt()}"
            }
            Tool.BATTERY -> {
                val pct = batteryPercent()
                panelLabel.text = if (pct >= 0) "البطارية $pct%" else "البطارية"
                batterySwitch.setOnCheckedChangeListener(null)
                batterySwitch.isChecked = prefs.rClockBattery().get()
                batterySwitch.setOnCheckedChangeListener { _, checked ->
                    prefs.rClockBattery().set(checked)
                    onInteract()
                }
            }
            Tool.LOCK -> Unit
        }
    }

    /** Closes the panel (used when the reader menu hides). */
    fun close() {
        open = null
        panel.isVisible = false
        buttons.values.forEach {
            it.setColorFilter(onSurface)
            it.background = roundButton(false)
        }
    }
}
