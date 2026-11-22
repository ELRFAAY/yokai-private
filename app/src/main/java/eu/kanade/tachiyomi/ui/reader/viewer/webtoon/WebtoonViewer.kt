package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.graphics.PointF
import android.os.Build
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import uy.kohesive.injekt.injectLazy

/**
 * Implementation of a [BaseViewer] to display pages with a [RecyclerView].
 */
class WebtoonViewer(val activity: ReaderActivity, val hasMargins: Boolean = false) : BaseViewer {

    val downloadManager: DownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * Recycler view used by this viewer.
     */
    val recycler = WebtoonRecyclerView(activity)

    /**
     * Frame containing the recycler view.
     */
    private val frame = WebtoonFrame(activity)

    /**
     * Distance to scroll when the user taps on one side of the recycler view.
     */
    private var scrollDistance = activity.resources.displayMetrics.heightPixels * 3 / 4

    /**
     * Layout manager of the recycler view.
     */
    private val layoutManager = WebtoonLayoutManager(activity, scrollDistance)

    /**
     * Adapter of the recycler view.
     */
    private val adapter = WebtoonAdapter(this)

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    private var currentPage: Any? = null

    /**
     * Configuration used by this viewer, like allow taps, or crop image borders.
     */
    val config = WebtoonConfig(scope)

    private val rPreferences: PreferencesHelper by injectLazy()

    /** Called (on the main thread) whenever auto-scroll starts or stops, so the UI can update its button. */
    var autoScrollListener: ((running: Boolean) -> Unit)? = null

    private var autoScrolling = false
    private var autoScrollRemainder = 0f
    private var autoScrollLastFrameNanos = 0L

    private val autoScrollFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!autoScrolling) return
            if (autoScrollLastFrameNanos != 0L) {
                val seconds = (frameTimeNanos - autoScrollLastFrameNanos) / 1_000_000_000f
                val pxPerSecond = activity.resources.displayMetrics.density * rPreferences.rAutoScrollSpeed().get()
                autoScrollRemainder += pxPerSecond * seconds
                val px = autoScrollRemainder.toInt()
                if (px > 0) {
                    autoScrollRemainder -= px
                    recycler.scrollBy(0, px)
                }
            }
            autoScrollLastFrameNanos = frameTimeNanos
            if (!recycler.canScrollVertically(1)) {
                stopAutoScroll()
                return
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) recycler.setItemViewCacheSize(RECYCLER_VIEW_CACHE_SIZE)
        recycler.isVisible = false // Don't let the recycler layout yet
        recycler.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        recycler.itemAnimator = null
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    // The user grabbed the page: stop moving by ourselves.
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) stopAutoScroll()
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    onScrolled()

                    if (dy > config.menuThreshold || dy < -config.menuThreshold) {
                        activity.hideMenu()
                    }

                    if (dy < 0) {
                        val firstIndex = layoutManager.findFirstVisibleItemPosition()
                        val firstItem = adapter.items.getOrNull(firstIndex)
                        if (firstItem is ChapterTransition.Prev && firstItem.to != null) {
                            activity.requestPreloadChapter(firstItem.to)
                        }
                    }

                    val lastIndex = layoutManager.findLastEndVisibleItemPosition()
                    val lastItem = adapter.items.getOrNull(lastIndex)
                    if (lastItem is ChapterTransition.Next && lastItem.to == null) {
                        activity.showMenu()
                    }
                }
            },
        )
        recycler.tapListener = f@{ event ->
            val viewPosition = IntArray(2)
            recycler.getLocationOnScreen(viewPosition)
            val viewPositionRelativeToWindow = IntArray(2)
            recycler.getLocationInWindow(viewPositionRelativeToWindow)
            val pos = PointF(
                (event.rawX - viewPosition[0] + viewPositionRelativeToWindow[0]) / recycler.width,
                (event.rawY - viewPosition[1] + viewPositionRelativeToWindow[1]) / recycler.originalHeight,
            )
            // R: bottom half taps scroll by a jump (Settings > R > القارئ)
            // the middle (menu) region always toggles the tools; the jump zones only own the rest of the bottom half
            val rDir = if (config.navigator.getAction(pos) == ViewerNavigation.NavigationRegion.MENU) 0 else rZoneDirection(pos.x, pos.y)
            if (rDir != 0) {
                val dy = rDir * rJumpPx()
                // R: the jump takes as long as the tools card speed says (same speed as the hold and the auto scroll)
                val ms = (kotlin.math.abs(dy) / rZonePxPerSecond() * 1000f).toInt().coerceIn(150, 3000)
                recycler.smoothScrollBy(0, dy, android.view.animation.DecelerateInterpolator(), ms)
                return@f
            }
            when (config.navigator.getAction(pos)) {
                ViewerNavigation.NavigationRegion.MENU -> activity.toggleMenu()
                ViewerNavigation.NavigationRegion.NEXT, ViewerNavigation.NavigationRegion.RIGHT -> moveToNext()
                ViewerNavigation.NavigationRegion.PREV, ViewerNavigation.NavigationRegion.LEFT -> moveToPrevious()
            }
        }
        recycler.longTapListener = f@{ event ->
            if (activity.menuVisible || config.longTapEnabled) {
                val child = recycler.findChildViewUnder(event.x, event.y)
                if (child != null) {
                    val position = recycler.getChildAdapterPosition(child)
                    val item = adapter.items.getOrNull(position)
                    if (item is ReaderPage) {
                        activity.onPageLongTap(item)
                        return@f true
                    }
                }
            }
            false
        }

        config.imagePropertyChangedListener = {
            refreshAdapter()
        }

        config.zoomPropertyChangedListener = {
            frame.enableZoomOut = it
        }

        config.doubleTapZoomChangedListener = {
            frame.doubleTapZoom = it
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayForNewUser
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }
        config.navigationModeInvertedListener = { activity.binding.navigationOverlay.showNavigationAgain() }

        // R: hold a finger in the bottom half to keep scrolling
        frame.holdDirection = { x, y -> if (rPreferences.rHoldScroll().get()) rZoneDirection(x, y) else 0 }
        frame.holdPxPerSecond = { rZonePxPerSecond() }
        frame.holdScrollBy = { px -> recycler.scrollBy(0, px) }

        frame.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        frame.addView(recycler)
    }

    /** R: +1 = scroll down, -1 = scroll up, 0 = outside the tap-scroll zone. [x], [y] are 0..1 fractions of the screen. */
    private fun rZoneDirection(x: Float, y: Float): Int {
        if (!rPreferences.rTapScroll().get() || y < 0.5f) return 0
        val right = x >= 0.5f
        return when (rPreferences.rTapScrollSide().get()) {
            1 -> if (right) -1 else 1
            2 -> 1
            else -> if (right) 1 else -1
        }
    }

    /**
     * R: speed of the bottom-corner scrolling (tap jump and hold). It follows the SPEED slider of the tools card
     * (`r_auto_scroll_speed`, dp per second); the old hold speed choice (5/10/15/25) is only a gear on top of it
     * (default 10 x slider 60 = 600 dp/s, about what the hold did before).
     */
    private fun rZonePxPerSecond(): Float {
        val density = activity.resources.displayMetrics.density
        val speed = rPreferences.rAutoScrollSpeed().get()
        val gear = rPreferences.rHoldScrollSpeed().get().coerceAtLeast(1)
        return (density * speed * gear).coerceAtLeast(1f)
    }

    private fun rJumpPx() = (recycler.height * rPreferences.rTapJump().get() / 100f).toInt().coerceAtLeast(1)

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
        val nextChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as? ReaderPage)?.chapter

        // Allow preload for
        // 1. Going between pages of same chapter
        // 2. Next chapter page
        return when (page.chapter) {
            (currentPage as? ReaderPage)?.chapter -> true
            nextChapter -> true
            else -> false
        }
    }

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View {
        return frame
    }

    /**
     * Destroys this viewer. Called when leaving the reader or swapping viewers.
     */
    override fun destroy() {
        super.destroy()
        stopAutoScroll()
        scope.cancel()
    }

    /** Starts the auto-scroll. Speed (dp/s) comes from Settings > R. */
    fun startAutoScroll() {
        if (autoScrolling) return
        autoScrolling = true
        autoScrollRemainder = 0f
        autoScrollLastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(autoScrollFrame)
        autoScrollListener?.invoke(true)
    }

    fun stopAutoScroll() {
        if (!autoScrolling) return
        autoScrolling = false
        Choreographer.getInstance().removeFrameCallback(autoScrollFrame)
        autoScrollListener?.invoke(false)
    }

    /** @return true if auto-scroll is now running. */
    fun toggleAutoScroll(): Boolean {
        if (autoScrolling) stopAutoScroll() else startAutoScroll()
        return autoScrolling
    }

    /**
     * Called from the RecyclerView listener when a [page] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onPageSelected(page: ReaderPage, allowPreload: Boolean) {
        val pages = page.chapter.pages ?: return
        Logger.d { "onPageSelected: ${page.number}/${pages.size}" }
        activity.onPageSelected(page, false)

        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            Logger.d { "Request preload next chapter because we're at page ${page.number} of ${pages.size}" }
            val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
            val transitionChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as?ReaderPage)?.chapter
            if (transitionChapter != null) {
                Logger.d { "Requesting to preload chapter ${transitionChapter.chapter.chapter_number}" }
                activity.requestPreloadChapter(transitionChapter)
            }
        }
    }

    /**
     * Called from the RecyclerView listener when a [transition] is marked as active. It request the
     * preload of the destination chapter of the transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        Logger.d { "onTransitionSelected: $transition" }
        val toChapter = transition.to
        if (toChapter != null) {
            Logger.d { "Request preload destination chapter because we're on the transition" }
            activity.requestPreloadChapter(toChapter)
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active.
     */
    override fun setChapters(chapters: ViewerChapters) {
        Logger.d { "setChapters" }
        val forceTransition = config.alwaysShowChapterTransition || currentPage is ChapterTransition
        adapter.setChapters(chapters, forceTransition)

        if (recycler.isGone) {
            Logger.d { "Recycler first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
            recycler.isVisible = true
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage, animated: Boolean) {
        Logger.d { "moveToPage" }
        val position = adapter.items.indexOf(page)
        if (position != -1) {
            recycler.scrollToPosition(position)
            if (layoutManager.findLastEndVisibleItemPosition() == -1) {
                onScrolled(position)
            }
        } else {
            Logger.d { "Page $page not found in adapter" }
        }
    }

    fun onScrolled(pos: Int? = null) {
        val position = pos ?: layoutManager.findLastEndVisibleItemPosition()
        val item = adapter.items.getOrNull(position)
        val allowPreload = checkAllowPreload(item as? ReaderPage)
        if (item != null && currentPage != item) {
            currentPage = item
            when (item) {
                is ReaderPage -> onPageSelected(item, allowPreload)
                is ChapterTransition -> onTransitionSelected(item)
            }
        }
    }

    /**
     * Scrolls up by [scrollDistance].
     */
    override fun moveToPrevious() {
        recycler.smoothScrollBy(0, -scrollDistance)
    }

    /**
     * Scrolls down by [scrollDistance].
     */
    override fun moveToNext() {
        recycler.smoothScrollBy(0, scrollDistance)
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveToNext() else moveToPrevious()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveToPrevious() else moveToNext()
                }
            }
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            -> if (isUp) moveToPrevious()

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            -> if (isUp) moveToNext()
            else -> return false
        }
        return true
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        return false
    }

    /**
     * Notifies adapter of changes around the current page to trigger a relayout in the recycler.
     * Used when an image configuration is changed.
     */
    private fun refreshAdapter() {
        val position = layoutManager.findLastEndVisibleItemPosition()
        adapter.notifyItemRangeChanged(
            max(0, position - 3),
            min(position + 3, adapter.itemCount - 1),
        )
    }
}

private const val RECYCLER_VIEW_CACHE_SIZE = 4
