package eu.kanade.tachiyomi.data.preference

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.core.storage.preference.asDateFormat
import eu.kanade.tachiyomi.data.updater.AppDownloadInstallJob
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.ui.reader.settings.PageLayout
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.settings.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.recents.RecentsPresenter
import eu.kanade.tachiyomi.util.system.Themes
import java.text.DateFormat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

fun <T> Preference<T>.changesIn(scope: CoroutineScope, block: (value: T) -> Unit): Job {
    block(get())
    return changes()
        .onEach { block(it) }
        .launchIn(scope)
}

fun Preference<Boolean>.toggle() = set(!get())

operator fun <T> Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

operator fun <T> Preference<Set<T>>.plusAssign(item: Collection<T>) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: Collection<T>) {
    set(get() - item)
}

class PreferencesHelper(val context: Context, val preferenceStore: PreferenceStore) {

    fun getInt(key: String, default: Int) = preferenceStore.getInt(key, default)
    fun getStringPref(key: String, default: String = "") = preferenceStore.getString(key, default)
    fun getStringSet(key: String, default: Set<String>) = preferenceStore.getStringSet(key, default)

    fun startingTab() = preferenceStore.getInt(Keys.startingTab, 0)
    fun backReturnsToStart() = preferenceStore.getBoolean(Keys.backToStart, true)

    fun hasShownNotifPermission() = preferenceStore.getBoolean("has_shown_notification_permission", false)

    fun hasDeniedA11FilePermission() = preferenceStore.getBoolean(Keys.deniedA11FilePermission, false)

    fun nightMode() = preferenceStore.getInt(Keys.nightMode, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun themeDarkAmoled() = preferenceStore.getBoolean(Keys.themeDarkAmoled, false)

    private val supportsDynamic = DynamicColors.isDynamicColorAvailable()
    fun lightTheme() = preferenceStore.getEnum(Keys.lightTheme, if (supportsDynamic) Themes.MONET else Themes.DEFAULT)
    fun darkTheme() = preferenceStore.getEnum(Keys.darkTheme, if (supportsDynamic) Themes.MONET else Themes.DEFAULT)

    fun pageTransitions() = preferenceStore.getBoolean(Keys.enableTransitions, true)

    fun doubleTapAnimSpeed() = preferenceStore.getInt(Keys.doubleTapAnimationSpeed, 500)

    fun showPageNumber() = preferenceStore.getBoolean(Keys.showPageNumber, true)

    fun fullscreen() = preferenceStore.getBoolean(Keys.fullscreen, true)

    fun keepScreenOn() = preferenceStore.getBoolean(Keys.keepScreenOn, true)

    fun customBrightness() = preferenceStore.getBoolean(Keys.customBrightness, false)

    fun customBrightnessValue() = preferenceStore.getInt(Keys.customBrightnessValue, 0)

    fun colorFilter() = preferenceStore.getBoolean(Keys.colorFilter, false)

    fun colorFilterValue() = preferenceStore.getInt(Keys.colorFilterValue, 0)

    fun colorFilterMode() = preferenceStore.getInt(Keys.colorFilterMode, 0)

    fun defaultReadingMode() = preferenceStore.getInt(Keys.defaultReadingMode, ReadingModeType.RIGHT_TO_LEFT.flagValue)

    fun defaultOrientationType() = preferenceStore.getInt(Keys.defaultOrientationType, OrientationType.FREE.flagValue)

    fun imageScaleType() = preferenceStore.getInt(Keys.imageScaleType, 1)

    fun zoomStart() = preferenceStore.getInt(Keys.zoomStart, 1)

    fun readerTheme() = preferenceStore.getInt(Keys.readerTheme, 2)

    fun cropBorders() = preferenceStore.getBoolean(Keys.cropBorders, false)

    fun cropBordersWebtoon() = preferenceStore.getBoolean(Keys.cropBordersWebtoon, false)

    fun navigateToPan() = preferenceStore.getBoolean("navigate_pan", true)

    fun landscapeZoom() = preferenceStore.getBoolean("landscape_zoom", false)

    fun grayscale() = preferenceStore.getBoolean("pref_grayscale", false)

    fun invertedColors() = preferenceStore.getBoolean("pref_inverted_colors", false)

    fun webtoonSidePadding() = preferenceStore.getInt(Keys.webtoonSidePadding, 0)

    fun webtoonEnableZoomOut() = preferenceStore.getBoolean(Keys.webtoonEnableZoomOut, false)

    fun readWithLongTap() = preferenceStore.getBoolean(Keys.readWithLongTap, true)

    fun readWithVolumeKeys() = preferenceStore.getBoolean(Keys.readWithVolumeKeys, false)

    fun readWithVolumeKeysInverted() = preferenceStore.getBoolean(Keys.readWithVolumeKeysInverted, false)

    fun navigationModePager() = preferenceStore.getInt(Keys.navigationModePager, 0)

    fun navigationModeWebtoon() = preferenceStore.getInt(Keys.navigationModeWebtoon, 0)

    fun pagerNavInverted() = preferenceStore.getEnum(Keys.pagerNavInverted, ViewerNavigation.TappingInvertMode.NONE)

    fun webtoonNavInverted() = preferenceStore.getEnum(Keys.webtoonNavInverted, ViewerNavigation.TappingInvertMode.NONE)

    fun pageLayout() = preferenceStore.getInt(Keys.pageLayout, PageLayout.AUTOMATIC.value)

    fun automaticSplitsPage() = preferenceStore.getBoolean(Keys.automaticSplitsPage, false)

    fun invertDoublePages() = preferenceStore.getBoolean(Keys.invertDoublePages, false)

    fun webtoonPageLayout() = preferenceStore.getInt(Keys.webtoonPageLayout, PageLayout.SINGLE_PAGE.value)

    fun webtoonReaderHideThreshold() = preferenceStore.getEnum("reader_hide_threshold", Values.ReaderHideThreshold.LOW)

    fun webtoonInvertDoublePages() = preferenceStore.getBoolean(Keys.webtoonInvertDoublePages, false)

    fun readerBottomButtons() = preferenceStore.getStringSet(
        Keys.readerBottomButtons,
        ReaderBottomButton.BUTTONS_DEFAULTS,
    )

    fun showNavigationOverlayNewUser() = preferenceStore.getBoolean(Keys.showNavigationOverlayNewUser, true)

    fun showNavigationOverlayNewUserWebtoon() = preferenceStore.getBoolean(Keys.showNavigationOverlayNewUserWebtoon, true)

    fun preloadSize() = preferenceStore.getInt(Keys.preloadSize, 6)

    fun autoUpdateTrack() = preferenceStore.getBoolean(Keys.autoUpdateTrack, true)

    fun trackMarkedAsRead() = preferenceStore.getBoolean(Keys.trackMarkedAsRead, false)

    fun trackingsToAddOnline() = preferenceStore.getStringSet(Keys.trackingsToAddOnline, emptySet())

    // TODO: SourcePref
    fun lastUsedCatalogueSource() = preferenceStore.getLong(Keys.lastUsedCatalogueSource, -1)

    fun lastUsedCategory() = preferenceStore.getInt(Keys.lastUsedCategory, 0)

    // TODO: SourcePref
    fun lastUsedSources() = preferenceStore.getStringSet("last_used_sources", emptySet())

    fun lastVersionCode() = preferenceStore.getInt("last_version_code", 0)

    fun browseAsList() = preferenceStore.getBoolean(Keys.catalogueAsList, false)

    // TODO: SourcePref
    fun enabledLanguages() = preferenceStore.getStringSet(
        Keys.enabledLanguages,
        setOfNotNull("all", "en", Locale.getDefault().language.takeIf { !it.startsWith("en") }),
    )

    // TODO: SourcePref
    fun sourceSorting() = preferenceStore.getInt(Keys.sourcesSort, 0)

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", "POINT_10")

    fun dateFormatRaw() = preferenceStore.getString(Keys.dateFormat, "")

    @Deprecated("Use dateFormatRaw().get().asDateFormat() instead")
    fun dateFormat(format: String = dateFormatRaw().get()): DateFormat = format.asDateFormat()

    fun appLanguage() = preferenceStore.getString("app_language", "")

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean(Keys.downloadOnlyOverWifi, true)

    fun folderPerManga() = preferenceStore.getBoolean("create_folder_per_manga", false)

    fun librarySearchSuggestion() = preferenceStore.getString(Keys.librarySearchSuggestion, "")

    fun showLibrarySearchSuggestions() = preferenceStore.getBoolean(Keys.showLibrarySearchSuggestions, false)

    fun lastLibrarySuggestion() = preferenceStore.getLong("last_library_suggestion", 0L)

    fun removeAfterReadSlots() = preferenceStore.getInt(Keys.removeAfterReadSlots, -1)

    fun removeAfterMarkedAsRead() = preferenceStore.getBoolean(Keys.removeAfterMarkedAsRead, false)

    fun libraryUpdateInterval() = preferenceStore.getInt(Keys.libraryUpdateInterval, 24)

    fun libraryUpdateLastTimestamp() = preferenceStore.getLong("library_update_last_timestamp", 0L)

    fun libraryUpdateDeviceRestriction() = preferenceStore.getStringSet("library_update_restriction", setOf(DEVICE_ONLY_ON_WIFI))

    fun libraryUpdateMangaRestriction() = preferenceStore.getStringSet("library_update_manga_restriction", setOf(MANGA_HAS_UNREAD, MANGA_NON_COMPLETED, MANGA_NON_READ))

    fun libraryUpdateCategories() = preferenceStore.getStringSet("library_update_categories", emptySet())
    fun libraryUpdateCategoriesExclude() = preferenceStore.getStringSet("library_update_categories_exclude", emptySet())

    fun libraryLayout() = preferenceStore.getInt(Keys.libraryLayout, LibraryItem.LAYOUT_COMFORTABLE_GRID)

    fun gridSize() = preferenceStore.getFloat(Keys.gridSize, 1f)

    fun downloadBadge() = preferenceStore.getBoolean(Keys.downloadBadge, false)

    fun languageBadge() = preferenceStore.getBoolean(Keys.languageBadge, false)

    fun filterDownloaded() = preferenceStore.getInt(Keys.filterDownloaded, 0)

    fun filterUnread() = preferenceStore.getInt(Keys.filterUnread, 0)

    fun filterCompleted() = preferenceStore.getInt(Keys.filterCompleted, 0)

    fun filterBookmarked() = preferenceStore.getInt("pref_filter_bookmarked_key", 0)

    fun filterTracked() = preferenceStore.getInt(Keys.filterTracked, 0)

    fun filterMangaType() = preferenceStore.getInt(Keys.filterMangaType, 0)

    fun filterContentType() = preferenceStore.getInt("pref_filter_content_type_key", 0)

    fun showEmptyCategoriesWhileFiltering() = preferenceStore.getBoolean(Keys.showEmptyCategoriesFiltering, false)

    fun librarySortingMode() = preferenceStore.getInt("library_sorting_mode", 0)

    fun librarySortingAscending() = preferenceStore.getBoolean("library_sorting_ascending", true)

    fun automaticExtUpdates() = preferenceStore.getBoolean(Keys.automaticExtUpdates, true)

    // TODO: SourcePref
    fun installedExtensionsOrder() = preferenceStore.getInt(Keys.installedExtensionsOrder, InstalledExtensionsOrder.Name.value)

    // TODO: SourcePref
    fun migrationSourceOrder() = preferenceStore.getInt("migration_source_order", Values.MigrationSourceOrder.Alphabetically.value)

    fun collapsedCategories() = preferenceStore.getStringSet("collapsed_categories", mutableSetOf())

    fun collapsedDynamicCategories() = preferenceStore.getStringSet("collapsed_dynamic_categories", mutableSetOf())

    fun collapsedDynamicAtBottom() = preferenceStore.getBoolean("collapsed_dynamic_at_bottom", false)

    // TODO: SourcePref
    fun hiddenSources() = preferenceStore.getStringSet("hidden_catalogues", mutableSetOf())

    // TODO: SourcePref
    fun pinnedCatalogues() = preferenceStore.getStringSet("pinned_catalogues", mutableSetOf())

    fun saveChaptersAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", true)

    // "R" modifications (Settings > R). Every key starts with "r_". Default = our modified behavior;
    // switching it off restores the original Yokai behavior.
    fun rFixSplitTallImageError() = preferenceStore.getBoolean("r_fix_split_tall_image_error", true)

    /** Reader: auto-scroll button in the webtoon (continuous) reader. */
    fun rAutoScroll() = preferenceStore.getBoolean("r_auto_scroll", true)

    /** Reader: auto-scroll speed in dp per second. */
    fun rAutoScrollSpeed() = preferenceStore.getInt("r_auto_scroll_speed", 60)

    /** Appearance: hide the app icon picker so the app has a single icon (the R logo). */
    fun rSingleAppIcon() = preferenceStore.getBoolean("r_single_app_icon", true)

    /** Home screen (library): the floating snake mascot you can grab and throw. */
    fun rDragonsOff() = preferenceStore.getBoolean("r_dragons_off", false) // master switch: hides the small home dragon AND the big reader/stats dragon; profile, XP, badges stay
    fun rMascot() = preferenceStore.getBoolean("r_mascot", true)

    /** Mascot: small talking blips (one per letter) while a speech bubble types out; silent in silent/vibrate mode. */
    /** Mascot: tapping the little dragon turns him into a random character (knight, ninja, pirate, detective) in a cloud of smoke. */
    fun rBadgesClaimed() = preferenceStore.getStringSet("r_badges_claimed", emptySet()) // badge ids whose XP was collected
    fun rChestDay() = preferenceStore.getString("r_chest_day", "") // yyyy-MM-dd of the last opened daily chest
    fun rChestStreak() = preferenceStore.getInt("r_chest_streak", 0) // day 1..7 of the 7-day cycle that was opened last
    fun rShopOwned() = preferenceStore.getStringSet("r_shop_owned", emptySet()) // bought cosmetic item ids
    fun rShopFrame() = preferenceStore.getString("r_shop_frame", "") // equipped profile frame id
    fun rShopTitle() = preferenceStore.getString("r_shop_title", "") // equipped title id
    fun rShopCobra() = preferenceStore.getString("r_shop_cobra", "") // equipped cobra outfit id
    fun rShopCounter() = preferenceStore.getString("r_shop_counter", "") // equipped reader counter theme id
    fun rBoostUntil() = preferenceStore.getLong("r_boost_until", 0L) // XP x2 booster active until (ms)
    fun rComboShields() = preferenceStore.getInt("r_combo_shields", 0) // combo protections in the bag
    fun rExtraFav() = preferenceStore.getInt("r_extra_fav", 0) // bought favourite slot packs (+5 each, max 3)
    fun rChestWeek() = preferenceStore.getString("r_chest_week", "") // ISO week of the last opened weekly chest
    fun rFavManga() = preferenceStore.getString("r_fav_manga", "") // up to 3 "source|url" keys shown on the profile, "\n"-separated
    fun rSystemBrightness() = preferenceStore.getBoolean("r_system_brightness", true) // the reader brightness slider also changes the PHONE brightness (needs the "modify system settings" permission)
    fun rMascotEvolve() = preferenceStore.getBoolean("r_mascot_evolve", true) // egg evolution of the home dragon on level-up
    fun rDragonSeenLevel() = preferenceStore.getInt("r_dragon_seen_level", 0) // last level the evolution was shown for (0 = not set yet)
    fun rMascotTransform() = preferenceStore.getBoolean("r_mascot_transform", true)

    /** The little dragon walks by himself between a few safe spots (edges and the free middle), never over the bars. */
    fun rMascotWander() = preferenceStore.getBoolean("r_mascot_wander", true)

    /**
     * The small dragon's size in dp. ONE value for the home screen and the reader (the dragon, the reader visits and
     * the stick scene), so the two are always exactly the same size.
     */
    fun rDragonSize() = preferenceStore.getInt("r_dragon_size", 72)

    /** Transformation costumes already shown in the current round (indexes, comma-separated, in order). */
    fun rMascotTfSeen() = preferenceStore.getString("r_mascot_tf_seen", "")

    fun rMascotSound() = preferenceStore.getBoolean("r_mascot_sound", true)

    /** Mascot: after a minute without touching the phone the little dragon says goodnight, pulls a blanket up and sleeps. */
    fun rMascotSleep() = preferenceStore.getBoolean("r_mascot_sleep", true)

    // R: the little dragon's clips from the 2026-11-23 sheets (each has its own switch in Settings > R)
    fun rMascotGreet() = preferenceStore.getBoolean("r_mascot_greet", true) // SA: waves when a manga page opens
    fun rMascotNight() = preferenceStore.getBoolean("r_mascot_night", true) // SD: yawns in a sleep cap between 00:00 and 04:59
    fun rMascotGoalSpin() = preferenceStore.getBoolean("r_mascot_goal_spin", true) // SC + SC2: spins when the weekly goal is passed
    fun rMascotStreak() = preferenceStore.getBoolean("r_mascot_streak", true) // SF: 3 / 7 / 30 day streak, once a day
    fun rMascotFriday() = preferenceStore.getBoolean("r_mascot_friday", true) // SG: Friday outfit and a short reminder
    fun rDragonXp() = preferenceStore.getInt("r_dragon_xp", 0) // chapters + 4 x hours read, refreshed whenever the stats are read (RDragonLevel)
    fun rMascotClipDays() = preferenceStore.getString("r_mascot_clip_days", "") // "streak=<epochDay>;goal=<epochDay of the week start>;friday=<epochDay>;night=<epochDay>"
    fun rMascotFall() = preferenceStore.getBoolean("r_mascot_fall", true)

    /** The little dragon in the chapter-end card and in the statistics hovers (bob, glow shadow, sparkle trail) and points at its text. */
    fun rDragonXpL() = preferenceStore.getLong("r_dragon_xp_l", 0L) // total XP, unlimited (Long); the Int `r_dragon_xp` is only read once to migrate
    fun rDragonXpBonusL() = preferenceStore.getLong("r_dragon_xp_bonus_l", 0L) // combo + badges + chest XP, unlimited
    fun rGoldSpent() = preferenceStore.getLong("r_gold_spent", 0L) // gold coins spent in the (future) shop; balance = XP/1000 - spent
    fun rDragonXpBonus() = preferenceStore.getInt("r_dragon_xp_bonus", 0)
    fun rXpCounter() = preferenceStore.getBoolean("r_xp_counter", true)
    fun rHammerMode() = preferenceStore.getBoolean("r_hammer_mode", false)
    fun rRankCard() = preferenceStore.getBoolean("r_rank_card", true) // compact green rank card on top of the overflow menu
    fun rDragonOutfit() = preferenceStore.getString("r_dragon_outfit", "") // costume name (r_avc_<name>) used as the rank-card avatar, "" = stage portrait
    fun rDragonFloat() = preferenceStore.getBoolean("r_dragon_float", true)

    /** Mascot: when the app is opened the little dragon drops in from the top right and says how long you were away. */
    fun rMascotWelcome() = preferenceStore.getBoolean("r_mascot_welcome", true)

    /** Home welcome drop-in with the E2 sheet frames (falling, squash, bounce, waving); off = the previous wave. */
    fun rWelcomeE2() = preferenceStore.getBoolean("r_welcome_e2", true)

    /** When the app was last on screen (epoch ms), used by the welcome. */
    fun rLastOpenMs() = preferenceStore.getLong("r_last_open_ms", 0L)

    /** Reader scene with the 2026-11-22 sheets (walk, hit, squash, drag); off = the previous scene. */
    fun rSceneV2() = preferenceStore.getBoolean("r_scene_v2", true)

    /** Settings screens drawn as theme-tinted rounded cards, one per category. */
    fun rSettingsStyle() = preferenceStore.getBoolean("r_settings_style", true)

    /** Reader (webtoon): tap the bottom half of the screen to scroll; the side decides the direction. */
    // v2 key: the owner does not want tapping the top/bottom half to scroll (it swallowed the middle tap), so it starts OFF for everyone
    fun rTapScroll() = preferenceStore.getBoolean("r_tap_scroll_v2", false)

    /** 0 = bottom right scrolls down / bottom left up, 1 = the opposite, 2 = both bottom halves scroll down. */
    fun rTapScrollSide() = preferenceStore.getInt("r_tap_scroll_side", 0)

    /** Jump per tap, percent of the screen height. */
    fun rTapJump() = preferenceStore.getInt("r_tap_jump", 85)

    /** Hold a finger in the bottom half to keep scrolling. */
    fun rHoldScroll() = preferenceStore.getBoolean("r_hold_scroll", true)

    /** Hold-scroll speed in tenths of a screen per second. */
    fun rHoldScrollSpeed() = preferenceStore.getInt("r_hold_scroll_speed", 10)
    fun rToolsStyle() = preferenceStore.getInt("r_tools_style", 0) // reader tools: 0 full card, 1 thin bar, 2 floating capsule, 3 side strip
    fun rToolsClean() = preferenceStore.getBoolean("r_tools_clean", true) // while the tools card is open: no second page number, no second clock/battery pill

    /** Reader: hide the toolbar by itself after N seconds (0 = never). */
    fun rAutoHideTools() = preferenceStore.getInt("r_autohide_tools", 0)

    /** Reader: small clock + battery level in the corner. */
    fun rClockBattery() = preferenceStore.getBoolean("r_clock_battery", false)

    /** Stats profile: round picture + banners (flipping every few seconds) + name, at the top of the statistics. */
    fun rStatsProfile() = preferenceStore.getBoolean("r_stats_profile", true)
    fun rProfileName() = preferenceStore.getString("r_profile_name", "")
    fun rProfileAvatar() = preferenceStore.getString("r_profile_avatar", "")
    fun rProfileBanners() = preferenceStore.getString("r_profile_banners", "") // paths, one per line
    fun rProfileInterval() = preferenceStore.getInt("r_profile_interval", 5)

    /** Reader tools bar under the toolbar: speed, brightness, volume, battery icons with small panels. */
    fun rToolsBar() = preferenceStore.getBoolean("r_tools_bar", true)
    /** R: the original page bar (seekbar + page numbers) of the reader. Off by default: the new tools card replaces it. */
    /** R: side page bar back (no auto-scroll button), arrow that opens the old toolbar as a strip, old bottom bar hidden. */
    fun rSideTools() = preferenceStore.getBoolean("r_side_tools", true)

    /** Reader: tapping the middle shows only the app's bars; the phone's 3 buttons come only with a swipe up from the bottom edge. */
    fun rHideNavButtons() = preferenceStore.getBoolean("r_hide_nav_buttons", true)

    fun rOldToolbar() = preferenceStore.getBoolean("r_old_toolbar", false)

    /** Reader: thin permanent progress bar at the bottom edge. */
    fun rProgressBar() = preferenceStore.getBoolean("r_progress_bar", false)

    /** Stats: extra sections (streak, weekly goal, top 5, hours, month comparison, genre/source donut). */
    fun rStatsExtra() = preferenceStore.getBoolean("r_stats_extra", true)

    /** Stats: weekly goal in chapters (0 = no goal set). */
    fun rWeeklyGoal() = preferenceStore.getInt("r_weekly_goal", 0)

    /** Library: the 3-dot menu gets "hide / show lists"; lists (categories) he hides stay hidden until he shows them again. */
    fun rHideLists() = preferenceStore.getBoolean("r_hide_lists", true)

    /** Ids (as strings) of the library lists the owner hid from the 3-dot menu. */
    fun rHiddenLists() = preferenceStore.getStringSet("r_hidden_lists", mutableSetOf())

    /** Reader: the green cobra that peeks in from the left edge while you read. */
    fun rReaderCobra() = preferenceStore.getBoolean("r_reader_cobra", true)

    /** Reader cobra: typing and movement sounds. */
    fun rReaderCobraSound() = preferenceStore.getBoolean("r_reader_cobra_sound", true)

    /** Reader: every Nth visit the wise dragon hits the little dragon with his stick and drags him away. */
    fun rReaderCobraScene() = preferenceStore.getBoolean("r_reader_cobra_scene", true)

    /** Reader: the stick scene comes on every Nth visit (the little dragon visits N-1 times first). */
    fun rReaderCobraSceneEvery() = preferenceStore.getInt("r_reader_cobra_scene_every", 3)

    /** Reader cobra: sleepy cobra that tells you to go to bed between 00:00 and 04:59. */
    fun rReaderCobraNight() = preferenceStore.getBoolean("r_reader_cobra_night", true)

    /** Reader cobra: minutes between visits (randomized by +-20 %). */
    fun rReaderCobraInterval() = preferenceStore.getInt("r_reader_cobra_interval", 6) // no longer used: visits are per chapter now

    /** Reader cobra: he visits once every this many chapters (instead of every few minutes). */
    fun rReaderCobraChapters() = preferenceStore.getInt("r_reader_cobra_chapters", 2)

    /** Reader cobra: minutes both dragons stay away after the wise dragon's scene. */
    fun rReaderCobraCooldown() = preferenceStore.getInt("r_reader_cobra_cooldown", 10)

    /** Language: open the app in Arabic unless another language was picked in Settings > General. */
    fun rForceArabic() = preferenceStore.getBoolean("r_force_arabic", true)

    /** Reader: the page / chapter / auto-scroll bar is a vertical bar on the right edge (off = original horizontal bar). */
    fun rVerticalNav() = preferenceStore.getBoolean("r_vertical_nav", true)

    /** Reader: card with the time spent in the chapter you just finished, shown between chapters. */
    fun rChapterTimeCard() = preferenceStore.getBoolean("r_chapter_time_card", true)

    /** Library: pin manga to the top of their category (selection menu action, pin marker on the title). */
    fun rPinManga() = preferenceStore.getBoolean("r_pin_manga", true)

    /** Downloads: also find folders written by Mihon (hash suffix, non-ASCII characters written as hex). */
    fun rMihonFolders() = preferenceStore.getBoolean("r_mihon_folders", true)

    /** Statistics: show minutes in the reading time even when it is more than a day. */
    fun rStatsMinutes() = preferenceStore.getBoolean("r_stats_minutes", true)
    /** R: personal notes per manga, JSON {"sourceId:url": "text"}; a normal preference so the backup carries it. */
    /** R: chapter notes, tags and the reading log (JSON in preferences, so the backup carries them). */
    fun rChapterNotes() = preferenceStore.getString("r_chapter_notes", "{}")
    fun rMangaTags() = preferenceStore.getString("r_manga_tags", "{}")
    fun rReadLog() = preferenceStore.getString("r_read_log", "{}")
    fun rReminder() = preferenceStore.getBoolean("r_reminder", true)
    fun rReminderDays() = preferenceStore.getInt("r_reminder_days", 3)
    fun rMangaNotes() = preferenceStore.getString("r_manga_notes", "{}")
    fun rColorCards() = preferenceStore.getBoolean("r_color_cards", false) // R: colored title strip under library covers

    /** Mascot: the wise old snake sometimes shows up and gives useless advice (a joke). */
    fun rMascotSage() = preferenceStore.getBoolean("r_mascot_sage", true)

    /** Mascot: how often (percent of taps) the wise old snake shows up. */
    fun rMascotSageChance() = preferenceStore.getInt("r_mascot_sage_chance", 35)

    fun downloadNewChapters() = preferenceStore.getBoolean(Keys.downloadNew, false)

    fun downloadNewChaptersInCategories() = preferenceStore.getStringSet("download_new_categories", emptySet())
    fun excludeCategoriesInDownloadNew() = preferenceStore.getStringSet("download_new_categories_exclude", emptySet())

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)

    fun defaultCategory() = preferenceStore.getInt(Keys.defaultCategory, -2)

    fun skipRead() = preferenceStore.getBoolean(Keys.skipRead, false)

    fun skipFiltered() = preferenceStore.getBoolean(Keys.skipFiltered, true)

    fun skipDupe() = preferenceStore.getBoolean("skip_dupe", false)

    fun lockAfter() = preferenceStore.getInt(Keys.lockAfter, 0)

    fun lastUnlock() = preferenceStore.getLong(Keys.lastUnlock, 0)

    fun secureScreen() = preferenceStore.getEnum("secure_screen_v2", Values.SecureScreenMode.INCOGNITO)

    fun hideNotificationContent() = preferenceStore.getBoolean(Keys.hideNotificationContent, false)

    fun removeArticles() = preferenceStore.getBoolean(Keys.removeArticles, false)

    fun migrateFlags() = preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)

    // using string instead of set so it is ordered
    // TODO: SourcePref
    fun migrationSources() = preferenceStore.getString("migrate_sources", "")

    // TODO: SourcePref
    fun useSourceWithMost() = preferenceStore.getBoolean("use_source_with_most", false)

    fun skipPreMigration() = preferenceStore.getBoolean(Keys.skipPreMigration, false)

    fun defaultMangaOrder() = preferenceStore.getString("default_manga_order", "")

    fun refreshCoversToo() = preferenceStore.getBoolean(Keys.refreshCoversToo, true)

    // TODO: SourcePref
    fun extensionUpdatesCount() = preferenceStore.getInt("ext_updates_count", 0)

    fun showUpdatedTime() = preferenceStore.getBoolean(Keys.showUpdatedTime, false)

    fun sortFetchedTime() = preferenceStore.getBoolean("sort_fetched_time", false)

    fun collapseGroupedUpdates() = preferenceStore.getBoolean("group_chapters_updates", false)

    fun groupChaptersHistory() = preferenceStore.getEnum("group_chapters_history_type", RecentsPresenter.GroupType.ByWeek)

    fun collapseGroupedHistory() = preferenceStore.getBoolean("collapse_group_history", true)

    fun lastExtCheck() = preferenceStore.getLong("last_ext_check", 0)

    fun lastAppCheck() = preferenceStore.getLong("last_app_check", 0)

    fun checkForBetas() = preferenceStore.getBoolean("check_for_betas", BuildConfig.BETA)

    fun unreadBadgeType() = preferenceStore.getInt("unread_badge_type", 2)

    fun categoryNumberOfItems() = preferenceStore.getBoolean(Keys.categoryNumberOfItems, false)

    fun hideStartReadingButton() = preferenceStore.getBoolean("hide_reading_button", false)

    fun alwaysShowChapterTransition() = preferenceStore.getBoolean(Keys.alwaysShowChapterTransition, true)

    fun deleteRemovedChapters() = preferenceStore.getInt(Keys.deleteRemovedChapters, 0)

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    fun removeExcludeCategories() = preferenceStore.getStringSet("remove_exclude_categories", emptySet())

    fun showAllCategories() = preferenceStore.getBoolean("show_all_categories", true)

    fun showAllCategoriesWhenSearchingSingleCategory() = preferenceStore.getBoolean("show_all_categories_when_searching_single_category", false)

    fun hopperGravity() = preferenceStore.getInt("hopper_gravity", 1)

    fun filterOrder() = preferenceStore.getString("filter_order", FilterBottomSheet.Filters.DEFAULT_ORDER)

    fun hopperLongPressAction() = preferenceStore.getInt(Keys.hopperLongPress, 0)

    fun hideHopper() = preferenceStore.getBoolean("hide_hopper", false)

    fun autohideHopper() = preferenceStore.getBoolean(Keys.autoHideHopper, true)

    fun groupLibraryBy() = preferenceStore.getInt("group_library_by", 0)

    fun showCategoryInTitle() = preferenceStore.getBoolean("category_in_title", false)

    fun onlySearchPinned() = preferenceStore.getBoolean(Keys.onlySearchPinned, false)

    fun hideInLibraryItems() = preferenceStore.getBoolean("browse_hide_in_library_items", false)

    // Tutorial preferences
    fun shownFilterTutorial() = preferenceStore.getBoolean("shown_filter_tutorial", false)

    fun shownChapterSwipeTutorial() = preferenceStore.getBoolean("shown_swipe_tutorial", false)

    fun shownDownloadQueueTutorial() = preferenceStore.getBoolean("shown_download_queue", false)

    fun shownLongPressCategoryTutorial() = preferenceStore.getBoolean("shown_long_press_category", false)

    fun shownHopperSwipeTutorial() = preferenceStore.getBoolean("shown_hopper_swipe", false)

    fun shownDownloadSwipeTutorial() = preferenceStore.getBoolean("shown_download_tutorial", false)

    fun hideBottomNavOnScroll() = preferenceStore.getBoolean(Keys.hideBottomNavOnScroll, true)

    fun sideNavIconAlignment() = preferenceStore.getInt(Keys.sideNavIconAlignment, 1)

    // TODO: SourcePref
    fun showNsfwSources() = preferenceStore.getBoolean(Keys.showNsfwSource, true)

    fun themeMangaDetails() = preferenceStore.getBoolean(Keys.themeMangaDetails, true)

    fun useLargeToolbar() = preferenceStore.getBoolean("use_large_toolbar", true)

    fun showSeriesInShortcuts() = preferenceStore.getBoolean(Keys.showSeriesInShortcuts, true)
    fun showSourcesInShortcuts() = preferenceStore.getBoolean(Keys.showSourcesInShortcuts, true)
    fun openChapterInShortcuts() = preferenceStore.getBoolean(Keys.openChapterInShortcuts, true)

    fun incognitoMode() = preferenceStore.getBoolean(Keys.incognitoMode, false)

    fun hasPromptedBeforeUpdateAll() = preferenceStore.getBoolean("has_prompted_update_all", false)

    fun sideNavMode() = preferenceStore.getInt(Keys.sideNavMode, 0)

    fun appShouldAutoUpdate() = preferenceStore.getInt(Keys.shouldAutoUpdate, AppDownloadInstallJob.ONLY_ON_UNMETERED)

    // TODO: SourcePref
    fun autoUpdateExtensions() = preferenceStore.getInt(Keys.autoUpdateExtensions, AppDownloadInstallJob.ONLY_ON_UNMETERED)

    fun filterChapterByRead() = preferenceStore.getInt(Keys.defaultChapterFilterByRead, Manga.SHOW_ALL)

    fun filterChapterByDownloaded() = preferenceStore.getInt(Keys.defaultChapterFilterByDownloaded, Manga.SHOW_ALL)

    fun filterChapterByBookmarked() = preferenceStore.getInt(Keys.defaultChapterFilterByBookmarked, Manga.SHOW_ALL)

    fun sortChapterOrder() = preferenceStore.getInt(Keys.defaultChapterSortBySourceOrNumber, Manga.CHAPTER_SORTING_SOURCE)

    fun hideChapterTitlesByDefault() = preferenceStore.getBoolean(Keys.hideChapterTitles, false)

    fun chaptersDescAsDefault() = preferenceStore.getBoolean(Keys.chaptersDescAsDefault, true)

    fun sortChapterByAscendingOrDescending() = preferenceStore.getInt(Keys.defaultChapterSortByAscendingOrDescending, Manga.CHAPTER_SORT_DESC)

    fun coverRatios() = preferenceStore.getStringSet(Keys.coverRatios, emptySet())

    fun coverColors() = preferenceStore.getStringSet(Keys.coverColors, emptySet())

    fun useStaggeredGrid() = preferenceStore.getBoolean("use_staggered_grid", false)
}
