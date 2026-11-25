package eu.kanade.tachiyomi.ui.more.stats

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textview.MaterialTextView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.main.RFloatDragonView
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper.getReadDuration
import eu.kanade.tachiyomi.util.system.getResourceColor
import kotlin.math.abs
import kotlin.math.roundToInt
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.util.coil.loadManga
import android.R as AR

/**
 * R: the extra sections of the stats screen, built in code so the original layout stays untouched.
 * Order: streak, weekly goal, top 5, reading hours, month comparison, genre / source donut.
 */
object RStatsSections {

    fun build(
        ctx: Context,
        root: LinearLayout,
        d: RStatsData,
        goal: Int,
        onGoalClick: () -> Unit,
    ) {
        root.removeAllViews()
        val rt = try { eu.kanade.tachiyomi.util.RReadLog.summary() } catch (e: Exception) { null }
        // each tile = one topic: a square with an icon, a big number and a label; tap = the full card in a dialog
        class Tile(val emoji: String, val label: String, val value: String, val make: () -> View?)
        val tiles = listOf(
            Tile("🔥", "السلسلة", "${d.streakNow}") { streakCard(ctx, d) },
            Tile("🎯", "هدف الأسبوع", if (goal > 0) "${d.weekChapters}/$goal" else "—") { goalCard(ctx, d, goal, onGoalClick) },
            Tile("⏱️", "وقت القراءة", rt?.let { eu.kanade.tachiyomi.util.RReadLog.format(it.todayMs) } ?: "—") { readTimeCard(ctx) },
            Tile("🏆", "الأكتر قراءة", "${d.top.size}") { topCard(ctx, d) },
            Tile("🕒", "ساعات اليوم", "${d.hours.indices.maxByOrNull { d.hours[it] } ?: 0}:00") { hoursCard(ctx, d) },
            Tile("📅", "الشهر", "${d.monthNow}") { monthCard(ctx, d) },
            Tile("🍩", "مقروء / غير مقروء", "${d.totalChapters}") { donutCard(ctx, d) },
        )
        val cols = 2
        var row: LinearLayout? = null
        tiles.forEachIndexed { i, t ->
            if (i % cols == 0) {
                row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    weightSum = cols.toFloat()
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { bottomMargin = ctx.dp(10) }
                }
                root.addView(row)
            }
            val tile = MaterialCardView(ctx).apply {
                radius = ctx.dp(18).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(ctx.getResourceColor(com.google.android.material.R.attr.colorSurfaceVariant))
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(0, ctx.dp(112), 1f).apply {
                    marginStart = ctx.dp(5)
                    marginEnd = ctx.dp(5)
                }
            }
            val box = vBox(ctx).apply {
                gravity = Gravity.CENTER
                setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8))
            }
            box.addView(text(ctx, t.emoji, 26f))
            box.addView(text(ctx, t.value, 20f, bold = true))
            box.addView(text(ctx, t.label, 12f, secondary = true))
            tile.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            tile.setOnClickListener {
                try {
                    val v = t.make() ?: return@setOnClickListener
                    (v.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = 0
                    val pad = ctx.dp(12)
                    val holder = ScrollView(ctx).apply {
                        setPadding(pad, pad, pad, pad)
                        addView(v)
                    }
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                        .setTitle(t.label)
                        .setView(holder)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } catch (e: Exception) {
                    Logger.e(e) { "R stats tile failed" }
                }
            }
            // entrance: fade + rise, one after the other
            tile.alpha = 0f
            tile.translationY = ctx.dp(18).toFloat()
            tile.animate().alpha(1f).translationY(0f).setStartDelay(i * 70L).setDuration(320L).start()
            row?.addView(tile)
            if (i == tiles.lastIndex && tiles.size % cols != 0) {
                row?.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = ctx.dp(5); marginEnd = ctx.dp(5) })
            }
        }
    }

    // ---- sections ----

    private fun streakCard(ctx: Context, d: RStatsData): View {
        val (card, box) = card(ctx, "السلسلة")
        val row = hRow(ctx)
        row.addView(text(ctx, "🔥", 34f))
        val col = vBox(ctx).apply { setPadding(ctx.dp(12), 0, 0, 0) }
        val now = d.streakNow
        col.addView(
            text(
                ctx,
                when {
                    now >= 2 -> "قريت $now أيام ورا بعض"
                    now == 1 -> "قريت النهارده، كمّل بكرة"
                    else -> "مفيش سلسلة دلوقتي، ابدأ النهارده"
                },
                17f,
                bold = true,
            ),
        )
        col.addView(text(ctx, "أطول سلسلة عندك: ${d.streakBest} ${if (d.streakBest in 3..10) "أيام" else "يوم"}", 14f, secondary = true))
        row.addView(col)
        box.addView(row)
        return card
    }

    /** R: real reading time (from the reading log): today, average per reading day, best day, and the top titles. */
    private fun readTimeCard(ctx: Context): View? {
        val s = eu.kanade.tachiyomi.util.RReadLog.summary()
        if (s.avgActiveDayMs <= 0L && s.todayMs <= 0L) return null
        val f = eu.kanade.tachiyomi.util.RReadLog::format
        val (card, box) = card(ctx, "وقت القراءة")
        box.addView(text(ctx, "النهارده: ${f(s.todayMs)}", 18f, bold = true))
        box.addView(text(ctx, "متوسط اليوم اللي قريت فيه: ${f(s.avgActiveDayMs)}", 14f, secondary = true))
        if (s.bestDay != null) box.addView(text(ctx, "أطول يوم: ${s.bestDay} (${f(s.bestDayMs)})", 14f, secondary = true))
        if (s.top.isNotEmpty()) {
            box.addView(text(ctx, "أكتر مناهج قريت فيها", 13f, secondary = true).apply { setPadding(0, ctx.dp(10), 0, ctx.dp(2)) })
            s.top.forEach { (title, ms) -> box.addView(text(ctx, "• $title — ${f(ms)}", 14f)) }
        }
        return card
    }

    private fun goalCard(ctx: Context, d: RStatsData, goal: Int, onClick: () -> Unit): View {
        val (card, box) = card(ctx, "هدف الأسبوع")
        card.isClickable = true
        card.setOnClickListener { onClick() }
        if (goal <= 0) {
            box.addView(text(ctx, "حدد عدد الشباتر اللي عايز تقراها في الأسبوع (اضغط هنا)", 15f))
            return card
        }
        val done = d.weekChapters
        val pct = (done * 100f / goal).roundToInt().coerceIn(0, 100)
        box.addView(text(ctx, "$done من $goal شابتر (آخر ٧ أيام)", 17f, bold = true))
        box.addView(
            LinearProgressIndicator(ctx).apply {
                max = 100
                trackThickness = ctx.dp(10)
                trackCornerRadius = ctx.dp(5)
                setProgressCompat(pct, true)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = ctx.dp(10); bottomMargin = ctx.dp(6) }
            },
        )
        val reached = done >= goal
        box.addView(
            dragonLine(
                ctx,
                if (reached) "مبروك يا بطل! حققت هدف الأسبوع 🎉" else "فاضل ${goal - done} شابتر وتحقق الهدف، يلا!",
            ),
        )
        box.addView(text(ctx, "اضغط لتغيير الهدف", 12f, secondary = true))
        return card
    }

    private fun topCard(ctx: Context, d: RStatsData): View? {
        if (d.top.isEmpty()) return null
        val (card, box) = card(ctx, "أكتر ٥ مانهوا قريتها")
        val maxValue = d.top.maxOf { if (it.timeMs > 0) it.timeMs else it.chapters.toLong() }.coerceAtLeast(1L)
        d.top.forEach { t ->
            val row = hRow(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = ctx.dp(8) }
            }
            val cover = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(ctx.dp(42), ctx.dp(60))
            }
            runCatching { cover.loadManga(t.manga) }
            row.addView(cover)
            val col = vBox(ctx).apply {
                setPadding(ctx.dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(text(ctx, t.manga.title, 15f, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            val value = if (t.timeMs > 0) t.timeMs else t.chapters.toLong()
            col.addView(
                LinearProgressIndicator(ctx).apply {
                    max = 1000
                    trackThickness = ctx.dp(8)
                    trackCornerRadius = ctx.dp(4)
                    setProgressCompat((value * 1000f / maxValue).roundToInt().coerceIn(15, 1000), false)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = ctx.dp(6); bottomMargin = ctx.dp(4) }
                },
            )
            val timeText = if (t.timeMs > 0) " • ${t.timeMs.getReadDuration("")}" else ""
            col.addView(text(ctx, "${t.chapters} شابتر$timeText", 12f, secondary = true))
            row.addView(col)
            box.addView(row)
        }
        return card
    }

    private fun hoursCard(ctx: Context, d: RStatsData): View? {
        if (d.hours.sum() == 0) return null
        val (card, box) = card(ctx, "بتقرا إمتى؟")
        val textColor = ctx.getResourceColor(R.attr.colorOnBackground)
        val primary = ctx.getResourceColor(com.google.android.material.R.attr.colorSecondary)
        val chart = BarChart(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(150))
            val set = BarDataSet(d.hours.mapIndexed { i, n -> BarEntry(i.toFloat(), n.toFloat()) }, "").apply {
                color = primary
                setDrawValues(false)
            }
            data = BarData(set).apply { barWidth = 0.75f }
            axisLeft.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            xAxis.apply {
                setDrawGridLines(false)
                position = XAxis.XAxisPosition.BOTTOM
                this.textColor = textColor
                granularity = 1f
                labelCount = 5
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}"
                }
            }
            invalidate()
        }
        box.addView(chart)
        // when does he read the most?
        val night = (22..23).sumOf { d.hours[it] } + (0..4).sumOf { d.hours[it] }
        val morning = (5..11).sumOf { d.hours[it] }
        val afternoon = (12..17).sumOf { d.hours[it] }
        val evening = (18..21).sumOf { d.hours[it] }
        val comment = when (maxOf(night, morning, afternoon, evening)) {
            night -> "بتقرا أكتر بالليل 🌙 متنساش تنام"
            morning -> "بتقرا أكتر الصبح ☀️ بداية حلوة لليوم"
            afternoon -> "بتقرا أكتر بعد الضهر 🌤️"
            else -> "بتقرا أكتر بالمغرب 🌆"
        }
        box.addView(dragonLine(ctx, comment))
        return card
    }

    private fun monthCard(ctx: Context, d: RStatsData): View {
        val (card, box) = card(ctx, "الشهر ده مع اللي فات")
        val msg = when {
            d.monthPrev == 0 && d.monthNow == 0 -> "لسه ما قريتش في الشهرين دول"
            d.monthPrev == 0 -> "قريت ${d.monthNow} شابتر الشهر ده (مفيش قراءة الشهر اللي فات)"
            else -> {
                val pct = ((d.monthNow - d.monthPrev) * 100f / d.monthPrev).roundToInt()
                when {
                    pct > 0 -> "قريت أكتر بـ $pct% من الشهر اللي فات 📈"
                    pct < 0 -> "قريت أقل بـ ${abs(pct)}% من الشهر اللي فات 📉"
                    else -> "قريت زي الشهر اللي فات بالظبط"
                }
            }
        }
        box.addView(text(ctx, msg, 17f, bold = true))
        box.addView(text(ctx, "الشهر ده: ${d.monthNow} شابتر • الشهر اللي فات: ${d.monthPrev} شابتر", 13f, secondary = true))
        return card
    }

    private fun donutCard(ctx: Context, d: RStatsData): View? {
        if (d.genres.isEmpty() && d.sources.isEmpty()) return null
        val (card, box) = card(ctx, "بتقرا إيه؟")
        val toggle = MaterialButtonToggleGroup(ctx).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val byGenre = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "النوع"
            id = View.generateViewId()
        }
        val bySource = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "المصدر"
            id = View.generateViewId()
        }
        toggle.addView(byGenre)
        toggle.addView(bySource)
        box.addView(toggle)
        val chart = PieChart(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(220))
        }
        val legendText = text(ctx, "", 13f, secondary = true).apply { gravity = Gravity.CENTER }
        box.addView(chart)
        box.addView(legendText)
        val palette = StatsHelper.PIE_CHART_COLOR_LIST
        val show = { items: List<Pair<String, Int>> ->
            val set = PieDataSet(items.map { PieEntry(it.second.toFloat(), it.first) }, "").apply {
                colors = items.indices.map { palette[it % palette.size] }
                sliceSpace = 3f
            }
            chart.apply {
                data = PieData(set).apply { setDrawValues(false) }
                isDrawHoleEnabled = true
                holeRadius = 60f
                setHoleColor(ContextCompat.getColor(ctx, AR.color.transparent))
                setDrawEntryLabels(false)
                setTouchEnabled(false)
                description.isEnabled = false
                legend.isEnabled = false
                animateY(500)
                invalidate()
            }
            val total = items.sumOf { it.second }.coerceAtLeast(1)
            legendText.text = items.joinToString("  •  ") { "${it.first} ${(it.second * 100f / total).roundToInt()}%" }
        }
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            runCatching { show(if (checkedId == byGenre.id) d.genres else d.sources) }
        }
        if (d.genres.isNotEmpty()) toggle.check(byGenre.id) else toggle.check(bySource.id)
        return card
    }

    // ---- small view helpers ----

    private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun card(ctx: Context, title: String): Pair<MaterialCardView, LinearLayout> {
        val box = vBox(ctx).apply { setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(16)) }
        val card = MaterialCardView(ctx).apply {
            radius = ctx.dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ctx.getResourceColor(com.google.android.material.R.attr.colorSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = ctx.dp(14) }
        }
        box.addView(text(ctx, title, 13f, secondary = true).apply { setPadding(0, 0, 0, ctx.dp(8)) })
        card.addView(box)
        return card to box
    }

    private fun vBox(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    private fun hRow(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun text(ctx: Context, value: String, size: Float, bold: Boolean = false, secondary: Boolean = false) =
        MaterialTextView(ctx).apply {
            text = value
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                if (secondary) ctx.getResourceColor(AR.attr.textColorSecondary) else ctx.getResourceColor(R.attr.colorOnBackground),
            )
        }

    /** The small dragon with a line of text next to it. */
    private fun dragonLine(ctx: Context, message: String): View = hRow(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = ctx.dp(6) }
        val floatOn = runCatching { Injekt.get<PreferencesHelper>().let { it.rDragonFloat().get() && !it.rDragonsOff().get() } }.getOrDefault(true)
        if (floatOn) {
            // the text is after the dragon in the row: it points toward it (left in RTL, right in LTR)
            addView(
                RFloatDragonView(ctx).apply {
                    sizeDp = 48
                    pointing = true
                    pointRight = ctx.resources.configuration.layoutDirection != View.LAYOUT_DIRECTION_RTL
                },
            )
        } else {
            addView(
                ImageView(ctx).apply {
                    setImageResource(R.drawable.r_mascot_happy)
                    adjustViewBounds = true
                    layoutParams = LinearLayout.LayoutParams(ctx.dp(48), ctx.dp(48))
                },
            )
        }
        addView(
            text(ctx, message, 14f).apply {
                setPadding(ctx.dp(10), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
    }
}
