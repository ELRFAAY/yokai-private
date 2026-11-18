# Yokai (private fork) — Handoff

Read this first when starting a new session or from another account. Update it after every meaningful change.
Last updated: 2026-11-18

## What this is
- Private fork of https://github.com/null2264/yokai (Android manga reader, Tachiyomi/Mihon lineage).
- Private repo: `ELRFAAY/yokai-private` (must stay private; everything done on this project is private).
- Base: upstream commit `843a59b` (2026-09-28), imported as one initial commit on `main`.
- A git remote named `upstream` points at the original repo (read-only, for pulling updates later).

## Decisions made by the owner
- The app will be installed **alongside the original Yokai**, as a separate app. So the `applicationId` must be changed, and so must everything that would clash with the original.
- Many changes are planned, including branding: **app name and logo** (plus more to come, to be listed below).
- Language of conversation with the owner: Egyptian Arabic.

## START HERE: current state and next steps (updated 2026-11-18, written because the previous account was near its usage limit)

### Branches in `ELRFAAY/yokai-private` (all pushed)
| Branch | Based on | Contents | Built/tested? |
|---|---|---|---|
| `main` | upstream `843a59b` | Clean Yokai + this handoff + `branding/icon-source.jpg`. No app-code changes. | n/a |
| `fix/download-split-not-found` | `main` | 1-line fix in `Downloader.splitTallImageIfNeeded` (`throw Error` -> `error`). | No |
| `feat/r-settings` | `fix/download-split-not-found` | "R" entry in main Settings (`SettingsRController`), pref `rFixSplitTallImageError`, download fix gated by it. **Includes the fix branch's commit.** | No |
| `feat/r-branding` | `main` (NOT stacked on the two above) | `applicationId` base -> `com.elrfaay.yokair`; `app_name` -> "Yokai R" (placeholder); new default launcher icon; old monochrome layer removed. | No |

To get everything in one app: merge `feat/r-settings` and `feat/r-branding` into a new build branch (e.g. `build/r`). They touch different files (Downloader/PreferencesHelper/SettingsMainController/new controller vs. build.gradle.kts/strings.xml/mipmap files), so conflicts are not expected. Nothing has been merged yet.

### Nothing has been compiled or run
The Claude session had no Android SDK, so no build was possible. Next account: first get a build. Options: (a) the owner builds locally with Android Studio / `./gradlew assembleStandardDebug`; (b) CI. The repo contains the upstream workflows in `.github/workflows/` (`build_check.yml`, `build_push.yml`, `mirror.yml`). They came from upstream and were never adapted; `build_push.yml` / `mirror.yml` likely need upstream secrets and may fail or do nothing useful in the private repo — read them before relying on them, and do not push anything that would publish or mirror this repo outside GitHub-private.

### Installing next to the original
- Final application ids with the current config: release `com.elrfaay.yokair.yokai`, debug `com.elrfaay.yokair.debugYokai`, nightly `com.elrfaay.yokair.nightlyYokai` (the build-type suffixes were left as upstream). The original app uses `eu.kanade.tachiyomi.yokai` (release) / `...debugYokai` / `...nightlyYokai`, so there is no clash.
- Every runtime use of the id goes through `BuildConfig.APPLICATION_ID` / `${applicationId}` (provider authority, icon aliases, notifications, backup file names), so changing the base id is safe in code. `namespace` stays `eu.kanade.tachiyomi` on purpose.
- The new app starts with an empty library (separate data). The owner can restore a Yokai backup into it.

### Icon work (done on `feat/r-branding`)
- Generated from `branding/icon-source.jpg` with a Pillow script (not stored in the repo). Rules used: legacy square icon = full source image resized (48/72/96/144/192 px); legacy round icon = source cropped to its circle; adaptive foreground = the circular badge (transparent outside the circle) scaled to 64% of the 108dp canvas (inside the 66dp safe zone), saved as lossless WebP at all 5 densities; background stays `@color/yokai_background` (#0E1418).
- Files replaced: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp`, `ic_launcher_round.webp`, `ic_launcher_foreground.webp`.
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`: the `<monochrome>` layer was removed (it was the old Yokai glyph; Android 13 "themed icons" would have shown it). If the owner wants themed icons, a monochrome version of the Y must be made.
- The other icons in the app's icon switcher (Blue, Orange, Gray, Classic) are untouched and still the original Yokai art.
- A preview image was checked by eye and looked right, but it has not been seen on a device.

### Open decisions to ask the owner
1. **Final app name.** Currently the placeholder "Yokai R" (only `app_name` was changed; `app_short_name` and `app_normalized_name` are still "Yōkai"/"Yokai").
2. **Shared storage folder.** `app_normalized_name` ("Yokai") is used by `AndroidStorageFolderProvider` for the default storage folder (downloads, backups). The new app would use the same folder name as the original, so the two apps may share or clash over downloads. Ask the owner whether they want that or a separate folder (changing `app_normalized_name` gives a separate one).
3. **Built-in updater.** The `standard` flavor has `INCLUDE_UPDATER = true`: it checks upstream releases, which are for the original app (different id). Probably should be disabled or put behind an R switch. Not touched yet.
4. **Extension "open in app" links** might be tied to the host package; not checked.
5. Downloads bug follow-ups still waiting on the owner: which source/extension, whether it fails on every source, a logcat, and whether turning off Downloads > "Split tall images" makes downloads work (that option exists, default on). Also the crash "download closes and reopens the app" on Xiaomi is not investigated.

### Suggested order for the next account
1. Read this file, then ask the owner the open decisions above (one short message).
2. Create `build/r` by merging `feat/r-settings` + `feat/r-branding`; get a build (see above); fix compile errors if any.
3. Owner installs it next to the original and tests: library refresh, download (a chapter that failed before, on the Xiaomi phone), browsing sources, extension updates, and the Settings > R switch.
4. Then continue with the owner's next modifications, each on its own branch behind an R switch, and keep the tables below updated.

## Hard requirement for every change
The app must stay fully usable after any modification:
- library refresh works
- downloading (and re-downloading) works
- browsing sources works
- **source (extension) updates keep working normally** — do not touch the extension loader, extension repos or extension installer unless the owner asks.

## Hard requirement #2: every change is switchable from an "R" settings section
Stated by the owner on 2026-11-18:
- Add a new section in the app's Settings named **"R"** (the owner also called it "R modifications" / "تعديلات R").
- **Every modification we make must be switchable there** — turn it off, turn it on, or get back the original app's behavior.
- **Anything new we add must have its own settings inside that section.**
- Practical meaning for any change: put it behind a preference (default = our new behavior), keep the original code path working when the switch is off, and list the switch in the table below.

### R switches (keep this table up to date)
| Switch (pref key) | What it controls | Default | Branch |
|---|---|---|---|
| `r_fix_split_tall_image_error` | Download: a missing page file during the tall-image split is only logged (on) vs. fails the page with "page not found during splitting" (off = original) | on | `feat/r-settings` |
| `r_auto_scroll` | Reader (continuous/webtoon mode only): play/pause auto-scroll button at the end of the reader nav row (on) vs. no button (off = original) | on | `feat/r-autoscroll` |
| `r_auto_scroll_speed` | Auto-scroll speed in dp/second (choices 30/60/100/150/220) | 60 | `feat/r-autoscroll` |

## Working rules
- Make each change on its own branch, not directly on `main`. `main` stays as the known-good base (this handoff file is the only thing committed straight to it).
- Keep changes small and targeted so any one of them can be dropped.
- Record each change in the log below.
- **Before analyzing any video, screenshot or file the owner sends, record it here first** (what it is and what the owner said about it), so a later account can continue right away if the current session's usage limit ends suddenly.

## Main bug to fix first: downloads
Symptom (as reported by the owner): downloading chapters does not work. The progress bar in the download queue keeps moving forward then jumping back, over and over, and the chapter never finishes.

Findings so far (not confirmed, nothing has been changed yet):
- Code reviewed: `app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt`, `download/model/Download.kt`, `ui/download/DownloadHolder.kt`, `Page.kt`, `OkioExtensions.kt`. The download queue UI uses a determinate `LinearProgressIndicator`.
- Leading hypothesis: pages fail mid-download and are retried automatically (`downloadImage` retries 3 times with 2s/4s/8s waits and progress restarts from 0), which would look exactly like a bar that fills and falls back.
- A similar report exists upstream (issue #552), tied to one specific source and probably the image server, with no fix.
- Still needed from the owner: which source/extension, any error notification text, whether it happens on every source, and a logcat from the moment it happens.

## Owner report #2 (2026-11-18, with screen-recording video `f366cb17-557507.mp4`)
What the owner said (video content notes are added below once reviewed):
- The download problem happens almost only on **Xiaomi / Poco phones** (anything Xiaomi-branded).
- Sometimes downloads do not start at all: pressing the download button makes the app close and reopen.
- The download notification shows an error saying roughly "not split" / "not found" (owner's wording, Arabic; exact text not captured yet).
- The same problem does **not** happen in **Mihon** (the upstream app Yokai comes from — the owner calls it "Hon"), even on the same Xiaomi phone. Every time the owner switches back to a Xiaomi phone they have to use Mihon, because Yokai can't be used there.
- Goal restated: make Yokai download correctly on Xiaomi phones, the way Mihon does.
- Lead from the code: `Downloader.splitTallImageIfNeeded` throws `download_notifier_split_page_not_found` ("page not found for splitting") when the page file can't be found right after download, which could match the "not found" wording. Not verified.

### What the video shows (reviewed 2026-11-18)
- Screen recording (~13 s, 1080x2400) on a Xiaomi phone, Arabic UI, app's download queue screen ("لائحة التنزيل").
- Queue holds 1 manga (source label "مانجا ليك") with chapter **"Otherworldly Evil Monarch 258"**, shown as **0/2 pages** (only 2 pages) and the bar stuck at the start.
- The big button flips between "استئناف" (Resume) and "إيقاف مؤقت" (Pause) several times: the downloader starts, fails, stops, over and over.
- The notification shade shows the error: **"لم يُعثر على الصفحة ٢ أثناء التقسيم"** = "page 2 not found during splitting". This is exactly the `download_notifier_split_page_not_found` string, so the failing step is the tall-image split.
- Other notifications in the shade are unrelated (screen recorder, a music player).

### Likely root cause (found in code, not yet tested on a device)
In `Downloader.splitTallImageIfNeeded`, the "file not found" case does `throw Error(...)` (a `java.lang.Error`), but the surrounding `try` only has `catch (e: Exception)`. An `Error` is not an `Exception`, so it is NOT caught there. It escapes to `getOrDownloadImage`, whose `catch (e: Throwable)` marks the page as Error and shows the notification, so the chapter never completes. As far as I recall, Mihon uses Kotlin's `error(...)` there (an `IllegalStateException`), which that same catch does handle: it just logs and the download continues. That would explain why Mihon works on the same phone. The reason the file isn't found right after download on Xiaomi is still unknown (possibly the storage listing right after the rename).
- Related but separate: "pressing download closes and reopens the app" may be a crash. Needs a logcat; not investigated.
- Possible workaround to test without any code change: turn off the "split tall images" option in the download settings, if it exists there.

## Owner input #3: the app icon (2026-11-18)
- The owner sent the new app icon. Original file saved in this repo at **`branding/icon-source.jpg`** (1024x1024 JPG, committed to `main`; the app code does not reference it).
- What it looks like: a circular badge on a dark charcoal square, thin light-green ring, black disc inside, a big white letter **Y** with green flames rising from its notch and green spotted tentacles curling around its base into a spiral. Colors: black/near-black (#0B1111), greens, white.
- The JPG has a dark square background (corner ~#212526) around the circle; launcher icons need it handled (see below).
- App name: **not decided yet.** Placeholder used until the owner says: "Yokai R".

## Owner input #4: mascot animation (2026-11-18)
- Owner answered open decision #2: the storage folder stays **shared** with the original Yokai. Do NOT change `app_normalized_name`.
- Owner sent two images, saved in `branding/`: `mascot-source.jpg` (his own character: a glossy green cobra-like snake head with big green eyes, fangs and two hands, on a dark background) and `mascot-reference-screenshot.jpg` (a screenshot of another app's home screen, Arabic UI, showing a small cat-like mint-green mascot that cries, floating next to the "continue reading" heading).
- Request: add a mascot like the crying cat in the reference, but using the owner's snake character, **same size and same placement/behavior** as the reference. The reference mascot is an **animation**. The owner will send more images after these (probably the animation frames or other poses), so wait for them before building.
- Rule that applies: this must be switchable from Settings > R (new switch to add to the table), and must not touch the extension loader, library refresh, downloads or sources.

### Owner input #5: chapter-transition screen (2026-11-18)
- Owner sent a third screenshot, saved as `branding/reader-transition-reference.jpg` (from the same other app, Arabic UI, reader view). It shows the end of one chapter and the start of the next, with a dark panel between them containing: a small line of text (\"you can continue reading\"), the **mascot with a happy animation**, a line \"you spent less than a minute in this chapter\" with a small timer icon, and an outlined button \"Next chapter · chapter 3\".
- Request: show this panel **between every two chapters** in the reader, using the owner's snake mascot (`branding/mascot-source.jpg`) with a nice animation, and the time spent in the chapter.
- Notes for whoever builds it: needs a per-chapter reading timer in the reader; it touches the reader's chapter-transition view, so keep it behind a Settings > R switch and keep the original transition when the switch is off. More images are still expected from the owner (animation frames / poses).

### Owner input #6: auto-scroll + reader tools panel (2026-11-18)
- Owner sent a fourth screenshot, saved as `branding/reader-controls-reference.jpg` (from the same other app, Arabic UI): a rounded dark panel over the page with (top) previous / chapter picker (\"Chapter 2\") / next, (middle) a page slider with a \"3 / 12\" box, a brightness slider (\"brightness\" label, 38%), and (bottom) a row of 6 round buttons: a highlighted toggle, a magic-wand (likely filters/enhance), a moon (night mode), a lock (likely rotation lock), a **play** button (likely auto-scroll) and a list/menu button.
- Request: add an **auto-scroll** in the reader (moves by itself, with speed control) and add more tools like that panel. The exact meaning of each button is the owner's to confirm (the guesses above are mine, not his).
- Findings in the code (checked 2026-11-18): no auto-scroll exists in the app. Already existing and reusable: `ReaderNavView.kt` (bottom nav with chapter buttons and slider), `ReaderSlider.kt`, `ReaderColorFilterView.kt` and the filter tab in `settings/ReaderFilterView.kt` (brightness / color filter), `ReaderTransitionView.kt` (chapter transition, where the mascot panel goes), viewers in `viewer/webtoon` and `viewer/pager`.
- Rule: every new tool needs its own switch in Settings > R.

## Planned changes (to be filled in as the owner decides)
- [x] Change `applicationId` so it installs next to the original (`feat/r-branding`, not built)
- [~] App name: placeholder "Yokai R" on `feat/r-branding`; final name pending
- [x] App logo / launcher icon (`feat/r-branding`, not built)
- [x] "R" settings section (scaffold done on `feat/r-settings`, not built yet)
- [ ] Fix downloads (see above): first fix is on `feat/r-settings` (stacked on `fix/download-split-not-found`), waiting for a build + test on the Xiaomi phone
- Note: the main Settings has "Split tall images" under Downloads (default on); turning it off skips the failing step entirely, a no-code workaround to test.

## Change log
| Date | Branch | What changed | Status |
|------|--------|--------------|--------|
| 2026-11-18 | `feat/r-autoscroll` | Auto-scroll for webtoon mode: `WebtoonViewer.start/stop/toggleAutoScroll` (Choreographer, fractional px accumulation, stops on user drag / end of content / onPause), play button `auto_scroll` in `reader_nav.xml`, prefs `rAutoScroll` + `rAutoScrollSpeed`, controls in Settings > R. Stacked on `feat/r-settings`. Known limit: stops if the next chapter is not loaded yet when the end is reached. Not tested on a device. | pending test |
| 2026-11-18 | `build/r` | Merge of `feat/r-settings` + `feat/r-branding` + `feat/r-autoscroll`, plus `.github/workflows/build_r.yml` (debug APK as artifact) and `google-services.json` entries for the new application ids (first build failed without them). First green build was before auto-scroll; build with auto-scroll was started after. | CI |
| 2026-11-18 | `main` | Initial import of upstream `843a59b` | done |
| 2026-11-18 | `feat/r-branding` | `applicationId` -> `com.elrfaay.yokair`, `app_name` -> "Yokai R" (placeholder), new default launcher icon (all densities), monochrome layer removed. Based on `main`. **Not compiled or tested.** | pending test |
| 2026-11-18 | `feat/r-settings` | New "R" entry in main Settings (`SettingsRController`, legacy preference DSL) + pref `rFixSplitTallImageError` in `PreferencesHelper`; the download fix is now gated by that switch. **Stacked on `fix/download-split-not-found`** (contains its commit), so use this branch, not the fix branch alone. **Not compiled or tested.** | pending test |
| 2026-11-18 | `fix/download-split-not-found` | `Downloader.splitTallImageIfNeeded`: `throw Error(...)` -> `error(...)` so a missing file during tall-image split is logged instead of failing the page. 1 line. **Not compiled or tested** (no Android SDK in the session); needs a build and a test on the Xiaomi phone. Not merged into `main`. | pending test |
