package eu.kanade.tachiyomi.ui.more.stats

import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import co.touchlab.kermit.Logger
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.databinding.StatsControllerBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.controller.BaseLegacyController
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsController
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.mapStatus
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.roundToTwoDecimal
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import eu.kanade.tachiyomi.util.view.compatToolTipText
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlin.math.roundToInt
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

class StatsController : BaseLegacyController<StatsControllerBinding>() {

    val presenter = StatsPresenter()
    private val prefs: PreferencesHelper by injectLazy()
    private var rData: RStatsData? = null

    private val mangaDistinct = presenter.mangaDistinct
    private var scoresList = emptyList<Double>()

    /**
     * Returns the toolbar title to show when this controller is attached.
     */
    override fun getTitle() = activity?.getString(MR.strings.statistics)

    override fun createBinding(inflater: LayoutInflater) = StatsControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        scrollViewWith(binding.statsScrollView, true)
        setupProfile()
        handleGeneralStats()
        handleHero()
        handleExtraSections()
        if (mangaDistinct.isNotEmpty()) {
            binding.viewDetailLayout.setOnClickListener {
                router.pushController(StatsDetailsController().withFadeTransaction())
            }
            handleStatusDistribution()
        }
        if (scoresList.isNotEmpty()) handleScoreDistribution()
    }

    private var profileView: RStatsProfileView? = null

    private companion object {
        const val REQ_AVATAR = 8811
        const val REQ_BANNERS = 8812
    }

    /** R: profile (round picture + flipping banners + name) at the very top of the statistics. */
    private fun setupProfile() {
        profileView = null
        if (!prefs.rStatsProfile().get()) return
        val ctx = view?.context ?: return
        val container = (binding.statsScrollView.getChildAt(0) as? android.widget.LinearLayout) ?: return
        val pv = RStatsProfileView(
            ctx,
            prefs,
            viewScope,
            { pickImages(REQ_AVATAR, false) },
            { pickImages(REQ_BANNERS, true) },
        )
        container.addView(pv, 0)
        profileView = pv
    }

    private fun pickImages(code: Int, multiple: Boolean) {
        val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        }
        startActivityForResult(android.content.Intent.createChooser(intent, "اختر صورة"), code)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return
        if (requestCode != REQ_AVATAR && requestCode != REQ_BANNERS) return
        val ctx = activity ?: return
        val uris = buildList {
            data.data?.let { add(it) }
            val clip = data.clipData
            if (clip != null) for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri)
        }.distinct()
        if (uris.isEmpty()) return
        viewScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val dir = java.io.File(ctx.filesDir, "r_profile").apply { mkdirs() }
                uris.mapNotNull { uri ->
                    try {
                        val out = java.io.File(dir, "img_${System.currentTimeMillis()}_${uri.hashCode().toUInt()}.jpg")
                        ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                        out.absolutePath.takeIf { out.length() > 0 }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            if (saved.isEmpty()) return@launch
            if (requestCode == REQ_AVATAR) {
                prefs.rProfileAvatar().set(saved.first())
            } else {
                val old = prefs.rProfileBanners().get().split('\n').filter { it.isNotBlank() }
                prefs.rProfileBanners().set((old + saved).joinToString("\n"))
            }
            profileView?.refresh()
        }
    }

    private fun handleGeneralStats() {
        val mangaTracks = mangaDistinct.map { it to presenter.getTracks(it.manga) }
        scoresList = getScoresList(mangaTracks)
        with(binding) {
            viewDetailLayout.isVisible = mangaDistinct.isNotEmpty()
            statsTotalMangaText.text = mangaDistinct.count().toString()
            statsTotalChaptersText.text = mangaDistinct.sumOf { it.totalChapters }.toString()
            statsChaptersReadText.text = mangaDistinct.sumOf { it.read }.toString()
            statsMangaMeanScoreText.text = if (scoresList.isEmpty()) {
                statsMangaMeanScoreText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                activity?.getString(MR.strings.none)
            } else {
                scoresList.average().roundToTwoDecimal().toString()
            }
            statsTrackedMangaText.text = mangaTracks.count { it.second.isNotEmpty() }.toString()
            statsChaptersDownloadedText.text = mangaDistinct.sumOf { presenter.getDownloadCount(it) }.toString()
            statsTotalTagsText.text = mangaDistinct.flatMap { it.manga.getTags() }.distinct().count().toString()
            statsMangaLocalText.text = mangaDistinct.count { it.manga.isLocal() }.toString()
            statsGlobalUpdateMangaText.text = presenter.getGlobalUpdateManga().count().toString()
            statsSourcesText.text = presenter.getSources().count().toString()
            statsTrackersText.text = presenter.getLoggedTrackers().count().toString()
            statsReadDurationText.text = presenter.getReadDuration()
            statsReadDurationLayout.compatToolTipText = activity?.getString(MR.strings.read_duration_info)
        }
    }

    /** R: big numbers on top, last-7-days chart and the read / unread donut. */
    private fun handleHero() {
        val ctx = activity ?: return
        val read = mangaDistinct.sumOf { it.read }
        val total = mangaDistinct.sumOf { it.totalChapters }
        val unread = (total - read).coerceAtLeast(0)
        val text = ctx.getResourceColor(R.attr.colorOnBackground)
        with(binding) {
            rHeroTime.text = statsReadDurationText.text
            rHeroChapters.text = read.toString()
            rHeroTitles.text = mangaDistinct.size.toString()
            try {
                val week = presenter.getWeeklyChapters()
                val palette = StatsHelper.PIE_CHART_COLOR_LIST
                val set = BarDataSet(
                    week.mapIndexed { i, (_, n) -> BarEntry(i.toFloat(), n.toFloat()) },
                    "",
                ).apply {
                    this.colors = week.indices.map { palette[(it * 2) % palette.size] }
                    valueTextColor = text
                    valueTextSize = 11f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float) = if (value > 0f) value.toInt().toString() else ""
                    }
                }
                with(rWeeklyChart) {
                    data = BarData(set).apply { barWidth = 0.6f }
                    axisLeft.isEnabled = false
                    axisRight.isEnabled = false
                    axisLeft.axisMinimum = 0f
                    description.isEnabled = false
                    legend.isEnabled = false
                    setTouchEnabled(false)
                    xAxis.apply {
                        setDrawGridLines(false)
                        position = XAxis.XAxisPosition.BOTTOM
                        textColor = text
                        granularity = 1f
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float) = week.getOrNull(value.toInt())?.first ?: ""
                        }
                    }
                    animateY(600)
                    invalidate()
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to show weekly chart" }
            }
            try {
                val entries = arrayListOf(PieEntry(read.toFloat(), "اتقرا"), PieEntry(unread.toFloat(), "لسه"))
                val set = PieDataSet(entries, "").apply {
                    this.colors = listOf(0xFF7BD555.toInt(), 0xFF3BAEEA.toInt())
                    sliceSpace = 3f
                }
                with(rReadDonut) {
                    data = PieData(set).apply { setDrawValues(false) }
                    isDrawHoleEnabled = true
                    holeRadius = 68f
                    setHoleColor(ContextCompat.getColor(context, AR.color.transparent))
                    setDrawEntryLabels(false)
                    setTouchEnabled(false)
                    description.isEnabled = false
                    legend.isEnabled = false
                    animateY(700)
                    invalidate()
                }
                val pct = if (total > 0) (read * 100f / total).roundToInt() else 0
                rDonutCenter.text = "$pct%"
                rDonutCenter.setTextColor(text)
                rDonutLegend.text = "اتقرا $read • لسه $unread"
            } catch (e: Exception) {
                Logger.e(e) { "Failed to show donut" }
            }
        }
    }

    /** R: streak, weekly goal, top 5, hours, month comparison, genre / source donut. */
    private fun handleExtraSections() {
        val ctx = activity ?: return
        if (!prefs.rStatsExtra().get()) {
            binding.rExtraStats.isVisible = false
            return
        }
        // the history pass runs off the main thread so the screen opens at once
        viewScope.launch {
            try {
                val data = rData ?: withContext(Dispatchers.IO) { presenter.getRData() }.also { rData = it }
                binding.rHeroAvg.text = "%.1f".format(data.dailyAvg)
                binding.rExtraStats.isVisible = true
                RStatsSections.build(ctx, binding.rExtraStats, data, prefs.rWeeklyGoal().get()) { showGoalDialog() }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to build R stats sections" }
                binding.rExtraStats.isVisible = false
            }
        }
    }

    private fun showGoalDialog() {
        val ctx = activity ?: return
        val input = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefs.rWeeklyGoal().get().takeIf { it > 0 }?.toString() ?: "")
            hint = "عدد الشباتر في الأسبوع"
        }
        val pad = (20 * ctx.resources.displayMetrics.density).toInt()
        val frame = android.widget.FrameLayout(ctx).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("هدف الأسبوع")
            .setView(frame)
            .setPositiveButton(AR.string.ok) { _, _ ->
                val n = input.text.toString().toIntOrNull()?.coerceIn(0, 9999) ?: 0
                prefs.rWeeklyGoal().set(n)
                handleExtraSections()
            }
            .setNeutralButton("إلغاء الهدف") { _, _ ->
                prefs.rWeeklyGoal().set(0)
                handleExtraSections()
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }

    private fun getScoresList(mangaTracks: List<Pair<LibraryManga, MutableList<Track>>>): List<Double> {
        return mangaTracks.filter { it.second.isNotEmpty() }
            .map {
                it.second.filter { track -> track.score > 0 }
                    .mapNotNull { track -> presenter.get10PointScore(track) }
                    .average()
            }.filter { it > 0.0 }
    }

    private fun Manga.getTags(): List<String> {
        return getGenres()?.map { it.uppercase() } ?: emptyList()
    }

    private fun handleStatusDistribution() {
        binding.mangaStatsStatusLayout.isVisible = true
        val statusMap = StatsHelper.STATUS_COLOR_MAP
        val pieEntries = ArrayList<PieEntry>()

        val mangaStatusDistributionList = statusMap.mapNotNull { (status, color) ->
            val libraryCount = mangaDistinct.count { it.manga.status == status }
            if (status == SManga.UNKNOWN && libraryCount == 0) return@mapNotNull null
            pieEntries.add(PieEntry(libraryCount.toFloat(), activity!!.mapStatus(status)))
            StatusDistributionItem(activity!!.mapStatus(status), libraryCount, color)
        }

        val pieDataSet = PieDataSet(pieEntries, activity!!.getString(MR.strings.manga_status_distribution))
        pieDataSet.colors = mangaStatusDistributionList.map { it.color }
        showMangaStatsStatusChart(pieDataSet)
        binding.mangaStatsStatusRecyclerView.adapter =
            StatsLegendAdapter(mangaStatusDistributionList)
    }

    private fun showMangaStatsStatusChart(pieDataSet: PieDataSet) {
        try {
            val pieData = PieData(pieDataSet)
            pieData.setDrawValues(false)

            binding.mangaStatsStatusPieChart.apply {
                setHoleColor(ContextCompat.getColor(context, AR.color.transparent))
                setDrawEntryLabels(false)
                setTouchEnabled(false)
                description.isEnabled = false
                legend.isEnabled = false
                data = pieData
                invalidate()
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to show chart" }
        }
    }

    private fun handleScoreDistribution() {
        binding.mangaStatsScoreLayout.isVisible = true
        val scoreMap = StatsHelper.SCORE_COLOR_MAP
        val userScoreList = scoresList.groupingBy { it.roundToInt().coerceIn(1..10) }
            .eachCount().toSortedMap()

        val barEntries = scoreMap.map { (score, _) ->
            BarEntry(score.toFloat(), userScoreList[score]?.toFloat() ?: 0f)
        }
        val barDataSet = BarDataSet(barEntries, activity!!.getString(MR.strings.manga_score_distribution))
        barDataSet.colors = scoreMap.values.toList()
        showMangaStatsScoreChart(barDataSet)
    }

    private fun showMangaStatsScoreChart(barDataSet: BarDataSet) {
        val valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float) = value.toInt().toString()
        }

        try {
            with(binding.mangaStatsScoreBarChart) {
                val barData = BarData(barDataSet)
                barData.setValueTextColor(activity!!.getResourceColor(R.attr.colorOnBackground))
                barData.setValueFormatter(valueFormatter)
                barData.barWidth = 0.6f
                barData.setValueTextSize(10f)
                axisLeft.isEnabled = false
                axisRight.isEnabled = false

                xAxis.apply {
                    setDrawGridLines(false)
                    position = XAxis.XAxisPosition.BOTTOM
                    setLabelCount(barDataSet.entryCount, false)
                    textColor = activity!!.getResourceColor(R.attr.colorOnBackground)
                }

                apply {
                    setTouchEnabled(false)
                    description.isEnabled = false
                    legend.isEnabled = false
                    data = barData
                    invalidate()
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to show chart" }
        }
    }

    class StatusDistributionItem(
        val status: String,
        val amount: Int,
        val color: Int,
    )
}
