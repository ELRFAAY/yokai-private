package eu.kanade.tachiyomi.ui.more.stats

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.RAchievements
import eu.kanade.tachiyomi.util.RChest
import eu.kanade.tachiyomi.util.RDragonLevel
import eu.kanade.tachiyomi.util.RReadLog
import eu.kanade.tachiyomi.util.rMangaKey
import eu.kanade.tachiyomi.util.storage.getUriCompat
import yokai.util.coil.loadManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.LocalDate

/**
 * R: the profile sections under the header card, built from code: weekly chest, badges (collect by hand / collect all), records,
 * monthly calendar, favourite manga and the share card. Everything reads the history, so a restored backup shows its old badges.
 */
object RProfileExtras {
    private fun dp(ctx: Context, v: Float) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(ctx, 14f), dp(ctx, 12f), dp(ctx, 14f), dp(ctx, 12f))
        background = GradientDrawable().apply {
            cornerRadius = dp(ctx, 14f).toFloat()
            setColor(0x14808080)
            setStroke(dp(ctx, 1f), 0x33808080)
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(ctx, 10f)
        }
    }

    private fun title(ctx: Context, t: String) = TextView(ctx).apply {
        text = t
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(ctx, 8f))
    }

    /** The same animated XP icon the reader counter uses (r_xp_0..10) with a text next to it, e.g. "+50". */
    private fun xpRow(ctx: Context, text: String, sizeDp: Float = 16f, bold: Boolean = true, center: Boolean = false): View {
        val anim = android.graphics.drawable.AnimationDrawable().apply {
            for (i in 0..10) {
                val id = ctx.resources.getIdentifier("r_xp_$i", "drawable", ctx.packageName)
                if (id != 0) androidx.core.content.ContextCompat.getDrawable(ctx, id)?.let { addFrame(it, 90) }
            }
            isOneShot = false
        }
        val icon = ImageView(ctx).apply {
            setImageDrawable(anim)
            post { anim.start() }
        }
        val label = TextView(ctx).apply {
            this.text = text
            textSize = 12.5f
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = (if (center) Gravity.CENTER_HORIZONTAL else Gravity.START) or Gravity.CENTER_VERTICAL
            addView(icon, LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp)))
            addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(ctx, 4f) })
        }
    }

    private fun small(ctx: Context, t: String, alpha: Float = 0.7f) = TextView(ctx).apply {
        text = t
        textSize = 12.5f
        this.alpha = alpha
    }

    private fun button(ctx: Context, t: String, onClick: () -> Unit) = com.google.android.material.button.MaterialButton(ctx).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    /**
     * Profile sections. [ach] = the "الإنجازات" tab (weekly chest, badges with a type filter, records); [root] = profile
     * (calendar, favourites, share). [onChanged] runs after XP changed (collect / chest) so the header card can repaint.
     */
    fun build(
        ctx: Context,
        root: LinearLayout,
        ach: LinearLayout,
        d: RStatsData,
        library: List<Manga>,
        prefs: PreferencesHelper = Injekt.get(),
        openManga: (Manga) -> Unit = {},
        onChanged: () -> Unit,
    ) {
        root.removeAllViews()
        ach.removeAllViews()
        fun rebuild() {
            onChanged()
            build(ctx, root, ach, d, library, prefs, openManga, onChanged)
        }
        ach.addView(chestCard(ctx, prefs, ::rebuild))
        ach.addView(badgesCard(ctx, d, prefs, ::rebuild))
        ach.addView(recordsCard(ctx, d))
        root.addView(calendarCard(ctx, d))
        root.addView(favouritesCard(ctx, library, prefs, openManga, ::rebuild))
        root.addView(shareCard(ctx, d, prefs))
    }

    // ---- daily chest (7-day cycle) ----
    private fun chestCard(ctx: Context, prefs: PreferencesHelper, rebuild: () -> Unit): View {
        val c = card(ctx)
        c.addView(title(ctx, "🎁 صندوق اليوم"))
        val avail = RChest.available(prefs)
        val day = RChest.cycleDay(prefs)
        val chestPic = ctx.resources.getIdentifier(if (avail) "r_badge_chest" else "r_badge_chest_open", "drawable", ctx.packageName)
        if (chestPic != 0) {
            c.addView(
                ImageView(ctx).apply { setImageResource(chestPic) },
                LinearLayout.LayoutParams(dp(ctx, 84f), dp(ctx, 84f)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(ctx, 6f) },
            )
        }
        // the 7 days of the cycle
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        for (i in 1..7) {
            val done = if (avail) i < day else i <= day
            val today = i == day
            row.addView(
                TextView(ctx).apply {
                    text = if (done) "✓" else "$i"
                    gravity = Gravity.CENTER
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(if (done || today) Color.WHITE else 0xFF888888.toInt())
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(ctx, 10f).toFloat()
                        setColor(if (done) 0xFF2E7D45.toInt() else if (today) 0xFFE0A800.toInt() else 0x22888888)
                    }
                },
                LinearLayout.LayoutParams(0, dp(ctx, 38f), 1f).apply { marginStart = dp(ctx, 2f); marginEnd = dp(ctx, 2f) },
            )
        }
        c.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(ctx, 8f) })
        val r = RChest.range(day)
        if (avail) {
            c.addView(small(ctx, "اليوم $day من 7 • فيه من ${r.first} لـ ${r.last} XP. يتجدد كل يوم 12 بالليل، وتفويت يوم بيرجّعك لليوم الأول."))
            c.addView(
                button(ctx, "افتح الصندوق") {
                    val xp = RChest.open(prefs)
                    if (xp > 0) {
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle(if (day == 7) "🎉 صندوق اليوم السابع!" else "🎁 الصندوق اتفتح")
                            .setMessage("خدت +$xp XP")
                            .setPositiveButton("تمام", null)
                            .show()
                        rebuild()
                    }
                },
            )
        } else {
            c.addView(small(ctx, "فتحت صندوق النهارده (اليوم $day من 7). الجاي بعد 12 بالليل. 😴"))
        }
        return c
    }

    // ---- badges: one list per type, with a filter ----
    /** -1 all, 0 chapters, 1 hours, 2 streak, 3 ready to collect. Kept while the screen is open. */
    private var badgeFilter = -1

    private fun badgesCard(ctx: Context, d: RStatsData, prefs: PreferencesHelper, rebuild: () -> Unit): View {
        val c = card(ctx)
        val done = RAchievements.claimed(prefs)
        val all = RAchievements.all
        val unlocked = all.filter { RAchievements.unlocked(it, d) }
        val ready = unlocked.filter { it.id !in done }
        c.addView(title(ctx, "🏅 الإنجازات (${unlocked.size}/${all.size})"))
        // overall progress
        c.addView(
            android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = all.size
                progress = unlocked.size
                progressTintList = android.content.res.ColorStateList.valueOf(0xFF2E7D45.toInt())
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 8f)).apply { bottomMargin = dp(ctx, 8f) },
        )
        if (ready.isNotEmpty()) {
            c.addView(small(ctx, "جاهز تجمعه: ${ready.size} إنجاز", 1f))
            c.addView(xpRow(ctx, "+${ready.sumOf { it.xp }}", 20f))
            c.addView(
                button(ctx, "جمع الكل") {
                    val (n, xp) = RAchievements.claimAll(d, prefs)
                    MaterialAlertDialogBuilder(ctx)
                        .setTitle("تم الجمع")
                        .setMessage("جمعت $n إنجاز، و+$xp XP")
                        .setPositiveButton("تمام", null)
                        .show()
                    rebuild()
                },
            )
        } else {
            c.addView(small(ctx, "مفيش XP جاهز للجمع دلوقتي."))
        }
        // type filter
        val names = listOf("الكل", "شباتر", "ساعات", "سلسلة", "جاهز")
        val values = listOf(-1, 0, 1, 2, 3)
        val ids = IntArray(names.size) { View.generateViewId() }
        val group = com.google.android.material.button.MaterialButtonToggleGroup(ctx).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        names.forEachIndexed { i, n ->
            group.addView(
                com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    id = ids[i]; text = n; textSize = 12f; isAllCaps = false; insetTop = 0; insetBottom = 0; minWidth = 0
                    setPadding(0, 0, 0, 0)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        group.check(ids[values.indexOf(badgeFilter).coerceAtLeast(0)])
        c.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(ctx, 10f) })
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(list)

        fun fill() {
            list.removeAllViews()
            val sections = listOf(
                RAchievements.CHAPTERS to "📖 الشباتر",
                RAchievements.HOURS to "⏱ ساعات القراءة",
                RAchievements.STREAK to "🔥 أيام السلسلة",
            )
            sections.forEach { (kind, label) ->
                if (badgeFilter in 0..2 && badgeFilter != kind) return@forEach
                var items = all.filter { it.kind == kind }
                if (badgeFilter == 3) items = items.filter { RAchievements.unlocked(it, d) && it.id !in done }
                if (items.isEmpty()) return@forEach
                val got = all.count { it.kind == kind && RAchievements.unlocked(it, d) }
                val total = all.count { it.kind == kind }
                list.addView(
                    TextView(ctx).apply {
                        text = "$label  ($got/$total)"
                        textSize = 14f
                        setTypeface(typeface, Typeface.BOLD)
                        setPadding(0, dp(ctx, 14f), 0, dp(ctx, 4f))
                    },
                )
                val cols = 4
                for (rowStart in items.indices step cols) {
                    val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = cols.toFloat() }
                    for (i in rowStart until minOf(rowStart + cols, items.size)) {
                        row.addView(badgeCell(ctx, items[i], d, prefs, done, rebuild), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    list.addView(row)
                }
            }
            if (list.childCount == 0) list.addView(small(ctx, "مفيش حاجة هنا."))
        }
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                badgeFilter = values[ids.indexOf(checkedId).coerceAtLeast(0)]
                fill()
            }
        }
        fill()
        return c
    }

    private fun badgeCell(
        ctx: Context,
        b: RAchievements.Badge,
        d: RStatsData,
        prefs: PreferencesHelper,
        done: Set<String>,
        rebuild: () -> Unit,
    ): View {
        val isOpen = RAchievements.unlocked(b, d)
        val collected = b.id in done
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(2, dp(ctx, 8f), 2, dp(ctx, 4f))
        }
        val iconId = ctx.resources.getIdentifier(b.iconName(), "drawable", ctx.packageName)
        if (iconId != 0) {
            cell.addView(
                ImageView(ctx).apply {
                    setImageResource(iconId)
                    if (!isOpen) {
                        alpha = 0.45f
                        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
                    }
                },
                LinearLayout.LayoutParams(dp(ctx, 56f), dp(ctx, 56f)),
            )
        } else {
            cell.addView(TextView(ctx).apply { text = b.emoji; textSize = 26f; gravity = Gravity.CENTER })
        }
        cell.addView(TextView(ctx).apply { text = b.title; textSize = 11f; gravity = Gravity.CENTER; alpha = if (isOpen) 1f else 0.6f })
        if (isOpen && !collected) {
            cell.addView(xpRow(ctx, "+${b.xp}", 14f, center = true))
        } else {
            cell.addView(
                TextView(ctx).apply {
                    text = if (!isOpen) "${RAchievements.value(b.kind, d)}/${b.target}" else "✓ اتجمع"
                    textSize = 10.5f
                    gravity = Gravity.CENTER
                    alpha = 0.7f
                },
            )
        }
        cell.setOnClickListener {
            if (isOpen && !collected) {
                val xp = RAchievements.claim(b, d, prefs)
                if (xp > 0) {
                    Toast.makeText(ctx, "+$xp XP", Toast.LENGTH_SHORT).show()
                    rebuild()
                }
            } else {
                Toast.makeText(ctx, b.desc + if (isOpen) "" else " (${RAchievements.value(b.kind, d)}/${b.target})", Toast.LENGTH_SHORT).show()
            }
        }
        return cell
    }

    // ---- records ----
    private fun recordsCard(ctx: Context, d: RStatsData): View {
        val c = card(ctx)
        c.addView(title(ctx, "🏆 الأرقام القياسية"))
        val best = d.dayCounts.maxByOrNull { it.value }
        val rt = runCatching { RReadLog.summary() }.getOrNull()
        c.addView(small(ctx, "أطول سلسلة: ${d.streakBest} يوم", 1f))
        if (best != null) {
            c.addView(small(ctx, "أكتر يوم قراءة: ${best.value} شابتر (${LocalDate.ofEpochDay(best.key)})", 1f))
        }
        if (rt != null && rt.bestDayMs > 0) {
            c.addView(small(ctx, "أطول يوم وقت: ${RReadLog.format(rt.bestDayMs)} (${rt.bestDay})", 1f))
        }
        c.addView(small(ctx, "إجمالي الشباتر: ${d.totalChapters} • الساعات: ${d.totalReadMs / 3_600_000L}", 1f))
        return c
    }

    // ---- calendar: last 12 weeks, GitHub style ----
    private fun calendarCard(ctx: Context, d: RStatsData): View {
        val c = card(ctx)
        c.addView(title(ctx, "📅 تقويم القراءة (آخر 12 أسبوع)"))
        val today = LocalDate.now()
        // columns = weeks (oldest first), rows = Sat..Fri; the last column is this week
        val weeks = 12
        val startOfThisWeek = today.minusDays(((today.dayOfWeek.value + 1) % 7).toLong()) // Saturday = week start
        val grid = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL }
        val max = (d.dayCounts.values.maxOrNull() ?: 1).coerceAtLeast(1)
        val size = dp(ctx, 18f)
        val gap = dp(ctx, 3f)
        for (w in 0 until weeks) {
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            for (r in 0 until 7) {
                val day = startOfThisWeek.minusWeeks((weeks - 1 - w).toLong()).plusDays(r.toLong())
                val n = if (day.isAfter(today)) -1 else d.dayCounts[day.toEpochDay()] ?: 0
                val cell = View(ctx).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(ctx, 3f).toFloat()
                        setColor(
                            when {
                                n < 0 -> Color.TRANSPARENT
                                n == 0 -> 0x22808080
                                else -> {
                                    val level = (n * 4f / max).coerceIn(1f, 4f)
                                    Color.argb(((0.30f + 0.17f * level) * 255).toInt(), 0x2E, 0xB8, 0x5C)
                                }
                            },
                        )
                    }
                    if (n >= 0) setOnClickListener { Toast.makeText(ctx, "$day: $n شابتر", Toast.LENGTH_SHORT).show() }
                }
                col.addView(cell, LinearLayout.LayoutParams(size, size).apply { setMargins(gap / 2, gap / 2, gap / 2, gap / 2) })
            }
            grid.addView(col)
        }
        c.addView(grid)
        c.addView(small(ctx, "كل مربع = يوم. كل ما اللون أغمق، قرأت أكتر."))
        return c
    }

    // ---- favourites (up to 5) ----
    private const val MAX_FAV = 10
    private const val FAV_PER_ROW = 5

    private fun favouritesCard(ctx: Context, library: List<Manga>, prefs: PreferencesHelper, openManga: (Manga) -> Unit, rebuild: () -> Unit): View {
        val c = card(ctx)
        c.addView(title(ctx, "⭐ مانجاتي المفضلة"))
        val keys = prefs.rFavManga().get().split("\n").filter { it.isNotBlank() }
        val favs = keys.mapNotNull { k -> library.firstOrNull { rMangaKey(it.source, it.url) == k } }
        val rows = ArrayList<LinearLayout>()
        // two rows of 5 slots; the second row only appears when more than 5 are chosen
        for (r in 0 until MAX_FAV / FAV_PER_ROW) {
            if (r > 0 && favs.size <= r * FAV_PER_ROW) break
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = FAV_PER_ROW.toFloat() }
            for (i in r * FAV_PER_ROW until (r + 1) * FAV_PER_ROW) {
                val m = favs.getOrNull(i)
                val img = ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    background = GradientDrawable().apply { cornerRadius = dp(ctx, 8f).toFloat(); setColor(if (m != null) 0x22808080 else 0x11808080) }
                    if (m != null) {
                        runCatching { loadManga(m) }
                        setOnClickListener { openManga(m) }
                    }
                }
                row.addView(img, LinearLayout.LayoutParams(0, dp(ctx, 96f), 1f).apply { setMargins(dp(ctx, 3f), dp(ctx, 3f), dp(ctx, 3f), dp(ctx, 3f)) })
            }
            rows += row
        }
        if (favs.isEmpty()) c.addView(small(ctx, "اختار لحد $MAX_FAV مانجا يظهروا هنا."))
        rows.forEach { c.addView(it) }
        c.addView(
            button(ctx, if (favs.isEmpty()) "اختار المفضلة" else "غيّر المفضلة") {
                pickFavourites(ctx, library, keys) { chosen ->
                    prefs.rFavManga().set(chosen.joinToString("\n"))
                    rebuild()
                }
            },
        )
        return c
    }

    /** Picker: a search box + a grid of covers on colour cards; tap to select (max [MAX_FAV]). */
    private fun pickFavourites(ctx: Context, library: List<Manga>, initial: List<String>, onSaved: (List<String>) -> Unit) {
        val all = library.distinctBy { it.id }.sortedBy { it.title.lowercase() }
        val selected = LinkedHashSet(initial)
        var shown: List<Manga> = all
        val palette = intArrayOf(0xFF2E7D45.toInt(), 0xFF1565C0.toInt(), 0xFF6A1B9A.toInt(), 0xFFC62828.toInt(), 0xFFEF6C00.toInt(), 0xFF00838F.toInt(), 0xFF4E342E.toInt())
        val cols = 3
        val cellW = (ctx.resources.displayMetrics.widthPixels - dp(ctx, 64f)) / cols

        val search = android.widget.EditText(ctx).apply {
            hint = "ابحث بالاسم"
            setSingleLine()
        }
        val rv = androidx.recyclerview.widget.RecyclerView(ctx).apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(ctx, cols)
        }
        lateinit var dlg: androidx.appcompat.app.AlertDialog
        fun refreshTitle() { dlg.setTitle("اختار لحد $MAX_FAV (${selected.size}/$MAX_FAV)") }

        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun getItemCount() = shown.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val cell = android.widget.FrameLayout(ctx).apply {
                    layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(cellW, (cellW * 1.5f).toInt()).apply { setMargins(4, 4, 4, 4) }
                    clipToOutline = true
                }
                cell.addView(ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; tag = "cover" }, android.widget.FrameLayout.LayoutParams(-1, -1))
                cell.addView(
                    TextView(ctx).apply {
                        tag = "name"; textSize = 11f; maxLines = 2; setTextColor(Color.WHITE)
                        setPadding(dp(ctx, 5f), dp(ctx, 3f), dp(ctx, 5f), dp(ctx, 3f))
                    },
                    android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM),
                )
                cell.addView(
                    TextView(ctx).apply { tag = "check"; text = "✓"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER },
                    android.widget.FrameLayout.LayoutParams(-1, -1),
                )
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(cell) {}
            }
            override fun onBindViewHolder(h: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val m = shown[position]
                val key = rMangaKey(m.source, m.url)
                val cell = h.itemView as android.widget.FrameLayout
                val color = palette[(m.title.hashCode() and 0x7fffffff) % palette.size]
                cell.background = GradientDrawable().apply { cornerRadius = dp(ctx, 8f).toFloat(); setColor(color) }
                cell.findViewWithTag<ImageView>("cover").apply { setImageDrawable(null); runCatching { loadManga(m) } }
                cell.findViewWithTag<TextView>("name").apply { text = m.title; setBackgroundColor((color and 0x00FFFFFF) or 0xCC000000.toInt()) }
                fun paint() {
                    val on = key in selected
                    cell.findViewWithTag<TextView>("check").apply {
                        visibility = if (on) View.VISIBLE else View.GONE
                        setBackgroundColor(0x882E7D45.toInt())
                    }
                }
                paint()
                cell.setOnClickListener {
                    if (key in selected) {
                        selected.remove(key)
                    } else if (selected.size >= MAX_FAV) {
                        Toast.makeText(ctx, "لحد $MAX_FAV بس", Toast.LENGTH_SHORT).show()
                    } else {
                        selected.add(key)
                    }
                    paint()
                    refreshTitle()
                }
            }
        }
        rv.adapter = adapter
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(e: android.text.Editable?) {
                val q = e?.toString()?.trim().orEmpty()
                shown = if (q.isEmpty()) all else all.filter { it.title.contains(q, ignoreCase = true) }
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16f), dp(ctx, 8f), dp(ctx, 16f), 0)
            addView(search)
            addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 420f)))
        }
        dlg = MaterialAlertDialogBuilder(ctx)
            .setTitle("اختار لحد $MAX_FAV")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ -> onSaved(selected.toList()) }
            .setNegativeButton("إلغاء", null)
            .create()
        dlg.show()
        refreshTitle()
    }

    // ---- share card ----
    private fun shareCard(ctx: Context, d: RStatsData, prefs: PreferencesHelper): View {
        val c = card(ctx)
        c.addView(title(ctx, "📤 كارت المشاركة"))
        c.addView(small(ctx, "صورة فيها مستواك وأهم أرقامك. احفظها أو ابعتها."))
        c.addView(
            button(ctx, "شارك الكارت") {
                runCatching {
                    val bmp = drawShareBitmap(ctx, d, prefs)
                    val dir = File(ctx.cacheDir, "r_share").apply { mkdirs() }
                    val f = File(dir, "yokai_profile.png")
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, f.getUriCompat(ctx))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(send, "مشاركة الكارت"))
                }.onFailure { Toast.makeText(ctx, "تعذّر إنشاء الكارت", Toast.LENGTH_SHORT).show() }
            },
        )
        return c
    }

    private fun drawShareBitmap(ctx: Context, d: RStatsData, prefs: PreferencesHelper): Bitmap {
        val w = 1080
        val h = 640
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF1F4D2E.toInt(), 0xFF2E7D45.toInt(), Shader.TileMode.CLAMP)
        cv.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), 48f, 48f, p)
        p.shader = null
        val info = RDragonLevel.info(RDragonLevel.totalXp(prefs))
        // avatar
        val pic = prefs.rProfileAvatar().get()
        val avatar = (if (pic.isNotBlank() && File(pic).exists()) runCatching { android.graphics.BitmapFactory.decodeFile(pic) }.getOrNull() else null)
            ?: runCatching {
                val res = eu.kanade.tachiyomi.util.RDragonLook.portraitId(ctx, prefs)
                if (res != 0) android.graphics.BitmapFactory.decodeResource(ctx.resources, res) else null
            }.getOrNull()
        if (avatar != null) {
            val r = 120f
            val save = cv.save()
            val path = android.graphics.Path().apply { addCircle(60f + r, 60f + r, r, android.graphics.Path.Direction.CW) }
            cv.clipPath(path)
            cv.drawBitmap(avatar, null, android.graphics.RectF(60f, 60f, 60f + 2 * r, 60f + 2 * r), p)
            cv.restoreToCount(save)
        }
        p.color = Color.WHITE
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 76f
        cv.drawText("LV. ${info.level}", 330f, 150f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = 38f
        p.alpha = 220
        cv.drawText("${info.xp} XP", 330f, 205f, p)
        p.alpha = 255
        // XP bar
        p.color = 0x33FFFFFF
        cv.drawRoundRect(330f, 232f, 1020f, 252f, 10f, 10f, p)
        p.color = 0xFF63B3FF.toInt()
        cv.drawRoundRect(330f, 232f, 330f + 690f * info.progress, 252f, 10f, 10f, p)
        // numbers
        val stats = listOf(
            "${d.totalChapters}" to "شابتر",
            "${d.totalReadMs / 3_600_000L}" to "ساعة",
            "${d.streakBest}" to "أطول سلسلة",
            RDragonLevel.compact(RDragonLevel.gold(info.xp)) to "عملة ذهبية",
        )
        stats.forEachIndexed { i, (v, label) ->
            val cx = 60f + 120f + i * 250f
            p.color = Color.WHITE
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = 78f
            cv.drawText(v, cx + 30f, 470f, p)
            p.typeface = Typeface.DEFAULT
            p.textSize = 34f
            p.alpha = 200
            cv.drawText(label, cx + 30f, 520f, p)
            p.alpha = 255
        }
        p.textAlign = Paint.Align.RIGHT
        p.textSize = 28f
        p.alpha = 150
        cv.drawText("Yokai R", w - 50f, h - 36f, p)
        return bmp
    }
}
