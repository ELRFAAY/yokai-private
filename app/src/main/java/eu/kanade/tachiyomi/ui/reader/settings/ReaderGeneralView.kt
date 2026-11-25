package eu.kanade.tachiyomi.ui.reader.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import eu.kanade.tachiyomi.data.database.models.orientationType
import eu.kanade.tachiyomi.data.database.models.readingModeType
import eu.kanade.tachiyomi.databinding.ReaderGeneralLayoutBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.bindToPreference
import eu.kanade.tachiyomi.util.lang.addBetaTag
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.widget.BaseReaderSettingsView
import yokai.util.lang.getString

class ReaderGeneralView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    BaseReaderSettingsView<ReaderGeneralLayoutBinding>(context, attrs) {

    lateinit var sheet: TabbedReaderSettingsSheet
    override fun inflateBinding() = ReaderGeneralLayoutBinding.bind(this)
    override fun initGeneralPreferences() {
        binding.viewerSeries.onItemSelectedListener = { position ->
            val readingModeType = ReadingModeType.fromSpinner(position)
            (context as ReaderActivity).viewModel.setMangaReadingMode(readingModeType.flagValue)

            val mangaViewer = activity.viewModel.getMangaReadingMode()
            if (mangaViewer == ReadingModeType.LONG_STRIP.flagValue || mangaViewer == ReadingModeType.CONTINUOUS_VERTICAL.flagValue) {
                initWebtoonPreferences()
            } else {
                initPagerPreferences()
            }
        }
        binding.viewerSeries.setSelection(
            (context as? ReaderActivity)?.viewModel?.state?.value?.manga?.readingModeType?.let {
                ReadingModeType.fromPreference(it).prefValue
            } ?: 0,
        )
        binding.rotationMode.onItemSelectedListener = { position ->
            val rotationType = OrientationType.fromSpinner(position)
            (context as ReaderActivity).viewModel.setMangaOrientationType(rotationType.flagValue)
        }
        binding.rotationMode.setSelection(
            (context as ReaderActivity).viewModel.manga?.orientationType?.let {
                OrientationType.fromPreference(it).prefValue
            } ?: 0,
        )

        binding.backgroundColor.setEntries(
            ReaderBackgroundColor.entries
                .map { context.getString(it.stringRes) },
        )
        val selection = ReaderBackgroundColor.indexFromPref(preferences.readerTheme().get())
        binding.backgroundColor.setSelection(selection)
        binding.backgroundColor.onItemSelectedListener = { position ->
            val backgroundColor = ReaderBackgroundColor.entries[position]
            preferences.readerTheme().set(backgroundColor.prefValue)
        }
        binding.showPageNumber.bindToPreference(preferences.showPageNumber())
        binding.fullscreen.bindToPreference(preferences.fullscreen()) {
            updatePrefs()
        }
        binding.cutoutShort.bindToPreference(readerPreferences.cutoutShort())
        binding.cutoutShort.text = binding.cutoutShort.text.toString().addBetaTag(context)
        binding.keepscreen.bindToPreference(preferences.keepScreenOn())
        binding.alwaysShowChapterTransition.bindToPreference(preferences.alwaysShowChapterTransition())

        // R: cards tinted with the theme color, like the owner's video
        val cardColor = ColorUtils.setAlphaComponent(
            context.getResourceColor(com.google.android.material.R.attr.colorSecondaryContainer),
            110,
        )
        for (i in 0 until binding.constraintLayout.childCount) {
            (binding.constraintLayout.getChildAt(i) as? MaterialCardView)?.setCardBackgroundColor(cardColor)
        }

        // R: tools / bar options
        val hideDelays = listOf(0, 3, 5, 8)
        RChoiceRow.bind(
            context, binding.rAutohide, RChoiceRow.CLOCK, hideDelays,
            listOf("أبدًا", "٣ ثواني", "٥ ثواني", "٨ ثواني"),
            hideDelays.indexOf(preferences.rAutoHideTools().get()).coerceAtLeast(0),
        ) { preferences.rAutoHideTools().set(hideDelays[it]) }
        binding.rClockBattery.bindToPreference(preferences.rClockBattery())
        binding.rProgressBar.bindToPreference(preferences.rProgressBar())
        binding.rVolumeKeys.bindToPreference(preferences.readWithVolumeKeys())
        binding.rVolumeInverted.bindToPreference(preferences.readWithVolumeKeysInverted())

        // R: tap / hold to scroll (same prefs as Settings > R > القارئ)
        binding.rTapScroll.bindToPreference(preferences.rTapScroll())
        binding.rHoldScroll.bindToPreference(preferences.rHoldScroll())
        RChoiceRow.bind(
            context, binding.rTapSide, RChoiceRow.SIDE, listOf(0, 1, 2),
            listOf("اليمين ينزّل", "اليسار ينزّل", "الاتنين ينزّلوا"),
            preferences.rTapScrollSide().get().coerceIn(0, 2),
        ) { preferences.rTapScrollSide().set(it) }
        val jumps = listOf(50, 70, 85, 100)
        RChoiceRow.bind(
            context, binding.rTapJump, RChoiceRow.JUMP, jumps,
            jumps.map { "$it% من الشاشة" },
            jumps.indexOf(preferences.rTapJump().get()).coerceAtLeast(0),
        ) { preferences.rTapJump().set(jumps[it]) }
        val speeds = listOf(5, 10, 15, 25)
        RChoiceRow.bind(
            context, binding.rHoldSpeed, RChoiceRow.SPEED, listOf(1, 2, 3, 4),
            listOf("بطيئة", "عادية", "سريعة", "أسرع"),
            speeds.indexOf(preferences.rHoldScrollSpeed().get()).coerceAtLeast(0),
        ) { preferences.rHoldScrollSpeed().set(speeds[it]) }

        updatePrefs()
    }

    /** R: fills a segmented button row with [labels], checks [selected] and reports taps. */
    private fun bindChoices(group: MaterialButtonToggleGroup, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        group.removeAllViews()
        val ids = labels.map { label ->
            MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }.also { group.addView(it) }.id
        }
        group.check(ids[selected.coerceIn(0, ids.lastIndex)])
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) ids.indexOf(checkedId).takeIf { it >= 0 }?.let(onSelect)
        }
    }

    /**
     * Init the preferences for the webtoon reader.
     */
    private fun initWebtoonPreferences() {
        sheet.updateTabs(true)
    }

    private fun initPagerPreferences() {
        sheet.updateTabs(false)
    }

    private fun updatePrefs() {
        binding.cutoutShort.isVisible =
            DeviceUtil.hasCutout(context as ReaderActivity).ordinal >= DeviceUtil.CutoutSupport.MODERN.ordinal && preferences.fullscreen().get()
    }
}
