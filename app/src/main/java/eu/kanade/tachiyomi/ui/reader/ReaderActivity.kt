package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.assist.AssistContent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.transition.addListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import co.touchlab.kermit.Logger
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.preference.toggle
import eu.kanade.tachiyomi.data.coil.TachiyomiImageDecoder
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.orientationType
import eu.kanade.tachiyomi.data.database.models.readingModeType
import eu.kanade.tachiyomi.data.preference.changesIn
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.databinding.ReaderActivityBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.ui.reader.settings.PageLayout
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.settings.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.settings.TabbedReaderSettingsSheet
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.L2RPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.VerticalPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.preferredChapterName
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.DeviceUtil.LegacyCutoutMode
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.e
import eu.kanade.tachiyomi.util.system.getBottomGestureInsets
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.hasSideNavBar
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.isBottomTappable
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchNonCancellableIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.spToPx
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.compatToolTipText
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.popupMenu
import eu.kanade.tachiyomi.util.view.setAction
import eu.kanade.tachiyomi.util.view.setMessage
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.widget.doOnEnd
import eu.kanade.tachiyomi.widget.doOnStart
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import yokai.domain.base.BasePreferences
import yokai.domain.ui.settings.ReaderPreferences
import yokai.domain.ui.settings.ReaderPreferences.LandscapeCutoutBehaviour
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

/**
 * Activity containing the reader of Tachiyomi. This activity is mostly a container of the
 * viewers, to which calls from the view model or UI events are delegated.
 */
class ReaderActivity : BaseActivity<ReaderActivityBinding>() {

    val viewModel by viewModels<ReaderViewModel>()

    val scope = lifecycleScope

    /**
     * Viewer used to display the pages (pager, webtoon, ...).
     */
    var viewer: BaseViewer? = null

    /** R: the cobra overlay (see [RReaderCobraView]). */
    private var rCobra: RReaderCobraView? = null
        private set

    /**
     * Whether the menu is currently visible.
     */
    var menuVisible = false
        private set

    /**
     * Whether the menu should stay visible.
     */
    private var menuTemporarilyVisible = false

    private var coroutine: Job? = null
    private var rAutoHideJob: Job? = null
    private var rClockJob: Job? = null
    private var rBatteryPercent = -1
    private var rCharging = false
    private val rClockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra("level", -1)
                val scale = intent.getIntExtra("scale", 100)
                if (level >= 0 && scale > 0) rBatteryPercent = level * 100 / scale
                rCharging = intent.getIntExtra("plugged", 0) != 0
            }
            rUpdateClock()
        }
    }

    private var fromUrl = false

    /**
     * Configuration at reader level, like background color or forced orientation.
     */
    private var config: ReaderConfig? = null

    /**
     * Current Bottom Sheet on display, used to dismiss
     */
    private var bottomSheet: BottomSheetDialog? = null

    var sheetManageNavColor = false

    private val wic by lazy { WindowInsetsControllerCompat(window, binding.root) }
    private var lastVis = false

    private var snackbar: Snackbar? = null

    private var intentPageNumber: Int? = null

    var isLoading = false

    private var lastShiftDoubleState: Boolean? = null
    private var indexPageToShift: Int? = null
    private var indexChapterToShift: Long? = null

    private var lastCropRes = 0
    var manuallyShiftedPages = false
        private set

    val isSplitScreen: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode

    private var didTransitionFromChapter = false
    private var visibleChapterRange = longArrayOf()
    private var backPressedCallback: OnBackPressedCallback? = null

    var isScrollingThroughPagesOrChapters = false
    private var hingeGapSize = 0
        set(value) {
            field = value
            (viewer as? PagerViewer)?.config?.hingeGapSize = value
        }

    private val readerPreferences: ReaderPreferences by injectLazy()
    private val basePreferences: BasePreferences by injectLazy()

    companion object {

        const val SHIFT_DOUBLE_PAGES = "shiftingDoublePages"
        const val SHIFTED_PAGE_INDEX = "shiftedPageIndex"
        const val SHIFTED_CHAP_INDEX = "shiftedChapterIndex"

        const val TRANSITION_NAME = "${BuildConfig.APPLICATION_ID}.TRANSITION_NAME"
        const val VISIBLE_CHAPTERS = "${BuildConfig.APPLICATION_ID}.VISIBLE_CHAPTERS"

        fun newIntent(context: Context, manga: Manga, chapter: Chapter): Intent {
            MainActivity.chapterIdToExitTo = 0L
            val intent = Intent(context, ReaderActivity::class.java)
            intent.putExtra("manga", manga.id)
            intent.putExtra("chapter", chapter.id)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }

        fun newIntentWithTransitionOptions(activity: Activity, manga: Manga, chapter: Chapter, sharedElement: View): Pair<Intent, Bundle?> {
            MainActivity.chapterIdToExitTo = 0L
            val intent = newIntent(activity, manga, chapter)
            intent.putExtra(TRANSITION_NAME, sharedElement.transitionName)
            val activityOptions = ActivityOptions.makeSceneTransitionAnimation(
                activity,
                sharedElement,
                sharedElement.transitionName,
            )
            return intent to activityOptions.toBundle()
        }
    }

    /**
     * Called when the activity is created. Initializes the view model and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = maybeInstallSplashScreen(savedInstanceState)

        // Setup shared element transitions
        if (intent.extras?.getString(TRANSITION_NAME) != null) {
            window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
            findViewById<View>(AR.id.content)?.let { contentView ->
                MainActivity.chapterIdToExitTo = 0L
                contentView.transitionName = intent.extras?.getString(TRANSITION_NAME)
                visibleChapterRange = intent.extras?.getLongArray(VISIBLE_CHAPTERS) ?: longArrayOf()
                didTransitionFromChapter = contentView.transitionName.contains("details chapter")
                setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
                window.sharedElementEnterTransition = buildContainerTransform(true)
                window.sharedElementReturnTransition = buildContainerTransform(false)
                // Postpone custom transition until manga ready
                postponeEnterTransition()
            }
        }

        super.onCreate(savedInstanceState)
        binding = ReaderActivityBinding.inflate(layoutInflater)
        setupReaderNav()
        setContentView(binding.root)

        // R: green cobra that peeks in from the left edge while reading (Settings > R > reader cobra).
        // It is decoration only: if anything in it fails, the reader must still open.
        try {
            rCobra = RReaderCobraView(
                this,
                enabled = { preferences.rReaderCobra().get() },
                canShow = { !menuVisible && viewer != null && !isFinishing && !isDestroyed },
                soundOn = { preferences.rReaderCobraSound().get() },
                nightOn = { preferences.rReaderCobraNight().get() },
                intervalMinutes = { preferences.rReaderCobraInterval().get() },
                chaptersEvery = { preferences.rReaderCobraChapters().get() },
                cooldownMinutes = { preferences.rReaderCobraCooldown().get() },
                sceneOn = { preferences.rReaderCobraScene().get() },
                sceneEvery = { preferences.rReaderCobraSceneEvery().get() },
                sceneV2On = { preferences.rSceneV2().get() },
                sizeDp = { preferences.rDragonSize().get() },
            ).also { view ->
                binding.root.addView(
                    view,
                    CoordinatorLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        } catch (e: Throwable) {
            android.util.Log.e("YokaiR", "reader cobra failed to start, continuing without it", e)
            rCobra = null
        }

        val a = obtainStyledAttributes(intArrayOf(AR.attr.windowLightStatusBar))
        val lightStatusBar = a.getBoolean(0, false)
        a.recycle()
        setCutoutMode()

        splashScreen?.configure()

        wic.isAppearanceLightStatusBars = lightStatusBar
        wic.isAppearanceLightNavigationBars = lightStatusBar

        binding.appBar.setBackgroundColor(contextCompatColor(R.color.surface_alpha))
        ViewCompat.setBackgroundTintList(
            readerNav.root,
            ColorStateList.valueOf(contextCompatColor(R.color.surface_alpha)),
        )

        backPressedCallback = object : OnBackPressedCallback(enabled = true) {
            override fun handleOnBackPressed() {
                if (binding.chaptersSheet.root.sheetBehavior.isExpanded()) {
                    binding.chaptersSheet.root.lastScale = binding.chaptersSheet.root.scaleX
                    binding.chaptersSheet.root.sheetBehavior?.collapse()
                }
                reEnableBackPressedCallBack()
            }

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                if (binding.chaptersSheet.root.sheetBehavior.isExpanded()) {
                    binding.chaptersSheet.root.sheetBehavior?.startBackProgress(backEvent)
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (binding.chaptersSheet.root.sheetBehavior.isExpanded()) {
                    binding.chaptersSheet.root.sheetBehavior?.updateBackProgress(backEvent)
                }
            }

            override fun handleOnBackCancelled() {
                if (binding.chaptersSheet.root.sheetBehavior.isExpanded()) {
                    binding.chaptersSheet.root.sheetBehavior?.cancelBackProgress()
                }
            }
        }
        onBackPressedDispatcher.addCallback(backPressedCallback!!)
        if (viewModel.needsInit()) {
            fromUrl = handleIntentAction(intent)
            if (!fromUrl) {
                val manga = intent.extras?.getLong("manga", -1L) ?: -1L
                val chapter = intent.extras?.getLong("chapter", -1L) ?: -1L
                if (manga == -1L || chapter == -1L) {
                    finish()
                    return
                }
                lifecycleScope.launchNonCancellableIO {
                    val initResult = viewModel.init(manga, chapter)
                    if (!initResult.getOrDefault(false)) {
                        val exception = initResult.exceptionOrNull() ?: IllegalStateException("Unknown err")
                        withUIContext {
                            setInitialChapterError(exception)
                        }
                    }
                }
            } else {
                binding.pleaseWait.isVisible = true
            }
        }

        if (savedInstanceState != null) {
            menuVisible = savedInstanceState.getBoolean(::menuVisible.name)
            lastShiftDoubleState = savedInstanceState.getBoolean(SHIFT_DOUBLE_PAGES)
                .takeIf { savedInstanceState.containsKey(SHIFT_DOUBLE_PAGES) }
            indexPageToShift = savedInstanceState.getInt(SHIFTED_PAGE_INDEX, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }
            indexChapterToShift = savedInstanceState.getLong(SHIFTED_CHAP_INDEX, Long.MIN_VALUE)
                .takeIf { it != Long.MIN_VALUE }
            readerNav.root.isInvisible = !menuVisible
        } else {
            readerNav.root.isInvisible = true
        }

        binding.chaptersSheet.chaptersBottomSheet.setup(this)
        rSetupToolsBar()
        rStyleClock()
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> rPositionClock() }
        // R: the original page bar is only shown when its switch is on (Settings > R); the new tools card replaces it
        if (preferences.rSideTools().get() && verticalNav) {
            rSetupSideTools()
        } else if (!preferences.rOldToolbar().get()) {
            binding.navLayout.visibility = View.GONE
        }
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateNavHeight() }
        config = ReaderConfig()
        initializeMenu()

        preferences.incognitoMode()
            .changesIn(lifecycleScope) {
                SecureActivityDelegate.setSecure(this)
            }
        reEnableBackPressedCallBack()

        viewModel.state
            .map { it.isLoadingAdjacentChapter }
            .distinctUntilChanged()
            .onEach(::setProgressDialog)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.manga }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setManga)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.viewerChapters }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setChapters)
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    ReaderViewModel.Event.ReloadMangaAndChapters -> {
                        viewModel.manga?.let(::setManga)
                        viewModel.state.value.viewerChapters?.let(::setChapters)
                    }
                    ReaderViewModel.Event.ReloadViewerChapters -> {
                        viewModel.state.value.viewerChapters?.let(::setChapters)
                    }
                    is ReaderViewModel.Event.SetOrientation -> {
                        setOrientation(event.orientation)
                    }
                    is ReaderViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is ReaderViewModel.Event.ShareImage -> {
                        onShareImageResult(event.file, event.page)
                    }
                    is ReaderViewModel.Event.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                    is ReaderViewModel.Event.ShareTrackingError -> {
                        showTrackingError(event.errors)
                    }
                }
            }
            .launchIn(lifecycleScope)

        lifecycleScope.launchUI {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@ReaderActivity).windowLayoutInfo(this@ReaderActivity)
                    .collect { newLayoutInfo ->
                        hingeGapSize = 0
                        for (displayFeature: DisplayFeature in newLayoutInfo.displayFeatures) {
                            if (displayFeature is FoldingFeature && displayFeature.occlusionType == FoldingFeature.OcclusionType.FULL &&
                                displayFeature.isSeparating && displayFeature.orientation == FoldingFeature.Orientation.VERTICAL
                            ) {
                                hingeGapSize = displayFeature.bounds.width()
                            }
                        }
                        if (hingeGapSize > 0) {
                            binding.chaptersSheet.root.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                                gravity = Gravity.END
                                width = (binding.root.width - hingeGapSize) / 2
                            }
                            binding.pleaseWait.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                                marginStart = binding.root.width / 2 + hingeGapSize
                            }
                            if (!verticalNav) {
                                binding.navLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                                    gravity = Gravity.TOP or Gravity.CENTER
                                    anchorGravity = Gravity.TOP or Gravity.CENTER
                                    width = (binding.root.width - hingeGapSize) / 2 - 24.dpToPx
                                }
                            }
                        }
                    }
            }
        }
    }

    /**
     * Called when the activity is destroyed. Cleans up the viewer, configuration and any view.
     */
    override fun onDestroy() {
        super.onDestroy()
        viewer?.destroy()
        binding.chaptersSheet.chaptersBottomSheet.adapter = null
        viewer = null
        config = null
        bottomSheet?.dismiss()
        bottomSheet = null
        snackbar?.dismiss()
        snackbar = null
    }

    /**
     * Called when the activity is saving instance state. Current progress is persisted if this
     * activity isn't changing configurations.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(::menuVisible.name, menuVisible)
        (viewer as? PagerViewer)?.let { pViewer ->
            val config = pViewer.config
            if (config.doublePages) {
                outState.putBoolean(SHIFT_DOUBLE_PAGES, config.shiftDoublePage)
            }
            if (config.shiftDoublePage && config.doublePages) {
                pViewer.getShiftedPage()?.let {
                    outState.putInt(SHIFTED_PAGE_INDEX, it.index)
                    outState.putLong(SHIFTED_CHAP_INDEX, it.chapter.chapter.id ?: 0L)
                }
            }
        }
        super.onSaveInstanceState(outState)
    }

    /**
     * Called when the options menu of the binding.toolbar is being created. It adds our custom menu.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.reader, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val splitItem = menu.findItem(R.id.action_shift_double_page)
        splitItem?.isVisible = ((viewer as? PagerViewer)?.config?.doublePages ?: false) && !canShowSplitAtBottom()
        binding.chaptersSheet.shiftPageButton.isVisible = ((viewer as? PagerViewer)?.config?.doublePages ?: false) && canShowSplitAtBottom()
        (viewer as? PagerViewer)?.config?.let { config ->
            val icon = ContextCompat.getDrawable(
                this,
                if ((!config.shiftDoublePage).xor(viewer is R2LPagerViewer)) R.drawable.ic_page_previous_outline_24dp else R.drawable.ic_page_next_outline_24dp,
            )
            splitItem?.icon = icon
            binding.chaptersSheet.shiftPageButton.setImageDrawable(icon)
        }
        setBottomNavButtons(preferences.pageLayout().get())
        (binding.toolbar.background as? LayerDrawable)?.let { layerDrawable ->
            val isDoublePage = splitItem?.isVisible ?: false
            // Shout out to Google for not fixing setVisible https://issuetracker.google.com/issues/127538945
            layerDrawable.findDrawableByLayerId(R.id.layer_full_width).alpha = if (!isDoublePage) 255 else 0
            layerDrawable.findDrawableByLayerId(R.id.layer_one_item).alpha = if (isDoublePage) 255 else 0
        }
        return super.onPrepareOptionsMenu(menu)
    }

    private fun canShowSplitAtBottom(): Boolean {
        return if (!preferences.readerBottomButtons().isSet()) {
            isTablet()
        } else {
            ReaderBottomButton.ShiftDoublePage.isIn(preferences.readerBottomButtons().get())
        }
    }

    fun setBottomNavButtons(pageLayout: Int) {
        val isDoublePage = pageLayout == PageLayout.DOUBLE_PAGES.value ||
            (pageLayout == PageLayout.AUTOMATIC.value && (viewer as? PagerViewer)?.config?.doublePages ?: false)
        binding.chaptersSheet.doublePage.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                when {
                    isDoublePage -> R.drawable.ic_book_open_variant_24dp
                    (viewer as? PagerViewer)?.config?.splitPages == true -> R.drawable.ic_book_open_split_24dp
                    else -> R.drawable.ic_single_page_24dp
                },
            ),
        )
        with(readerNav) {
            listOf(leftPageText, rightPageText).forEach {
                it.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    val isCurrent = (viewer is R2LPagerViewer).xor(it === leftPageText)
                    width = if (isDoublePage && isCurrent) 48.spToPx else 32.spToPx
                }
            }
        }
    }

    /** The page bar in use: the vertical one on the right (R switch r_vertical_nav) or the original horizontal one. */
    lateinit var readerNav: ReaderNavViews
        private set
    private var verticalNav = false

    private fun setupReaderNav() {
        verticalNav = preferences.rVerticalNav().get()
        if (verticalNav) {
            try {
                readerNav = ReaderNavViews.of(binding.readerNavVertical)
                readerNav.pageSeekbar.vertical = true
                binding.readerNav.root.visibility = View.GONE
                binding.readerNavVertical.root.visibility = View.VISIBLE
                binding.navLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                    height = 380.dpToPx
                    gravity = Gravity.TOP or Gravity.RIGHT
                }
                return
            } catch (e: Throwable) {
                // R: never let the new bar stop the reader from opening: go back to the original bar
                android.util.Log.e("YokaiR", "vertical page bar failed, using the original bar", e)
                verticalNav = false
                binding.readerNavVertical.root.visibility = View.GONE
                binding.readerNav.root.visibility = View.VISIBLE
                binding.navLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    gravity = Gravity.TOP
                }
            }
        }
        readerNav = ReaderNavViews.of(binding.readerNav)
    }

    /**
     * The vertical page bar fills the space between the top bar and the bottom toolbar,
     * but never gets taller than 400dp (or shorter than 150dp).
     */
    private fun updateNavHeight() {
        if (!verticalNav) return
        val rootHeight = binding.root.height
        if (rootHeight <= 0) return
        val peek = binding.chaptersSheet.root.sheetBehavior?.peekHeight ?: 0
        val topLimit = binding.appBar.height.takeIf { it > 0 } ?: 80.dpToPx
        val available = rootHeight - peek - topLimit - 24.dpToPx
        val target = available.coerceIn(150.dpToPx, 400.dpToPx)
        if (binding.navLayout.layoutParams.height != target) {
            binding.navLayout.updateLayoutParams { height = target }
        }
    }

    private fun updateOrientationShortcut(preference: Int) {
        val orientation = OrientationType.fromPreference(preference)
        binding.chaptersSheet.rotationSheetButton.setImageResource(orientation.iconRes)
    }

    private fun updateCropBordersShortcut() {
        val isPagerType = viewer is PagerViewer || (viewer as? WebtoonViewer)?.hasMargins == true
        val enabled = if (isPagerType) {
            preferences.cropBorders().get()
        } else {
            preferences.cropBordersWebtoon().get()
        }

        with(binding.chaptersSheet.cropBordersSheetButton) {
            val drawableRes = if (enabled) {
                R.drawable.anim_free_to_crop
            } else {
                R.drawable.anim_crop_to_free
            }
            if (lastCropRes != drawableRes) {
                val drawable = AnimatedVectorDrawableCompat.create(context, drawableRes)
                setImageDrawable(drawable)
                drawable?.start()
                lastCropRes = drawableRes
            }
            compatToolTipText =
                getString(
                    if (enabled) {
                        MR.strings.remove_crop
                    } else {
                        MR.strings.crop_borders
                    },
                )
        }
    }

    private fun updateBottomShortcuts() {
        val enabledButtons = preferences.readerBottomButtons().get()
        with(binding.chaptersSheet) {
            readingMode.isVisible = ReaderBottomButton.ReadingMode.isIn(enabledButtons)
            rotationSheetButton.isVisible =
                ReaderBottomButton.Rotation.isIn(enabledButtons)
            doublePage.isVisible = viewer is PagerViewer &&
                ReaderBottomButton.PageLayout.isIn(enabledButtons)
            cropBordersSheetButton.isVisible =
                if (viewer is PagerViewer) {
                    ReaderBottomButton.CropBordersPaged.isIn(enabledButtons)
                } else {
                    ReaderBottomButton.CropBordersWebtoon.isIn(enabledButtons)
                }
            webviewButton.isVisible =
                ReaderBottomButton.WebView.isIn(enabledButtons)
            chaptersButton.isVisible =
                ReaderBottomButton.ViewChapters.isIn(enabledButtons)
            shiftPageButton.isVisible =
                ((viewer as? PagerViewer)?.config?.doublePages ?: false) && canShowSplitAtBottom()
            binding.toolbar.menu.findItem(R.id.action_shift_double_page)?.isVisible =
                ((viewer as? PagerViewer)?.config?.doublePages ?: false) && !canShowSplitAtBottom()
        }
    }

    /**
     * Called when an item of the options menu was clicked. Used to handle clicks on our menu
     * entries.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_r_chapter_note -> rShowChapterNote()
            R.id.action_shift_double_page -> {
                shiftDoublePages()
                manuallyShiftedPages = true
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    fun shiftDoublePages(forceShift: Boolean? = null, page: ReaderPage? = null) {
        (viewer as? PagerViewer)?.let { pViewer ->
            if (forceShift == pViewer.config.shiftDoublePage) return
            pViewer.config.shiftDoublePage = !pViewer.config.shiftDoublePage
            viewModel.state.value.viewerChapters?.let {
                pViewer.updateShifting(page)
                pViewer.setChaptersDoubleShift(it)
                invalidateOptionsMenu()
            }
        }
    }

    private fun popToMain() {
        if (fromUrl) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finishAfterTransition()
        } else {
            backPressedCallback?.isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    fun reEnableBackPressedCallBack() {
        backPressedCallback?.isEnabled = binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isExpanded()
    }

    override fun finishAfterTransition() {
        if (didTransitionFromChapter && visibleChapterRange.isNotEmpty() && MainActivity.chapterIdToExitTo !in visibleChapterRange) {
            finish()
        } else {
            viewModel.onBackPressed()
            super.finishAfterTransition()
        }
    }

    override fun finish() {
        viewModel.onBackPressed()
        super.finish()
    }

    /**
     * Dispatches a key event. If the viewer doesn't handle it, call the default implementation.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = viewer?.handleKeyEvent(event) ?: false
        return handled || super.dispatchKeyEvent(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_N -> {
                if (viewer is R2LPagerViewer) {
                    readerNav.leftChapter.performClick()
                } else {
                    readerNav.rightChapter.performClick()
                }
                return true
            }
            KeyEvent.KEYCODE_P -> {
                if (viewer !is R2LPagerViewer) {
                    readerNav.leftChapter.performClick()
                } else {
                    readerNav.rightChapter.performClick()
                }
                return true
            }
            KeyEvent.KEYCODE_L -> {
                readerNav.leftChapter.performClick()
                return true
            }
            KeyEvent.KEYCODE_R -> {
                readerNav.rightChapter.performClick()
                return true
            }
            KeyEvent.KEYCODE_E -> {
                viewer?.moveToNext()
                return true
            }
            KeyEvent.KEYCODE_Q -> {
                viewer?.moveToPrevious()
                return true
            }
            else -> return super.onKeyUp(keyCode, event)
        }
    }

    /**
     * Dispatches a generic motion event. If the viewer doesn't handle it, call the default
     * implementation.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = viewer?.handleGenericMotionEvent(event) ?: false
        return handled || super.dispatchGenericMotionEvent(event)
    }

    private fun buildContainerTransform(entering: Boolean): MaterialContainerTransform {
        return MaterialContainerTransform(this, entering).apply {
            duration = (
                resources?.getInteger(
                    if (entering) {
                        AR.integer.config_longAnimTime
                    } else {
                        AR.integer.config_mediumAnimTime
                    },
                ) ?: 500
                ).toLong()
            addTarget(AR.id.content)
        }
    }

    /**
     * Initializes the reader menu. It sets up click listeners and the initial visibility.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initializeMenu() {
        // Set binding.toolbar
        setSupportActionBar(binding.toolbar)
        val primaryColor = ColorUtils.setAlphaComponent(
            getResourceColor(R.attr.colorSurface),
            200,
        )
        binding.appBar.setBackgroundColor(primaryColor)
        window.statusBarColor = Color.TRANSPARENT
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.navigationIcon?.setTint(getResourceColor(R.attr.actionBarTintColor))
        binding.toolbar.setNavigationOnClickListener {
            popToMain()
        }

        binding.toolbar.setOnClickListener {
            // R: one tap = one details screen (repeated taps while it loads used to stack several heavy activities and hang)
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - rLastDetailsTap < 2500) return@setOnClickListener
            rLastDetailsTap = now
            viewModel.manga?.id?.let { id ->
                // R: the manga page that opened this reader is still right behind it: going back to it is instant
                if (intent.getBooleanExtra("r_from_details", false)) {
                    onBackPressedDispatcher.onBackPressed()
                    return@let
                }
                val intent = SearchActivity.openMangaIntent(this, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                startActivity(intent)
                overridePendingTransition(0, 0)
            }
        }

        with(binding.chaptersSheet) {
            with(doublePage) {
                compatToolTipText = getString(MR.strings.page_layout)
                setOnClickListener {
                    if (preferences.pageLayout().get() == PageLayout.AUTOMATIC.value) {
                        (viewer as? PagerViewer)?.config?.let { config ->
                            config.doublePages = !config.doublePages
                            reloadChapters(config.doublePages, true)
                        }
                    } else {
                        showPageLayoutMenu()
                    }
                }
                setOnLongClickListener {
                    showPageLayoutMenu()
                    true
                }
            }
            cropBordersSheetButton.setOnClickListener {
                val pref =
                    if ((viewer as? WebtoonViewer)?.hasMargins == true ||
                        (viewer is PagerViewer)
                    ) {
                        preferences.cropBorders()
                    } else {
                        preferences.cropBordersWebtoon()
                    }
                pref.toggle()
            }

            with(rotationSheetButton) {
                compatToolTipText = getString(MR.strings.rotation)

                setOnClickListener {
                    popupMenu(
                        items = OrientationType.entries.map { it.flagValue to it.stringRes },
                        selectedItemId = viewModel.manga?.orientationType
                            ?: preferences.defaultOrientationType().get(),
                    ) {
                        val newOrientation = OrientationType.fromPreference(itemId)

                        viewModel.setMangaOrientationType(newOrientation.flagValue)

                        updateOrientationShortcut(newOrientation.flagValue)
                    }
                }
            }

            webviewButton.setOnClickListener {
                openMangaInBrowser()
            }

            displayOptions.setOnClickListener {
                TabbedReaderSettingsSheet(this@ReaderActivity).show()
            }

            displayOptions.setOnLongClickListener {
                TabbedReaderSettingsSheet(this@ReaderActivity, true).show()
                true
            }

            readingMode.setOnClickListener { readingMode ->
                readingMode.popupMenu(
                    items = ReadingModeType.entries.map { it.flagValue to it.stringRes },
                    selectedItemId = viewModel.manga?.readingModeType,
                ) {
                    viewModel.setMangaReadingMode(itemId)
                }
            }
        }

        listOf(preferences.cropBorders(), preferences.cropBordersWebtoon())
            .forEach { pref ->
                pref.changes()
                    .onEach { updateCropBordersShortcut() }
                    .launchIn(scope)
            }

        binding.chaptersSheet.shiftPageButton.setOnClickListener {
            shiftDoublePages()
            manuallyShiftedPages = true
        }

        readerNav.leftChapter.setOnClickListener { loadAdjacentChapter(false) }
        readerNav.rightChapter.setOnClickListener { loadAdjacentChapter(true) }

        binding.touchView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isExpanded()) {
                    binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.collapse()
                }
            }
            false
        }
        val readerNavGestureDetector = ReaderNavGestureDetector(this)
        val gestureDetector = GestureDetector(this, readerNavGestureDetector)
        with(readerNav) {
            readerNav.pageSeekbar.addOnSliderTouchListener(
                object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {
                        readerNavGestureDetector.lockVertical = false
                        readerNavGestureDetector.hasScrollHorizontal = true
                        isScrollingThroughPagesOrChapters = true
                    }

                    override fun onStopTrackingTouch(slider: Slider) {
                        isScrollingThroughPagesOrChapters = false
                    }
                },
            )
            listOf(root, leftChapter, rightChapter, pageSeekbar).forEach {
                it.setOnTouchListener { _, event ->
                    val result = gestureDetector.onTouchEvent(event)
                    if (event?.action == MotionEvent.ACTION_UP) {
                        if (!result) {
                            val sheetBehavior = binding.chaptersSheet.root.sheetBehavior
                            if (sheetBehavior?.state != BottomSheetBehavior.STATE_SETTLING && !sheetBehavior.isCollapsed()) {
                                sheetBehavior?.collapse()
                            }
                        }
                        if (readerNavGestureDetector.lockVertical) {
                            return@setOnTouchListener true
                        }
                    } else if ((event?.action != MotionEvent.ACTION_UP || event.action != MotionEvent.ACTION_DOWN) && result) {
                        event.action = MotionEvent.ACTION_CANCEL
                        return@setOnTouchListener false
                    }
                    if (it == pageSeekbar) {
                        readerNavGestureDetector.lockVertical
                    } else {
                        result
                    }
                }
            }
        }

        // Init listeners on bottom menu
        readerNav.pageSeekbar.addOnChangeListener { _, value, fromUser ->
            if (viewer != null && fromUser) {
                val prevValue = (viewer as? PagerViewer)?.pager?.currentItem ?: -1
                moveToPageIndex(value.roundToInt())
                val newValue = (viewer as? PagerViewer)?.pager?.currentItem ?: -1
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                    ((prevValue > -1 && newValue != prevValue) || viewer !is PagerViewer)
                ) {
                    readerNav.pageSeekbar.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                }
            }
        }

        readerNav.pageSeekbar.setLabelFormatter { value ->
            val pageNumber = (value + 1).roundToInt()
            (viewer as? PagerViewer)?.let {
                if (it.config.doublePages || it.config.splitPages) {
                    if (it.hasExtraPage(value.roundToInt(), viewModel.getCurrentChapter())) {
                        val invertDoublePage = (viewer as? PagerViewer)?.config?.invertDoublePages ?: false
                        return@setLabelFormatter if (!readerNav.pageSeekbar.isRTL.xor(invertDoublePage)) {
                            "$pageNumber-${pageNumber + 1}"
                        } else {
                            "${pageNumber + 1}-$pageNumber"
                        }
                    }
                }
            }
            pageNumber.toString()
        }

        // Set initial visibility
        setMenuVisibility(menuVisible, false)
        binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.isHideable = !menuVisible
        if (!menuVisible) binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.hide()
        binding.chaptersSheet.root.sheetBehavior?.isGestureInsetBottomIgnored = true
        val peek = 50.dpToPx
        lastVis = window.decorView.rootWindowInsetsCompat?.isVisible(statusBars()) ?: false
        var firstPass = true
        binding.readerLayout.doOnApplyWindowInsetsCompat { _, insets, _ ->
            setNavColor(insets)
            val systemInsets = insets.ignoredSystemInsets
            val currentOrientation = resources.configuration.orientation
            val isLandscapeFully = currentOrientation == Configuration.ORIENTATION_LANDSCAPE && readerPreferences.landscapeCutoutBehavior().get() == LandscapeCutoutBehaviour.DEFAULT
            val cutOutInsets = if (isLandscapeFully) insets.displayCutout else null
            val vis = insets.isVisible(statusBars())
            val fullscreen = preferences.fullscreen().get() && !isSplitScreen
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!firstPass && lastVis != vis && fullscreen) {
                    onVisibilityChange(vis)
                }
                firstPass = false
                lastVis = vis
            }
            wic.systemBarsBehavior = if (preferences.rHideNavButtons().get()) {
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
            if (!fullscreen && sheetManageNavColor) {
                window.navigationBarColor = getResourceColor(R.attr.colorSurface)
            }
            binding.appBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemInsets.left
                rightMargin = systemInsets.right
            }
            binding.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemInsets.top
            }
            binding.chaptersSheet.chaptersBottomSheet.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemInsets.left
                rightMargin = systemInsets.right
                height = 280.dpToPx + systemInsets.bottom
            }
            binding.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = cutOutInsets?.safeInsetLeft ?: 0
                rightMargin = cutOutInsets?.safeInsetRight ?: 0
            }
            binding.chaptersSheet.topbarLayout.updatePadding(
                left = cutOutInsets?.safeInsetLeft ?: 0,
                right = cutOutInsets?.safeInsetRight ?: 0,
            )
            binding.chaptersSheet.chapterRecycler.updatePadding(
                left = cutOutInsets?.safeInsetLeft ?: 0,
                right = cutOutInsets?.safeInsetRight ?: 0,
            )
            binding.navLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = 12.dpToPx + max(systemInsets.left, cutOutInsets?.safeInsetLeft ?: 0)
                rightMargin = 12.dpToPx + max(systemInsets.right, cutOutInsets?.safeInsetRight ?: 0)
            }
            // R: when the old bottom toolbar is hidden its 50dp of empty space is dropped, so the tools card and the page bar sit lower
            binding.chaptersSheet.root.sheetBehavior?.peekHeight =
                (if (rOldSheetHidden) 4.dpToPx else peek) + if (fullscreen) {
                insets.getBottomGestureInsets()
            } else {
                val rootInsets = binding.root.rootWindowInsetsCompat ?: insets
                max(
                    0,
                    (rootInsets.getBottomGestureInsets()) -
                        rootInsets.getInsetsIgnoringVisibility(systemBars()).bottom,
                )
            }
            binding.chaptersSheet.chapterRecycler.updatePaddingRelative(bottom = systemInsets.bottom)
            binding.viewerContainer.requestLayout()
            updateNavHeight()
            binding.root.post { rPositionToolsBar() }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            binding.readerLayout.setOnSystemUiVisibilityChangeListener {
                if (preferences.fullscreen().get()) {
                    onVisibilityChange((it and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0)
                }
            }
        }
    }

    private fun loadAdjacentChapter(rightButton: Boolean) {
        if (isLoading) {
            return
        }
        isScrollingThroughPagesOrChapters = true
        lifecycleScope.launch {
            val getNextChapter = (viewer is R2LPagerViewer).xor(rightButton)
            val adjChapter = viewModel.adjacentChapter(getNextChapter)
            if (adjChapter != null) {
                if (rightButton) {
                    readerNav.rightChapter.isInvisible = true
                    readerNav.rightProgress.isVisible = true
                } else {
                    readerNav.leftChapter.isInvisible = true
                    readerNav.leftProgress.isVisible = true
                }
                loadChapter(adjChapter)
            } else {
                toast(
                    if (getNextChapter) {
                        MR.strings.theres_no_next_chapter
                    } else {
                        MR.strings.theres_no_previous_chapter
                    },
                )
            }
        }
    }

    suspend fun loadChapter(chapter: Chapter) {
        loadChapter(ReaderChapter(chapter))
    }

    private suspend fun loadChapter(chapter: ReaderChapter) {
        val lastPage = viewModel.loadChapter(chapter) ?: return
        scope.launchUI {
            moveToPageIndex(lastPage, false, chapterChange = true)
        }
        refreshChapters()
    }

    fun setNavColor(insets: WindowInsetsCompat) {
        sheetManageNavColor = when {
            isSplitScreen -> {
                window.statusBarColor = getResourceColor(R.attr.colorPrimaryVariant)
                window.navigationBarColor = getResourceColor(R.attr.colorPrimaryVariant)
                false
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 -> {
                // basically if in landscape on a phone
                // For lollipop, draw opaque nav bar
                window.navigationBarColor = when {
                    insets.hasSideNavBar() -> Color.BLACK
                    isInNightMode() -> {
                        ColorUtils.setAlphaComponent(
                            getResourceColor(R.attr.colorPrimaryVariant),
                            179,
                        )
                    }
                    else -> Color.argb(179, 0, 0, 0)
                }
                !insets.hasSideNavBar()
            }
            insets.isBottomTappable() -> {
                window.navigationBarColor = Color.TRANSPARENT
                false
            }
            insets.hasSideNavBar() -> {
                window.navigationBarColor = getResourceColor(R.attr.colorSurface)
                false
            }
            // if in portrait with 2/3 button mode, translucent nav bar
            else -> {
                true
            }
        }
    }

    private fun showPageLayoutMenu() {
        with(binding.chaptersSheet.doublePage) {
            val config = (viewer as? PagerViewer)?.config
            val selectedId = when {
                config?.doublePages == true -> PageLayout.DOUBLE_PAGES
                config?.splitPages == true -> PageLayout.SPLIT_PAGES
                else -> PageLayout.SINGLE_PAGE
            }
            popupMenu(
                items = listOf(
                    PageLayout.SINGLE_PAGE,
                    PageLayout.DOUBLE_PAGES,
                    PageLayout.SPLIT_PAGES,
                ).map { it.value to it.stringRes },
                selectedItemId = selectedId.value,
            ) {
                val newLayout = PageLayout.fromPreference(itemId)

                if (preferences.pageLayout().get() == PageLayout.AUTOMATIC.value) {
                    (viewer as? PagerViewer)?.config?.let { config ->
                        config.doublePages = newLayout == PageLayout.DOUBLE_PAGES
                        if (newLayout == PageLayout.SINGLE_PAGE) {
                            preferences.automaticSplitsPage().set(false)
                        } else if (newLayout == PageLayout.SPLIT_PAGES) {
                            preferences.automaticSplitsPage().set(true)
                        }
                        reloadChapters(config.doublePages, true)
                    }
                } else {
                    preferences.pageLayout().set(newLayout.value)
                }
            }
        }
    }

    fun hideMenu() {
        if (menuVisible && !isScrollingThroughPagesOrChapters) {
            setMenuVisibility(false)
        }
    }

    /**
     * Sets the visibility of the menu according to [visible] and with an optional parameter to
     * [animate] the views.
     */
    private fun setMenuVisibility(visible: Boolean, animate: Boolean = true) {
        val oldVisibility = menuVisible
        menuVisible = visible
        binding.root.post { rUpdateClock() }
        if (visible) coroutine?.cancel()
        rScheduleAutoHide()
        binding.viewerContainer.requestLayout()
        if (visible) {
            snackbar?.dismiss()
            if (preferences.rHideNavButtons().get()) {
                // R: the phone's 3 buttons (back / home / recents) stay hidden; they come only with a swipe up from the bottom edge
                wic.show(statusBars())
                wic.hide(navigationBars())
                wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                wic.show(systemBars())
            }
            binding.appBar.isVisible = true
            rToolsBar?.isVisible = true

            if (binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isExpanded()) {
                binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.isHideable = false
            }
            if (!binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isExpanded() && sheetManageNavColor) {
                window.navigationBarColor = Color.TRANSPARENT
            }
            if (animate && oldVisibility != menuVisible) {
                if (!menuTemporarilyVisible) {
                    val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_top)
                    toolbarAnimation.doOnStart {
                        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    }
                    toolbarAnimation.doOnEnd { delayTitleScroll() }
                    binding.appBar.startAnimation(toolbarAnimation)
                } else {
                    delayTitleScroll()
                }
                binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.collapse()
            }
        } else {
            rToolsBar?.close()
            rToolsBar?.isVisible = false
            if (preferences.fullscreen().get()) {
                wic.hide(systemBars())
                // R: keep the swipe-up-from-the-bottom working while the menu is hidden
                wic.systemBarsBehavior = if (preferences.rHideNavButtons().get()) {
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }
            }

            if (animate && binding.appBar.isVisible) {
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_top)
                toolbarAnimation.doOnEnd {
                    binding.appBar.isVisible = false
                    stopTitleScroll()
                }
                binding.appBar.startAnimation(toolbarAnimation)
                binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.isHideable = true
                binding.chaptersSheet.chaptersBottomSheet.sheetBehavior?.hide()
            } else if (!animate) {
                binding.appBar.isVisible = false
                stopTitleScroll()
            }
        }
        menuTemporarilyVisible = false
    }

    /**
     * Called from the view model when a manga is ready. Used to instantiate the appropriate viewer
     * and the binding.toolbar title.
     */
    private fun setManga(manga: Manga) {
        val prevViewer = viewer
        val noDefault = manga.viewer_flags == -1
        val mangaViewer = viewModel.getMangaReadingMode()
        val newViewer = when (mangaViewer) {
            ReadingModeType.LEFT_TO_RIGHT.flagValue -> L2RPagerViewer(this)
            ReadingModeType.VERTICAL.flagValue -> VerticalPagerViewer(this)
            ReadingModeType.LONG_STRIP.flagValue -> WebtoonViewer(this)
            ReadingModeType.CONTINUOUS_VERTICAL.flagValue -> WebtoonViewer(this, hasMargins = true)
            else -> R2LPagerViewer(this)
        }

        if (noDefault && viewModel.manga?.readingModeType!! > 0 &&
            viewModel.manga?.readingModeType!! != preferences.defaultReadingMode().get()
        ) {
            snackbar = binding.readerLayout.snack(
                getString(
                    MR.strings.reading_,
                    getString(
                        when (mangaViewer) {
                            ReadingModeType.RIGHT_TO_LEFT.flagValue -> MR.strings.right_to_left_viewer
                            ReadingModeType.VERTICAL.flagValue -> MR.strings.vertical_viewer
                            ReadingModeType.LONG_STRIP.flagValue -> MR.strings.long_strip
                            else -> MR.strings.left_to_right_viewer
                        },
                    ).lowercase(Locale.getDefault()),
                ),
                4000,
            ) {
                setAction(MR.strings.use_default) {
                    viewModel.setMangaReadingMode(0)
                }
            }
        }

        if (window.sharedElementEnterTransition is MaterialContainerTransform &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            // Wait until transition is complete to avoid crash on API 26
            window.sharedElementEnterTransition.addListener(
                onEnd = { setOrientation(viewModel.getMangaOrientationType()) },
            )
        } else {
            setOrientation(viewModel.getMangaOrientationType())
        }

        // Destroy previous viewer if there was one
        if (prevViewer != null) {
            prevViewer.destroy()
            binding.viewerContainer.removeAllViews()
        }
        viewer = newViewer
        binding.viewerContainer.addView(newViewer.getView())
        setupAutoScrollButton(newViewer)

        if (newViewer is R2LPagerViewer) {
            readerNav.leftChapter.compatToolTipText = getString(MR.strings.next_chapter)
            readerNav.rightChapter.compatToolTipText = getString(MR.strings.previous_chapter)
        } else {
            readerNav.leftChapter.compatToolTipText = getString(MR.strings.previous_chapter)
            readerNav.rightChapter.compatToolTipText = getString(MR.strings.next_chapter)
        }

        if (newViewer is PagerViewer) {
            newViewer.config.hingeGapSize = hingeGapSize
            if (preferences.pageLayout().get() == PageLayout.AUTOMATIC.value) {
                setDoublePageMode(newViewer)
            }
            lastShiftDoubleState?.let { newViewer.config.shiftDoublePage = it }
        }

        binding.navigationOverlay.isLTR = viewer !is R2LPagerViewer

        supportActionBar?.title = manga.title

        readerNav.pageSeekbar.isRTL = newViewer is R2LPagerViewer

        binding.pleaseWait.isVisible = true
        binding.pleaseWait.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in_long))
        invalidateOptionsMenu()
        updateCropBordersShortcut()
        updateBottomShortcuts()
        val viewerMode = ReadingModeType.fromPreference(viewModel.state.value.manga?.readingModeType ?: 0)
        binding.chaptersSheet.readingMode.setImageResource(viewerMode.iconRes)
        startPostponedEnterTransition()
    }

    private fun setupAutoScrollButton(newViewer: BaseViewer) {
        val webtoon = newViewer as? WebtoonViewer
        with(readerNav.autoScroll) {
            // R: with the side tools on, auto-scroll lives only in the tools card (play button), not in the side bar
            isVisible = webtoon != null && preferences.rAutoScroll().get() && !(preferences.rSideTools().get() && verticalNav)
            setImageResource(R.drawable.ic_play_arrow_24dp)
            setOnClickListener {
                val running = webtoon?.toggleAutoScroll() ?: false
                if (running) hideMenu()
            }
        }
        webtoon?.autoScrollListener = { running ->
            readerNav.autoScroll.setImageResource(
                if (running) R.drawable.ic_pause_24dp else R.drawable.ic_play_arrow_24dp,
            )
        }
    }

    private var rToolsBar: RToolsBar? = null

    // ---- R: reader lock. Everything hides except a small lock at the top-left; tap it to bring everything back.
    // While locked: long-press bottom-right = next chapter button, bottom-left = previous; double-tap top-right =
    // show the app's normal tools (this unlocks). ----
    private var rLocked = false
    private var rLockIcon: android.widget.ImageButton? = null

    private val rLockGestures by lazy {
        android.view.GestureDetector(
            this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                private fun zone(e: MotionEvent): Int {
                    val w = binding.root.width.toFloat().coerceAtLeast(1f)
                    val h = binding.root.height.toFloat().coerceAtLeast(1f)
                    return when {
                        e.y > h * 0.72f && e.x > w * 0.6f -> 1 // bottom-right
                        e.y > h * 0.72f && e.x < w * 0.4f -> 2 // bottom-left
                        e.y < h * 0.22f && e.x > w * 0.6f -> 3 // top-right
                        else -> 0
                    }
                }

                override fun onLongPress(e: MotionEvent) {
                    val z = zone(e)
                    if (z != 1 && z != 2) return
                    // cancel the touch so the page does not open its own long-press sheet
                    val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
                    super@ReaderActivity.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    loadAdjacentChapter(z == 1)
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (zone(e) != 3) return false
                    rUnlock()
                    return true
                }
            },
        )
    }

    private fun rLock() {
        if (rLocked) return
        rLocked = true
        rAutoHideJob?.cancel()
        setMenuVisibility(false)
        binding.rClockBattery.isVisible = false
        if (rLockIcon == null) {
            val size = 44.dpToPx
            val icon = android.widget.ImageButton(this).apply {
                setImageResource(R.drawable.ic_lock_24dp)
                setColorFilter(android.graphics.Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.argb(120, 0, 0, 0))
                }
                contentDescription = "فتح القفل"
                setOnClickListener { rUnlock(showMenu = false) }
            }
            (binding.root as ViewGroup).addView(
                icon,
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(size, size).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
                    leftMargin = 12.dpToPx
                    topMargin = 40.dpToPx
                },
            )
            rLockIcon = icon
        }
        rLockIcon?.isVisible = true
    }

    private fun rUnlock(showMenu: Boolean = true) {
        if (!rLocked) return
        rLocked = false
        rLockIcon?.isVisible = false
        rUpdateClock()
        if (showMenu) setMenuVisibility(true)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (rLocked) rLockGestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** R: a note for the chapter being read (write the page and what happened there). */
    private fun rShowChapterNote() {
        val manga = viewModel.manga ?: return
        val chapter = viewModel.getCurrentChapter()?.chapter ?: return
        val input = android.widget.EditText(this).apply {
            setText(eu.kanade.tachiyomi.util.RChapterNotes.get(manga.source, manga.url, chapter.url))
            hint = "مثلاً: ص١٢ هنا بدأ الأرك"
            minLines = 4
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val pad = 20.dpToPx
        val box = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("ملاحظة: ${chapter.name}")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                eu.kanade.tachiyomi.util.RChapterNotes.set(manga.source, manga.url, chapter.url, input.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** R: simple chapters list dialog (the tools bar used to expand the heavy chapters sheet, which froze some phones). */
    private var rChapterDialogOpen = false
    private var rChapterDialogRef: android.app.Dialog? = null

    /** R: title row of the chapters list: two chips to switch between the chapters and the reading settings. */
    private fun rChaptersSettingsSwitch(): View {
        val d = resources.displayMetrics.density
        val accent = getResourceColor(com.google.android.material.R.attr.colorSecondary)
        val onSurface = getResourceColor(com.google.android.material.R.attr.colorOnSurface)
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (6 * d).toInt())
        }
        fun chip(text: String, selected: Boolean, onClick: () -> Unit) = android.widget.TextView(this).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (selected) android.graphics.Color.BLACK else onSurface)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20 * d
                if (selected) {
                    setColor(accent)
                } else {
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke((1 * d).toInt().coerceAtLeast(1), androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 160))
                }
            }
            setPadding((18 * d).toInt(), (8 * d).toInt(), (18 * d).toInt(), (8 * d).toInt())
            setOnClickListener { onClick() }
        }
        row.addView(chip("الشباتر", true) {})
        row.addView(
            chip("إعدادات القراءة", false) {
                rChapterDialogRef?.dismiss()
                TabbedReaderSettingsSheet(this@ReaderActivity).show()
            },
            android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = (8 * d).toInt() },
        )
        return row
    }

    // ---- R: side tools (arrow on the side page bar -> the old toolbar as a glass strip beside it) ----
    private var rSideTools: RSideTools? = null
    private var rOldSheetHidden = false
    private var rSideToolsOpen = false
    private var rSideToolsPositioning = false

    private fun rSetupSideTools() {
        val sheet = binding.chaptersSheet
        // the old bottom toolbar is not shown any more: invisible and not draggable, its layout space stays as it was
        sheet.root.alpha = 0f
        sheet.topbarLayout.visibility = View.INVISIBLE
        sheet.root.sheetBehavior?.isDraggable = false
        rOldSheetHidden = true
        binding.root.requestApplyInsets()

        val strip = RSideTools(this) { rScheduleAutoHide() }
        strip.add("الشباتر", null, R.drawable.ic_format_list_numbered_24dp) { rShowChapterDialog() }
        strip.add("فتح في المتصفح", sheet.webviewButton, 0) { sheet.webviewButton.performClick() }
        strip.add("وضع القراءة", sheet.readingMode, 0) { sheet.readingMode.performClick() }
        strip.add("التدوير", sheet.rotationSheetButton, 0) { sheet.rotationSheetButton.performClick() }
        strip.add("قص الحواف", sheet.cropBordersSheetButton, 0) { sheet.cropBordersSheetButton.performClick() }
        strip.add("صفحتين", sheet.doublePage, 0) { sheet.doublePage.performClick() }
        strip.add("إعدادات القراءة", null, R.drawable.ic_tune_24dp) { TabbedReaderSettingsSheet(this@ReaderActivity).show() }
        strip.isVisible = false
        val lp = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            150.dpToPx,
        ).apply {
            gravity = Gravity.TOP or Gravity.RIGHT
        }
        (binding.root as ViewGroup).addView(strip, lp)
        rSideTools = strip

        with(binding.readerNavVertical.sideToolsToggle) {
            isVisible = true
            setOnClickListener {
                rSideToolsOpen = !rSideToolsOpen
                animate().rotation(if (rSideToolsOpen) 180f else 0f).setDuration(150).start()
                rScheduleAutoHide()
                rPositionSideTools()
            }
        }
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            // never change layout params inside the layout pass itself: do it right after it
            if (!rSideToolsPositioning) {
                rSideToolsPositioning = true
                binding.root.post {
                    try {
                        rPositionSideTools()
                    } finally {
                        rSideToolsPositioning = false
                    }
                }
            }
        }
    }

    /** R: puts the strip right beside the side page bar, as long as the bar (the arrow's row excluded). */
    private fun rPositionSideTools() {
        val strip = rSideTools ?: return
        val nav = binding.navLayout
        val show = rSideToolsOpen && menuVisible && !rLocked && nav.isVisible && readerNav.root.isVisible && nav.height > 0
        if (strip.isVisible != show) strip.isVisible = show
        if (!show) return
        strip.refresh()
        val arrow = 32.dpToPx
        val lp = strip.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams ?: return
        val top = nav.top + arrow
        val height = (nav.height - arrow - nav.paddingBottom).coerceAtLeast(120.dpToPx)
        val right = nav.width + 6.dpToPx + 8.dpToPx
        if (lp.topMargin != top || lp.height != height || lp.rightMargin != right) {
            lp.topMargin = top
            lp.height = height
            lp.rightMargin = right
            strip.layoutParams = lp
        }
    }

    private fun rShowChapterDialog() {
        lifecycleScope.launch {
            val items = viewModel.getChapters()
            if (items.isEmpty()) return@launch
            val names = items.map { it.preferredChapterName(this@ReaderActivity, viewModel.manga!!, preferences) }.toTypedArray()
            val current = items.indexOfFirst { it.isCurrent }.coerceAtLeast(0)
            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this@ReaderActivity)
                .setTitle("الشباتر")
                .setCustomTitle(rChaptersSettingsSwitch())
                .setSingleChoiceItems(names, current) { d, which ->
                    d.dismiss()
                    if (items[which].chapter.id != viewModel.getCurrentChapter()?.chapter?.id) {
                        lifecycleScope.launch { loadChapter(items[which].chapter) }
                    }
                    // the tools disappear after choosing a chapter (they used to stay over the page)
                    hideMenu()
                }
                .create()
            rChapterDialogRef = dialog
            // the tools card disappears while the list is open and comes back when it closes
            rChapterDialogOpen = true
            rToolsBar?.isVisible = false
            dialog.setOnDismissListener {
                rChapterDialogOpen = false
                rPositionToolsBar()
            }
            dialog.show()
            dialog.listView?.setSelection((current - 3).coerceAtLeast(0))
        }
    }

    /** R: the tools bar (speed / brightness / volume / battery) under the toolbar, with fling gestures. */
    private fun rSetupToolsBar() {
        if (!preferences.rToolsBar().get()) return
        val bar = RToolsBar(
            this,
            preferences,
            { rBatteryPercent },
            { rShowChapterDialog() },
            { TabbedReaderSettingsSheet(this@ReaderActivity).show() },
            { rScheduleAutoHide() },
            { rLock() },
            { loadAdjacentChapter(viewer is R2LPagerViewer) }, // previous chapter
            { loadAdjacentChapter(viewer !is R2LPagerViewer) }, // next chapter
            { rShowChapterDialog() },
        )
        // R: floating card at the bottom; its bottom margin is computed from the TOP of the page bar / chapters sheet
        // (rPositionToolsBar), so it never covers them
        val lp = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = android.view.Gravity.BOTTOM
            leftMargin = 10.dpToPx
            rightMargin = 10.dpToPx
            bottomMargin = 120.dpToPx
        }
        bar.isVisible = menuVisible
        (binding.root as ViewGroup).addView(bar, lp)
        rToolsBar = bar
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            // never change layout params inside the layout pass itself (could loop and freeze the app): do it after it
            if (!rPositioning) {
                rPositioning = true
                binding.root.post {
                    try {
                        rPositionToolsBar()
                    } finally {
                        rPositioning = false
                    }
                }
            }
        }
    }

    private var rPositioning = false
    private var rLastDetailsTap = 0L

    /** R: puts the tools card right above the page bar (or above the chapters sheet when the page bar is not shown). */
    private fun rPositionToolsBar() {
        val bar = rToolsBar ?: return
        val rootHeight = binding.root.height
        if (rootHeight <= 0) return
        // while the chapters list is opening / open the tools stay hidden and untouched (no layout work during the slide)
        if (!binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isCollapsed()) {
            if (bar.isVisible) bar.isVisible = false
            return
        }
        val sheet = binding.chaptersSheet.root
        val pageBarShown = !verticalNav && binding.navLayout.isVisible && readerNav.root.isVisible
        val top = if (pageBarShown) binding.navLayout.top else sheet.top
        // R: with the original page bar hidden the card sits at the very bottom (where the phone buttons used to be);
        // when the phone buttons are shown by a swipe up, the card rides up on top of them
        val navInsets = ViewCompat.getRootWindowInsets(binding.root)
        val navLift = if (navInsets?.isVisible(navigationBars()) == true) navInsets.getInsets(navigationBars()).bottom else 0
        val margin = if (pageBarShown) {
            (rootHeight - top + 8.dpToPx).coerceAtLeast(0)
        } else {
            navLift + 10.dpToPx
        }
        val lp = bar.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams ?: return
        val right = if (verticalNav && binding.navLayout.isVisible) 64.dpToPx else 10.dpToPx // the vertical slider lives on the right
        if (lp.bottomMargin != margin || lp.rightMargin != right) {
            lp.bottomMargin = margin
            lp.rightMargin = right
            bar.layoutParams = lp
        }
        // hidden while the chapters list is open
        if (menuVisible) {
            val shown = !rChapterDialogOpen && binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isCollapsed()
            if (bar.isVisible != shown) bar.isVisible = shown
        }
    }

    /** R: hides the tools by itself after the delay chosen in the reader settings (0 = never). */
    private fun rScheduleAutoHide() {
        rAutoHideJob?.cancel()
        val seconds = preferences.rAutoHideTools().get()
        if (!menuVisible || seconds <= 0 || menuTemporarilyVisible) return
        rAutoHideJob = lifecycleScope.launch {
            delay(seconds * 1000L)
            val sheetOpen = binding.chaptersSheet.chaptersBottomSheet.sheetBehavior.isExpanded()
            if (menuVisible && !menuTemporarilyVisible && !sheetOpen) setMenuVisibility(false)
        }
    }

    /** R: clock + battery in the corner (switch in the reader settings). Always shown while the switch is on. */
    private fun rUpdateClock() {
        val on = preferences.rClockBattery().get() && !rLocked
        binding.rClockBattery.isVisible = on
        if (!on) return
        rReadBattery()
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(Date())
        // time, then (while plugged in) the green bolt in the gap, then the percentage
        val tv = binding.rClockBattery
        val sb = android.text.SpannableStringBuilder(time)
        if (rBatteryPercent >= 0) {
            sb.append("  ")
            if (rCharging) {
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_r_charging)?.mutate()?.let { d ->
                    val h = (tv.textSize * 1.15f).toInt()
                    d.setBounds(0, 0, h, h)
                    val start = sb.length
                    sb.append(" ")
                    sb.setSpan(
                        android.text.style.ImageSpan(d, android.text.style.ImageSpan.ALIGN_BOTTOM),
                        start,
                        sb.length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    sb.append("  ")
                }
            }
            sb.append("$rBatteryPercent%")
        }
        tv.text = sb
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        rPositionClock()
    }

    /** R: reads the battery level from the sticky broadcast, so it never depends on the receiver having fired. */
    private fun rReadBattery() {
        try {
            val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
            val level = i.getIntExtra("level", -1)
            val scale = i.getIntExtra("scale", 100)
            if (level >= 0 && scale > 0) rBatteryPercent = level * 100 / scale
            rCharging = i.getIntExtra("plugged", 0) != 0
        } catch (_: Exception) {
        }
    }

    /** R: readable on any page colour (white text on a soft dark pill), sized once. */
    private fun rStyleClock() {
        binding.rClockBattery.apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(10.dpToPx, 3.dpToPx, 10.dpToPx, 3.dpToPx)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12.dpToPx.toFloat()
                setColor(Color.argb(150, 0, 0, 0))
            }
        }
    }

    /** R: keeps the clock below the status bar / camera cutout so it is never hidden behind them. */
    private fun rPositionClock() {
        val v = binding.rClockBattery
        if (!v.isVisible) return
        val insets = ViewCompat.getRootWindowInsets(binding.root)
        val top = insets?.let {
            max(
                it.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                it.getInsets(WindowInsetsCompat.Type.displayCutout()).top,
            )
        } ?: 0
        val margin = top + 6.dpToPx
        val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (lp.topMargin != margin) {
            lp.topMargin = margin
            v.layoutParams = lp
        }
    }

    override fun onPause() {
        try {
            unregisterReceiver(rClockReceiver)
        } catch (_: Exception) {
        }
        rAutoHideJob?.cancel()
        rClockJob?.cancel()
        (viewer as? WebtoonViewer)?.stopAutoScroll()
        rCobra?.onActivityPause()
        viewModel.flushReadTimer()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        val clockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, rClockReceiver, clockFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        rUpdateClock()
        // R: refresh every 20 s too, in case a broadcast is missed
        rClockJob?.cancel()
        rClockJob = lifecycleScope.launch {
            while (true) {
                rUpdateClock()
                delay(20_000L)
            }
        }
        rScheduleAutoHide()
        viewModel.restartReadTimer()
        rCobra?.onActivityResume()
    }

    fun reloadChapters(doublePages: Boolean, force: Boolean = false) {
        val pViewer = viewer as? PagerViewer ?: return
        pViewer.updateShifting()
        if (!force && pViewer.config.autoDoublePages) {
            setDoublePageMode(pViewer)
        } else {
            pViewer.config.doublePages = doublePages
            if (pViewer.config.autoDoublePages) {
                pViewer.config.splitPages = preferences.automaticSplitsPage().get() && !pViewer.config.doublePages
            }
        }
        if (doublePages) {
            // If we're moving from single to double, we want the current page to be the first page
            val currentIndex = readerNav.pageSeekbar.value.roundToInt()
            viewModel.getCurrentChapter()?.requestedPage = currentIndex
            pViewer.hasMoved = false
            pViewer.config.shiftDoublePage = shouldShiftDoublePages(currentIndex)
        }
        viewModel.state.value.viewerChapters?.let {
            pViewer.setChaptersDoubleShift(it)
        }
        invalidateOptionsMenu()
    }

    private fun shouldShiftDoublePages(currentIndex: Int): Boolean {
        val currentChapter = viewModel.getCurrentChapter()
        return (
            currentIndex +
                (currentChapter?.pages?.take(currentIndex)?.count { it.alonePage } ?: 0)
            ) % 2 != 0
    }

    /**
     * Called from the view model whenever a new [viewerChapters] have been set. It delegates the
     * method to the current viewer, but also set the subtitle on the binding.toolbar.
     */
    fun setChapters(viewerChapters: ViewerChapters) {
        binding.pleaseWait.clearAnimation()
        binding.pleaseWait.isVisible = false
        if (indexChapterToShift != null && indexPageToShift != null) {
            viewerChapters.currChapter.pages?.find { it.index == indexPageToShift && it.chapter.chapter.id == indexChapterToShift }?.let {
                (viewer as? PagerViewer)?.updateShifting(it)
            }
            indexChapterToShift = null
            indexPageToShift = null
        }
        val currentChapterPageCount = viewerChapters.currChapter.pages?.size ?: 1
        readerNav.root.visibility = when {
            currentChapterPageCount == 1 -> View.GONE
            binding.chaptersSheet.root.sheetBehavior.isCollapsed() -> View.VISIBLE
            else -> View.INVISIBLE
        }
        if (lastShiftDoubleState == null) {
            manuallyShiftedPages = false
        }
        lastShiftDoubleState = null
        viewer?.setChapters(viewerChapters)
        intentPageNumber?.let { moveToPageIndex(it) }
        intentPageNumber = null
        val chapter = viewerChapters.currChapter.chapter
        rCobra?.onChapterOpened(chapter.id ?: 0L)
        binding.toolbar.subtitle =
            chapter.preferredChapterName(this, viewModel.manga!!, preferences)
        rToolsBar?.setChapterTitle(chapter.preferredChapterName(this, viewModel.manga!!, preferences))
        rToolsBar?.setNavEnabled(viewerChapters.prevChapter != null, viewerChapters.nextChapter != null)

        listOfNotNull(getTitleTextView(), getSubtitleTextView()).forEach { textView ->
            textView.ellipsize = TextUtils.TruncateAt.MARQUEE
            textView.marqueeRepeatLimit = -1
            textView.isSingleLine = true
            textView.isFocusable = true
            textView.isFocusableInTouchMode = true
            textView.isHorizontalFadingEdgeEnabled = true
            textView.setFadingEdgeLength(16.dpToPx)
            textView.setHorizontallyScrolling(true)
        }

        if (viewerChapters.nextChapter == null && viewerChapters.prevChapter == null) {
            readerNav.leftChapter.isVisible = false
            readerNav.rightChapter.isVisible = false
        } else if (viewer is R2LPagerViewer) {
            readerNav.leftChapter.alpha = if (viewerChapters.nextChapter != null) 1f else 0.5f
            readerNav.rightChapter.alpha = if (viewerChapters.prevChapter != null) 1f else 0.5f
        } else {
            readerNav.rightChapter.alpha = if (viewerChapters.nextChapter != null) 1f else 0.5f
            readerNav.leftChapter.alpha = if (viewerChapters.prevChapter != null) 1f else 0.5f
        }
        if (didTransitionFromChapter) {
            MainActivity.chapterIdToExitTo = viewerChapters.currChapter.chapter.id ?: 0L
        }
    }

    private fun getTitleTextView(): TextView? = getTextViewsWithText(binding.toolbar.title)
    private fun getSubtitleTextView(): TextView? = getTextViewsWithText(binding.toolbar.subtitle)

    private fun getTextViewsWithText(text: CharSequence?): TextView? {
        if (text.isNullOrBlank()) return null
        val viewTopComparator = Comparator<View> { view1, view2 -> view1.top - view2.top }
        val textViews = binding.toolbar.children.filterIsInstance<TextView>()
            .filter { TextUtils.equals(it.text, text) }.toList()
        return if (textViews.isEmpty()) null else Collections.max(textViews, viewTopComparator)
    }

    private fun delayTitleScroll() {
        val list = listOfNotNull(getTitleTextView(), getSubtitleTextView())
        if (list.isNotEmpty()) {
            scope.launchUI {
                delay(1000)
                if (menuVisible) {
                    list.forEach { it.isSelected = true }
                }
            }
        }
    }

    private fun stopTitleScroll() =
        listOfNotNull(getTitleTextView(), getSubtitleTextView()).forEach { it.isSelected = false }

    /**
     * Called from the view model if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialChapterError(error: Throwable) {
        Logger.e(error)
        finish()
        toast(error.message)
    }

    /**
     * Called from the view model whenever it's loading the next or previous chapter. It shows or
     * dismisses a non-cancellable dialog to prevent user interaction according to the value of
     * [show]. This is only used when the next/previous buttons on the binding.toolbar are clicked; the
     * other cases are handled with chapter transitions on the viewers and chapter preloading.
     */
    private fun setProgressDialog(show: Boolean) {
        if (!show) {
            readerNav.leftChapter.isVisible = true
            readerNav.rightChapter.isVisible = true

            readerNav.leftProgress.isVisible = false
            readerNav.rightProgress.isVisible = false
            binding.chaptersSheet.root.resetChapter()
        }
        if (show) {
            isLoading = true
        } else {
            scope.launchIO {
                delay(100)
                isLoading = false
            }
        }
    }

    /**
     * Moves the viewer to the given page [index]. It does nothing if the viewer is null or the
     * page is not found.
     */
    private fun moveToPageIndex(index: Int, animated: Boolean = true, chapterChange: Boolean = false) {
        val viewer = viewer ?: return
        val currentChapter = viewModel.getCurrentChapter() ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        viewer.moveToPage(page, animated)
        if (chapterChange) {
            isScrollingThroughPagesOrChapters = false
        }
    }

    private fun refreshChapters() {
        binding.chaptersSheet.chaptersBottomSheet.refreshList()
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the view model.
     */
    @SuppressLint("SetTextI18n")
    fun onPageSelected(page: ReaderPage, hasExtraPage: Boolean) {
        viewModel.onPageSelected(page, hasExtraPage)
        val pages = page.chapter.pages ?: return

        val currentPage = if (hasExtraPage) {
            val invertDoublePage = (viewer as? PagerViewer)?.config?.invertDoublePages ?: false
            if (!readerNav.pageSeekbar.isRTL.xor(invertDoublePage)) {
                "${page.number}-${page.number + 1}"
            } else {
                "${page.number + 1}-${page.number}"
            }
        } else {
            "${page.number}${if (page.firstHalf == false) "*" else ""}"
        }

        val totalPages = pages.size.toString()
        if (pages.isNotEmpty()) binding.rProgress.progress = page.number * 100 / pages.size
        if (hingeGapSize > 0) {
            binding.pageNumber.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                marginStart = (binding.root.width) / 2 + hingeGapSize
            }
        }
        binding.pageNumber.text = if (resources.isLTR) "$currentPage/$totalPages" else "$totalPages/$currentPage"
        rToolsBar?.setPageText("$currentPage / $totalPages")
        if (viewer is R2LPagerViewer) {
            readerNav.rightPageText.text = currentPage
            readerNav.leftPageText.text = totalPages
        } else {
            readerNav.leftPageText.text = currentPage
            readerNav.rightPageText.text = totalPages
        }
        if (binding.chaptersSheet.chaptersBottomSheet.selectedChapterId != page.chapter.chapter.id) {
            binding.chaptersSheet.chaptersBottomSheet.refreshList()
        }
        // Set seekbar progress
        readerNav.pageSeekbar.valueTo = max(pages.lastIndex.toFloat(), 1f)
        val progress = page.index + if (hasExtraPage) 1 else 0
        // For a double page, show the last 2 pages as if it was the final part of the seekbar
        readerNav.pageSeekbar.value = (if (progress == pages.lastIndex) progress else page.index).toFloat()
    }

    /**
     * Called from the viewer whenever a [page] is long clicked. A bottom sheet with a list of
     * actions to perform is shown.
     */
    fun onPageLongTap(page: ReaderPage, extraPage: ReaderPage? = null) {
        val items = if (extraPage != null) {
            listOf(
                MaterialMenuSheet.MenuSheetItem(
                    3,
                    R.drawable.ic_outline_share_24dp,
                    MR.strings.share_second_page,
                ),
                MaterialMenuSheet.MenuSheetItem(
                    4,
                    R.drawable.ic_outline_save_24dp,
                    MR.strings.save_second_page,
                ),
                MaterialMenuSheet.MenuSheetItem(
                    5,
                    R.drawable.ic_outline_photo_24dp,
                    MR.strings.set_second_page_as_cover,
                ),
                MaterialMenuSheet.MenuSheetItem(
                    0,
                    R.drawable.ic_share_24dp,
                    MR.strings.share_first_page,
                ),
                MaterialMenuSheet.MenuSheetItem(
                    1,
                    R.drawable.ic_save_24dp,
                    MR.strings.save_first_page,
                ),
                MaterialMenuSheet.MenuSheetItem(
                    2,
                    R.drawable.ic_photo_24dp,
                    MR.strings.set_first_page_as_cover,
                ),
                MaterialMenuSheet.MenuSheetItem(
                    6,
                    R.drawable.ic_share_all_outline_24dp,
                    MR.strings.share_combined_pages,
                ),
                MaterialMenuSheet.MenuSheetItem(
                    7,
                    R.drawable.ic_save_all_outline_24dp,
                    MR.strings.save_combined_pages,
                ),
            )
        } else {
            listOf(
                MaterialMenuSheet.MenuSheetItem(
                    0,
                    R.drawable.ic_share_24dp,
                    MR.strings.share,
                ),
                MaterialMenuSheet.MenuSheetItem(
                    1,
                    R.drawable.ic_save_24dp,
                    MR.strings.save,
                ),
                MaterialMenuSheet.MenuSheetItem(
                    2,
                    R.drawable.ic_photo_24dp,
                    MR.strings.set_as_cover,
                ),
            )
        }
        MaterialMenuSheet(this, items) { _, item ->
            when (item) {
                0 -> shareImage(page)
                1 -> saveImage(page)
                2 -> showSetCoverPrompt(page)
                3 -> extraPage?.let { shareImage(it) }
                4 -> extraPage?.let { saveImage(it) }
                5 -> extraPage?.let { showSetCoverPrompt(it) }
                6, 7 -> extraPage?.let { secondPage ->
                    (viewer as? PagerViewer)?.let { viewer ->
                        val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
                        val bg = ThemeUtil.readerBackgroundColor(viewer.config.readerTheme)
                        if (item == 6) {
                            viewModel.shareImages(page, secondPage, isLTR, bg)
                        } else {
                            viewModel.saveImages(page, secondPage, isLTR, bg)
                        }
                    }
                }
            }
            true
        }.show()
        if (binding.chaptersSheet.root.sheetBehavior.isExpanded()) {
            binding.chaptersSheet.root.sheetBehavior?.collapse()
        }
    }

    /**
     * Called from the viewer when the given [chapter] should be preloaded. It should be called when
     * the viewer is reaching the beginning or end of a chapter or the transition page is active.
     */
    fun requestPreloadChapter(chapter: ReaderChapter) {
        lifecycleScope.launch {
            viewModel.preloadChapter(chapter)
        }
    }

    /**
     * Called from the viewer to toggle the visibility of the menu. It's implemented on the
     * viewer because each one implements its own touch and key events.
     */
    fun toggleMenu() {
        if (rLocked) return
        setMenuVisibility(!menuVisible)
    }

    /**
     * Called from the viewer to show the menu.
     */
    fun showMenu() {
        if (rLocked) return
        if (!menuVisible) {
            setMenuVisibility(true)
        }
    }

    /**
     * Called from the page sheet. It delegates the call to the view model to do some IO, which
     * will call [onShareImageResult] with the path the image was saved on when it's ready.
     */
    private fun shareImage(page: ReaderPage) {
        viewModel.shareImage(page)
    }

    private fun showSetCoverPrompt(page: ReaderPage) {
        if (page.status !is Page.State.Ready) return

        materialAlertDialog()
            .setMessage(MR.strings.use_image_as_cover)
            .setPositiveButton(AR.string.ok) { _, _ ->
                setAsCover(page)
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }

    /**
     * Called from the view model when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    private fun onShareImageResult(file: UniFile, page: ReaderPage, secondPage: ReaderPage? = null) {
        val manga = viewModel.manga ?: return
        val chapter = page.chapter.chapter

        val decimalFormat =
            DecimalFormat("#.###", DecimalFormatSymbols().apply { decimalSeparator = '.' })

        val pageNumber = if (secondPage != null) {
            getString(MR.strings.pages_, if (resources.isLTR) "${page.number}-${page.number + 1}" else "${page.number + 1}-${page.number}")
        } else {
            getString(MR.strings.page_, page.number)
        }
        val text = "${manga.title}: ${if (chapter.isRecognizedNumber) {
            getString(MR.strings.chapter_, decimalFormat.format(chapter.chapter_number))
        } else {
            chapter.preferredChapterName(this, manga, preferences)
        }
        }, $pageNumber"

        val stream = file.uri.toFile().getUriCompat(this)
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_STREAM, stream)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            clipData = ClipData.newRawUri(null, stream)
            type = "image/*"
        }
        startActivity(Intent.createChooser(intent, getString(MR.strings.share)))
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        val chapterUrl = viewModel.getChapterUrl() ?: return
        outContent.webUri = Uri.parse(chapterUrl)
    }

    /**
     * Called from the page sheet. It delegates saving the image of the given [page] on external
     * storage to the viewModel.
     */
    private fun saveImage(page: ReaderPage) {
        viewModel.saveImage(page)
    }

    /**
     * Called from the view model when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    private fun onSaveImageResult(result: ReaderViewModel.SaveImageResult) {
        when (result) {
            is ReaderViewModel.SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }
            is ReaderViewModel.SaveImageResult.Error -> {
                Logger.e(result.error)
            }
        }
    }

    /**
     * Called from the page sheet. It delegates setting the image of the given [page] as the
     * cover to the viewModel.
     */
    private fun setAsCover(page: ReaderPage) {
        viewModel.setAsCover(page)
    }

    /**
     * Called from the view model when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    private fun onSetAsCoverResult(result: ReaderViewModel.SetAsCoverResult) {
        toast(
            when (result) {
                Success -> MR.strings.cover_updated
                AddToLibraryFirst -> MR.strings.must_be_in_library_to_edit
                Error -> MR.strings.failed_to_update_cover
            },
        )
    }

    private fun showTrackingError(errors: List<Pair<TrackService, String?>>) {
        if (errors.isEmpty()) return
        snackbar?.dismiss()
        val errorText = if (errors.size > 1) {
            getString(MR.strings.failed_to_update_, errors.joinToString(", ") { getString(it.first.nameRes()) })
        } else {
            val (service, errorMessage) = errors.first()
            buildSpannedString {
                if (errorMessage != null) {
                    val icon = contextCompatDrawable(service.getLogo())
                        ?.mutate()
                        ?.run {
                            (this as? BitmapDrawable)?.run {
                                val newBitmap = Bitmap.createBitmap(
                                    intrinsicWidth,
                                    intrinsicHeight,
                                    bitmap.config!!,
                                )
                                val canvas = Canvas(newBitmap)
                                val bgColor = ColorUtils.setAlphaComponent(service.getLogoColor(), 255)
                                canvas.drawColor(bgColor)
                                canvas.drawBitmap(bitmap, 0f, 0f, null)
                                BitmapDrawable(resources, newBitmap)
                            } ?: this
                        }?.apply {
                            val size =
                                resources.getDimension(com.google.android.material.R.dimen.design_snackbar_text_size)
                            val dRatio = intrinsicWidth / intrinsicHeight.toFloat()
                            setBounds(0, 0, (size * dRatio).roundToInt(), size.roundToInt())
                        } ?: return
                    val alignment =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) DynamicDrawableSpan.ALIGN_CENTER else DynamicDrawableSpan.ALIGN_BASELINE
                    inSpans(ImageSpan(icon, alignment)) { append("image") }
                    append(" - $errorMessage")
                }
            }
        }
        snackbar = binding.readerLayout.snack(errorText, 5000)
    }

    private fun onVisibilityChange(visible: Boolean) {
        if (visible && !menuTemporarilyVisible && !menuVisible && !binding.appBar.isVisible) {
            menuTemporarilyVisible = true
            coroutine = scope.launchUI {
                delay(2000)
                if (window.decorView.rootWindowInsetsCompat?.isVisible(statusBars()) == true) {
                    menuTemporarilyVisible = false
                    setMenuVisibility(false)
                }
            }
            if (sheetManageNavColor) {
                window.navigationBarColor =
                    ColorUtils.setAlphaComponent(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 || isInNightMode()) {
                            getResourceColor(R.attr.colorSurface)
                        } else {
                            Color.BLACK
                        },
                        if (binding.root.rootWindowInsetsCompat?.hasSideNavBar() == true) {
                            255
                        } else {
                            179
                        },
                    )
            }
            binding.appBar.isVisible = true
            val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_top)
            toolbarAnimation.doOnStart {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            }
            binding.appBar.startAnimation(toolbarAnimation)
        } else if (!visible && (menuTemporarilyVisible || menuVisible)) {
            if (menuTemporarilyVisible && !menuVisible) {
                setMenuVisibility(false)
            }
            coroutine?.cancel()
        }
    }

    /**
     * Sets notch cutout mode to "NEVER", if mobile is in a landscape view
     */
    private fun setCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val currentOrientation = resources.configuration.orientation

            val attributes = window.attributes
            attributes.layoutInDisplayCutoutMode =
                when (currentOrientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> {
                        if (readerPreferences.landscapeCutoutBehavior().get() == LandscapeCutoutBehaviour.HIDE) {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                        } else {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                    }

                    else -> {
                        if (readerPreferences.cutoutShort().get()) {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        } else {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                        }
                    }
                }

            window.attributes = attributes
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val currentOrientation = resources.configuration.orientation
            DeviceUtil.setLegacyCutoutMode(
                window,
                when (currentOrientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> {
                        if (readerPreferences.landscapeCutoutBehavior().get() == LandscapeCutoutBehaviour.HIDE)
                            LegacyCutoutMode.NEVER
                        else
                            LegacyCutoutMode.SHORT_EDGES
                    }
                    else -> {
                        if (readerPreferences.cutoutShort().get())
                            LegacyCutoutMode.SHORT_EDGES
                        else
                            LegacyCutoutMode.NEVER
                    }
                },
            )
        }
    }

    private fun setDoublePageMode(viewer: PagerViewer) {
        val currentOrientation = resources.configuration.orientation
        viewer.config.doublePages = (currentOrientation == Configuration.ORIENTATION_LANDSCAPE)
        if (viewer.config.autoDoublePages) {
            viewer.config.splitPages = preferences.automaticSplitsPage().get() && !viewer.config.doublePages
        }
    }

    private fun handleIntentAction(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        if (!viewModel.canLoadUrl(uri)) {
            openInBrowser(intent.data!!.toString(), true)
            finishAfterTransition()
            return true
        }
        setMenuVisibility(visible = false, animate = true)
        scope.launch(Dispatchers.IO) {
            try {
                intentPageNumber = viewModel.intentPageNumber(uri)
                viewModel.loadChapterURL(uri)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setInitialChapterError(e)
                }
            }
        }
        return true
    }

    private fun openMangaInBrowser() {
        val source = viewModel.getSource() ?: return
        val chapterUrl = viewModel.getChapterUrl() ?: return

        val intent = WebViewActivity.newIntent(
            applicationContext,
            chapterUrl,
            source.id,
            viewModel.manga!!.title,
        )
        startActivity(intent)
    }

    /**
     * Forces the user preferred [orientation] on the activity.
     */
    fun setOrientation(orientation: Int) {
        val newOrientation = OrientationType.fromPreference(orientation)
        if (newOrientation.flag != requestedOrientation) {
            requestedOrientation = newOrientation.flag
        }
    }

    /**
     * Class that handles the user preferences of the reader.
     */
    private inner class ReaderConfig {

        var showNewChapter = false

        /**
         * Initializes the reader subscriptions.
         */
        init {
            preferences.readerTheme().changesIn(scope) { theme ->
                    binding.viewerContainer.setBackgroundColor(
                        ThemeUtil.readerBackgroundColor(
                            theme,
                            getResourceColor(R.attr.background),
                        )
                    )
                }

            preferences.defaultOrientationType().changes()
                .drop(1)
                .onEach {
                    delay(250)
                    setOrientation(viewModel.getMangaOrientationType())
                }
                .launchIn(scope)

            preferences.showPageNumber().changesIn(scope) { setPageNumberVisibility(it) }
            preferences.rClockBattery().changesIn(scope) { rUpdateClock() }
            preferences.rProgressBar().changesIn(scope) { binding.rProgress.isVisible = it }
            preferences.rAutoHideTools().changesIn(scope) { rScheduleAutoHide() }

            readerPreferences.cutoutShort().changesIn(scope) { setCutoutMode() }

            readerPreferences.landscapeCutoutBehavior().changes()
                .drop(1)
                .onEach { setCutoutMode() }
                .launchIn(scope)

            basePreferences.displayProfile().changesIn(scope) { setDisplayProfile(it) }

            preferences.fullscreen().changesIn(scope) { setFullscreen(it) }

            preferences.keepScreenOn().changesIn(scope) { setKeepScreenOn(it) }

            preferences.customBrightness().changesIn(scope) { setCustomBrightness(it) }

            preferences.colorFilter().changesIn(scope) { setColorFilter(it) }

            preferences.colorFilterMode().changesIn(scope) {
                setColorFilter(preferences.colorFilter().get())
            }

            merge(preferences.grayscale().changes(), preferences.invertedColors().changes())
                .onEach { setLayerPaint(preferences.grayscale().get(), preferences.invertedColors().get()) }
                .launchIn(lifecycleScope)

            preferences.alwaysShowChapterTransition().changesIn(scope) {
                showNewChapter = it
            }

            preferences.pageLayout().changesIn(scope) { setBottomNavButtons(it) }

            preferences.automaticSplitsPage().changes()
                .drop(1)
                .onEach {
                    val isPaused = !this@ReaderActivity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    if (isPaused) {
                        (viewer as? PagerViewer)?.config?.let { config ->
                            reloadChapters(config.doublePages, true)
                        }
                    }
                }
                .launchIn(scope)

            preferences.readerBottomButtons().changesIn(scope) { updateBottomShortcuts() }
        }

        /**
         * Sets the visibility of the bottom page indicator according to [visible].
         */
        private fun setPageNumberVisibility(visible: Boolean) {
            binding.pageNumber.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        }

        /**
         * Sets the display profile to [path].
         */
        private fun setDisplayProfile(path: String) {
            val file = UniFile.fromUri(baseContext, path.toUri())
            if (file != null && file.exists()) {
                val inputStream = file.openInputStream()
                val outputStream = ByteArrayOutputStream()
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                val data = outputStream.toByteArray()
                SubsamplingScaleImageView.setDisplayProfile(data)
                TachiyomiImageDecoder.displayProfile = data
            }
        }

        /**
         * Sets the fullscreen reading mode (immersive) according to [enabled].
         */
        private fun setFullscreen(enabled: Boolean) {
            WindowCompat.setDecorFitsSystemWindows(window, !enabled || isSplitScreen)
            wic.systemBarsBehavior = if (preferences.rHideNavButtons().get()) {
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
            binding.root.rootWindowInsetsCompat?.let { setNavColor(it) }
        }

        /**
         * Sets the keep screen on mode according to [enabled].
         */
        private fun setKeepScreenOn(enabled: Boolean) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        /**
         * Sets the custom brightness overlay according to [enabled].
         */
        private fun setCustomBrightness(enabled: Boolean) {
            if (enabled) {
                preferences.customBrightnessValue().changes()
                    .sample(100)
                    .onEach { setCustomBrightnessValue(it) }
                    .launchIn(scope)
            } else {
                setCustomBrightnessValue(0)
            }
        }

        /**
         * Sets the color filter overlay according to [enabled].
         */
        private fun setColorFilter(enabled: Boolean) {
            if (enabled) {
                preferences.colorFilterValue().changes()
                    .sample(100)
                    .onEach { setColorFilterValue(it) }
                    .launchIn(scope)
            } else {
                binding.colorOverlay.isVisible = false
            }
        }

        private fun getCombinedPaint(grayscale: Boolean, invertedColors: Boolean): Paint {
            return Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply {
                        if (grayscale) {
                            setSaturation(0f)
                        }
                        if (invertedColors) {
                            postConcat(
                                ColorMatrix(
                                    floatArrayOf(
                                        -1f, 0f, 0f, 0f, 255f,
                                        0f, -1f, 0f, 0f, 255f,
                                        0f, 0f, -1f, 0f, 255f,
                                        0f, 0f, 0f, 1f, 0f,
                                    ),
                                ),
                            )
                        }
                    },
                )
            }
        }

        /**
         * Sets the brightness of the screen. Range is [-75, 100].
         * From -75 to -1 a semi-transparent black view is overlaid with the minimum brightness.
         * From 1 to 100 it sets that value as brightness.
         * 0 sets system brightness and hides the overlay.
         */
        private fun setCustomBrightnessValue(value: Int) {
            // Calculate and set reader brightness.
            val readerBrightness = when {
                value > 0 -> {
                    value / 100f
                }
                value < 0 -> {
                    0.01f
                }
                else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }

            window.attributes = window.attributes.apply { screenBrightness = readerBrightness }

            // Set black overlay visibility.
            if (value < 0) {
                binding.brightnessOverlay.isVisible = true
                val alpha = (abs(value) * 2.56).toInt()
                binding.brightnessOverlay.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
            } else {
                binding.brightnessOverlay.isVisible = false
            }
        }

        /**
         * Sets the color filter [value].
         */
        private fun setColorFilterValue(value: Int) {
            binding.colorOverlay.isVisible = true
            binding.colorOverlay.setFilterColor(value, preferences.colorFilterMode().get())
        }

        private fun setLayerPaint(grayscale: Boolean, invertedColors: Boolean) {
            val paint = if (grayscale || invertedColors) getCombinedPaint(grayscale, invertedColors) else null
            binding.viewerContainer.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        }
    }
}
