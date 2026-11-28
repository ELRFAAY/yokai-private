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

    /** Second color (warm amber) next to the theme color, so the card is not one flat color. */
    private val accent2 = Color.parseColor("#F5B942")
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var open: Tool? = null

    private val buttons = LinkedHashMap<Tool, ImageButton>()
    private val panel = LinearLayout(context)
    private val panelLabel = TextView(context)
    private val slider = Slider(context)
    private val batterySwitch = MaterialSwitch(context)
    private val brightSwitch = MaterialSwitch(context)
    private val chapterTitle = TextView(context)
    private val pageCounter = TextView(context)
    private lateinit var prevButton: ImageButton
    private lateinit var nextButton: ImageButton

    // R: the tools bar comes in 4 shapes (Settings > R): 0 full card, 1 thin bar, 2 floating capsule, 3 side strip
    private var style = 0
    private var capsuleOpen = false
    private var moreShown = false
    private lateinit var navRowRef: LinearLayout
    private lateinit var toolRowRef: LinearLayout
    private lateinit var handleRef: View
    private val moreButton = TextView(context)

    /** True when the bar stretches over the screen width (card, thin bar, opened capsule); false for the capsule and the side strip. */
    val isWide: Boolean get() = style == 0 || style == 1 || (style == 2 && capsuleOpen)
    val isSide: Boolean get() = style == 3

    fun applyStyle(s: Int) {
        style = s.coerceIn(0, 3)
        capsuleOpen = false
        moreShown = false
        relayout()
    }

    private fun relayout() {
        val capsule = style == 2 && !capsuleOpen
        val side = style == 3
        val thin = style == 1
        val full = style == 0 || (style == 2 && capsuleOpen)
        // the page counter lives under the chapter row, except in the capsule (between the two arrows)
        (pageCounter.parent as? ViewGroup)?.removeView(pageCounter)
        val ph = ((if (side) 6 else 14) * density).toInt()
        pageCounter.setPadding(ph, (3 * density).toInt(), ph, (3 * density).toInt())
        if (capsule) {
            navRowRef.addView(
                pageCounter,
                1,
                LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = (6 * density).toInt()
                    marginEnd = (6 * density).toInt()
                },
            )
        } else {
            addView(
                pageCounter,
                indexOfChild(navRowRef) + 1,
                LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = (2 * density).toInt()
                    bottomMargin = (2 * density).toInt()
                },
            )
        }
        chapterTitle.isVisible = !(capsule || side)
        navRowRef.orientation = if (side) VERTICAL else HORIZONTAL
        navRowRef.gravity = if (side) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        toolRowRef.orientation = if (side) VERTICAL else HORIZONTAL
        // the stretchy gaps between the tool buttons: a fixed small gap in the vertical strip
        for (i in 0 until toolRowRef.childCount) {
            val c = toolRowRef.getChildAt(i)
            if (c is ImageButton) continue
            c.layoutParams = if (side) LayoutParams(1, (6 * density).toInt(), 0f) else LayoutParams(1, 1, 1f)
        }
        toolRowRef.isVisible = full || side || (thin && moreShown)
        handleRef.isVisible = full
        moreButton.isVisible = thin
        moreButton.text = if (moreShown) "أقل ⌃" else "المزيد ⌄"
        panel.minimumWidth = if (side) (240 * density).toInt() else 0
        if (!toolRowRef.isVisible) close()
        requestLayout()
    }

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
        setPadding(side, (4 * density).toInt(), side, (4 * density).toInt())
        // R: a floating rounded card (like the owner's reference): dark, theme-tinted outline
        background = rGlassBackground(density, 28f, accent)

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
            textSize = 13f
            maxLines = 2 // the full chapter name on two lines instead of "…" after one
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            textDirection = TEXT_DIRECTION_FIRST_STRONG
            background = pill(ColorUtils.setAlphaComponent(Color.WHITE, 18), 18f)
            setPadding((12 * density).toInt(), (7 * density).toInt(), (8 * density).toInt(), (7 * density).toInt())
            setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down_24dp, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(onSurface)
            setOnClickListener {
                onInteract()
                onOpenChapters()
            }
        }
        navRow.addView(prevButton, LayoutParams((40 * density).toInt(), (40 * density).toInt()))
        navRow.addView(
            chapterTitle,
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (6 * density).toInt()
                marginEnd = (6 * density).toInt()
            },
        )
        navRow.addView(nextButton, LayoutParams((40 * density).toInt(), (40 * density).toInt()))
        addView(navRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        pageCounter.apply {
            setTextColor(onSurface)
            textSize = 13f
            gravity = Gravity.CENTER
            textDirection = TEXT_DIRECTION_LTR // the screen is Arabic (RTL): without this "1 / 13" showed as "13 / 1"
            setTextColor(accent2)
            background = pill(ColorUtils.setAlphaComponent(accent2, 40), 14f)
            setPadding((14 * density).toInt(), (3 * density).toInt(), (14 * density).toInt(), (3 * density).toInt())
        }
        addView(
            pageCounter,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (2 * density).toInt()
                bottomMargin = (2 * density).toInt()
            },
        )

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((2 * density).toInt())
        }
        addTool(row, Tool.SPEED, R.drawable.ic_rt_speed, "سرعة التمرير التلقائي")
        addTool(row, Tool.BRIGHTNESS, R.drawable.ic_rt_brightness, "السطوع")
        addTool(row, Tool.VOLUME, R.drawable.ic_rt_volume, "الصوت")
        addTool(row, Tool.BATTERY, R.drawable.ic_rt_battery, "البطارية")
        addTool(row, Tool.LOCK, R.drawable.ic_rt_lock, "قفل الشاشة")
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
        // brightness on/off: off = the whole feature is disabled (overlay gone, phone brightness left alone)
        brightSwitch.apply {
            isVisible = false
            layoutDirection = LAYOUT_DIRECTION_RTL
        }
        panel.addView(brightSwitch, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(
            panel,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * density).toInt()
            },
        )

        // handle line under the card: drag it up (or tap it) to open the chapters list; its chips switch to the
        // reading settings
        val handleZone = android.widget.FrameLayout(context)
        // a fixed, visible "chapters" button (tap or drag up = the old chapters sheet)
        val handle = TextView(context).apply {
            text = "⌃  الشباتر"
            textSize = 13f
            setTextColor(onSurface)
            gravity = Gravity.CENTER
            background = pill(ColorUtils.setAlphaComponent(Color.WHITE, 30), 16f)
            setPadding((18 * density).toInt(), (5 * density).toInt(), (18 * density).toInt(), (5 * density).toInt())
        }
        handleZone.addView(
            handle,
            android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        var downY = 0f
        var fired = false
        handleZone.setOnTouchListener { v, ev ->
            onInteract()
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = ev.rawY
                    fired = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!fired && downY - ev.rawY > 24 * density) {
                        fired = true
                        onFlingUp()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!fired) {
                        v.performClick()
                        onFlingUp()
                    }
                    true
                }
                else -> true
            }
        }
        addView(
            handleZone,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (34 * density).toInt()).apply { // tall enough: the chip text was cut at the bottom
                topMargin = (2 * density).toInt()
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
        navRowRef = navRow
        toolRowRef = row
        handleRef = handleZone
        // thin bar: "more" shows / hides the tools row
        moreButton.apply {
            textSize = 12f
            setTextColor(onSurface)
            gravity = Gravity.CENTER
            background = pill(ColorUtils.setAlphaComponent(Color.WHITE, 22), 14f)
            setPadding((14 * density).toInt(), (3 * density).toInt(), (14 * density).toInt(), (3 * density).toInt())
            setOnClickListener {
                onInteract()
                moreShown = !moreShown
                relayout()
            }
        }
        addView(
            moreButton,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (2 * density).toInt()
            },
        )
        // capsule: tapping the page number opens / closes the full card
        pageCounter.setOnClickListener {
            if (style == 2) {
                onInteract()
                capsuleOpen = !capsuleOpen
                relayout()
            }
        }
        relayout()
    }

    private var swipeDownY = 0f
    private var swipeDownX = 0f
    private var swipeFired = false

    /** A drag UP from anywhere on the card (even on a button) opens the chapters sheet. */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeDownY = ev.rawY
                swipeDownX = ev.rawX
                swipeFired = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = swipeDownY - ev.rawY
                if (!swipeFired && dy > 28 * density && abs(ev.rawX - swipeDownX) < dy) {
                    swipeFired = true
                    onInteract()
                    onFlingUp()
                    return true
                }
            }
        }
        return false
    }

    /** Round button with a thin outline; filled with the accent tint while its panel is open. */
    private fun roundButton(active: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (active) ColorUtils.setAlphaComponent(accent, 80) else ColorUtils.setAlphaComponent(Color.WHITE, 22))
        setStroke((1 * density).toInt().coerceAtLeast(1), ColorUtils.setAlphaComponent(if (active) accent else accent2, 150))
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
            val pad = (12 * density).toInt()
            setPadding(pad, pad, pad, pad)
            setOnClickListener { toggle(tool) }
        }
        buttons[tool] = button
        // fixed round size; the space between the buttons stretches (they used to stretch themselves into ovals)
        if (row.childCount > 0) row.addView(View(context), LayoutParams(1, 1, 1f))
        row.addView(button, LayoutParams((46 * density).toInt(), (46 * density).toInt()))
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
        brightSwitch.isVisible = tool == Tool.BRIGHTNESS
        when (tool) {
            Tool.SPEED -> {
                slider.isEnabled = true
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
                brightSwitch.setOnCheckedChangeListener(null)
                brightSwitch.isChecked = prefs.customBrightness().get()
                slider.isEnabled = brightSwitch.isChecked
                brightSwitch.setOnCheckedChangeListener { _, checked ->
                    prefs.customBrightness().set(checked)
                    slider.isEnabled = checked
                    onInteract()
                }
            }
            Tool.VOLUME -> {
                slider.isEnabled = true
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
