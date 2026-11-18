# Yokai (private fork) — Handoff

Read this first when starting a new session or from another account. Update it after every meaningful change.
Last updated: 2026-11-18 (12:50, after owner input #13; newest code is on `build/r`, see the top of its git log)

## What this is
- Private fork of https://github.com/null2264/yokai (Android manga reader, Tachiyomi/Mihon lineage).
- Private repo: `ELRFAAY/yokai-private` (must stay private; everything done on this project is private).
- Base: upstream commit `843a59b` (2026-09-28), imported as one initial commit on `main`.
- A git remote named `upstream` points at the original repo (read-only, for pulling updates later).

## Decisions made by the owner
- The app will be installed **alongside the original Yokai**, as a separate app. So the `applicationId` must be changed, and so must everything that would clash with the original.
- Many changes are planned, including branding: **app name and logo** (plus more to come, to be listed below).
- Language of conversation with the owner: Egyptian Arabic.

## LATEST STATE (written 2026-11-18, owner's usage limit at 94%; read this before the older START HERE section below)

### Where things are
- Branches pushed: `main` (handoff + branding assets only), `fix/download-split-not-found`, `feat/r-settings`, `feat/r-branding`, **`feat/r-autoscroll`** (stacked on `feat/r-settings`), and **`build/r`** = merge of settings + branding + autoscroll, plus `.github/workflows/build_r.yml` and extra entries in `app/src/standard/google-services.json` for the new application ids.
- Last CI run on `build/r` (auto-scroll build, uploads only the universal debug APK): `in_progress None 45ea4be https://github.com/ELRFAAY/yokai-private/actions/runs/44273600210` (format: status conclusion sha link). If it is `in_progress`, check it; if it failed, read the Gradle error (see "How to read CI errors" below). The earlier build without auto-scroll was green.
- Nothing has been tested on a device yet. The owner downloads the APK himself from the Actions run page (artifact `yokai-r-debug`); the sandbox cannot download artifacts.

### How to work from the sandbox (important)
- The owner pasted a GitHub token in the chat; it let the session clone, push and read Actions via `api.github.com`. Never write the token in any file or memory. If it is gone, ask him for access again (he can paste a new token with read/write on Contents + Workflows for this repo only), and remind him to revoke it afterwards.
- No Android SDK in the sandbox, so every compile check is a CI run (about 15-25 min). Fix compile errors one push at a time.
- The sandbox cannot download Actions logs or artifacts (their host is blocked). `build_r.yml` therefore prints the Gradle errors as annotations: read them with `GET /repos/ELRFAAY/yokai-private/check-runs/<job id>/annotations` (titles \"Gradle errors\" and \"Gradle tail\").
- Past compile/build problems already solved: (1) `google-services.json` had no client for the new package names, so `processStandardDebugGoogleServices` failed; (2) `defaultValue = ...` inside `intListPreference` is unresolved (the `bindTo(...)` already sets it).
- Note: the `standard` flavor still includes Firebase Analytics/Crashlytics tied to the upstream author's Firebase project (`yokai-4034e`); the entries added to `google-services.json` copy that project for the new ids. This is an **open privacy decision** for the owner (turn it off or keep it); not discussed with him yet.

### Owner decisions and requests so far (all stated by the owner)
- Storage folder is **shared** with the original Yokai (do not change `app_normalized_name`).
- **One APK that works on all devices** (universal), not many per-architecture files.
- He mostly reads in **continuous vertical (webtoon) mode, no gaps between pages**.
- Final app name: still **not decided** (placeholder \"Yokai R\").
- Hard rules: the app must stay fully usable; every change switchable from Settings > R; extension updates untouched.

### Requested features and status
| Feature | Status |
|---|---|
| Download fix for Xiaomi (tall-image split) | code on `feat/r-settings`, untested on the phone |
| Settings > R screen | done (scaffold), in `build/r` |
| Auto-scroll in webtoon mode (play button in reader nav, speed in R settings) | done, in `build/r`, untested; known limit: stops if the next chapter is not loaded yet at the end |
| Extra reader tools panel like `branding/reader-controls-reference.jpg` (chapter nav, page slider with count, brightness slider, night mode, lock, play, menu) | not started; meaning of each button must be confirmed with the owner; brightness/filters/slider already exist in the code |
| Mascot (snake from `branding/mascot-source.jpg`) like the reference app: floats on the home screen, the owner **grabs and throws it**, it flies with a dotted trail, changes faces, shows speech bubbles | v1 built on `feat/r-mascot` (in `build/r`), untested; 6 faces added 12:50 (see change log); phrases are my draft, not approved |
| Chapter-transition panel with the mascot + \"time spent in this chapter\" + next-chapter button (`branding/reader-transition-reference.jpg`) | not started; needs a per-chapter reading timer; goes in `ReaderTransitionView` behind an R switch |
| New logo everywhere | see below |

### The logo (owner sent it again as `branding/logo-owner-557510.jpg`)
- Owner message: \"this is the logo\". It is a 1024x1024 circular badge: dark disc, thin light-green ring, big white **Y**, green flames rising from its notch, green spotted tentacles curling around its base into a spiral.
- **It is byte-identical to `branding/icon-source.jpg`** (same md5), which is already the launcher icon on `feat/r-branding` (default alias `MainActivityDefaultDummy` uses `@mipmap/ic_launcher`). So the home-screen icon already uses it.
- Before that, the owner said the icon he saw was \"not the logo he meant\". Probable explanation: other places still show the original Yokai art: the **splash screen** (`@drawable/ic_yokai_splash`, set in `res/values/themes.xml`), the in-app logo (`res/drawable/ic_yokai.xml`), and the icon-switcher icons (Blue, Orange, Gray, Classic mipmaps). Not confirmed.
- **Open question already asked to the owner (unanswered):** where did he see the wrong logo: the home-screen icon, or the splash / inside the app? If he says \"everywhere\": replace splash, `ic_yokai`, and the icon-switcher icons with this logo; tell him to uninstall the old build first.

### Next steps for the next account
1. Check the CI run above; if green, tell the owner the link (he downloads `yokai-r-debug`, one universal APK) and ask him to test: download on the Xiaomi phone, Settings > R, auto-scroll button in a webtoon chapter.
2. Get his answer about the logo location and the Firebase decision.
3. Then, in this order (each on its own branch behind an R switch, merged into `build/r`): chapter-transition panel with mascot and timer; mascot on the home screen (needs his images); reader tools panel.
4. Keep this file and the tables below up to date after every change.

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
| `r_fix_split_tall_image_error` | Download: page files always named with English digits + a missing file during the tall-image split is only logged (on) vs. device-language digits and the page fails with "page not found during splitting" (off = original) | on | `feat/r-settings` (digits part: `build/r`) |
| `r_auto_scroll` | Reader (continuous/webtoon mode only): play/pause auto-scroll button at the end of the reader nav row (on) vs. no button (off = original) | on | `feat/r-autoscroll` |
| `r_auto_scroll_speed` | Auto-scroll speed in dp/second (choices 30/60/100/150/220) | 60 | `feat/r-autoscroll` |
| `r_single_app_icon` | Appearance: icon picker hidden (on) so the app has one icon vs. original picker with Default/Blue/Orange/Gray/Classic (off) | on | `build/r` |
| `r_mascot` | Home (library screen): floating snake mascot, grab and throw it (on) vs. nothing (off = original) | on | `feat/r-mascot` (in `build/r`) |

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

### Owner input #7: video `557552.mp4` (2026-11-18, recorded BEFORE reviewing it)
- The owner sent a screen-recording video (`557552.mp4`) with the message \"look at this\" and nothing else. Probably a reference for the mascot animation or the reader features he asked for; content notes are added below once reviewed.
- **Review (2026-11-18, ~36 s, 1080x2400, 48 fps, screen recording of the same other app, Arabic UI):** it stays on the app's home screen with the banner carousel auto-advancing behind. The mint-green flame/drop mascot lives on top of the screen and moves around by itself: it floats, flies across the screen leaving a **dotted trail**, and changes **faces** (smiling, angry/hurt/crying). It also shows **speech bubbles** in Arabic over its head that change with its mood, for example: \"please stop...\", \"ouch, that hurts\", \"are you still hitting me?\", \"enough!\", \"watch out for the ceiling!\", \"that was fun!\", \"again!\", \"to infinity!\", \"why are you hitting me?\".
- **My reading (an inference, not confirmed by the owner):** the mascot probably reacts to the user touching/flicking it (the phrases are about being hit, thrown and the ceiling). The video alone does not show the touches, so ask the owner before building: is it interactive (tap/throw) or just a floating decoration, and should the phrases be copied or written fresh for the snake?

### Owner input #8 (2026-11-18): answers and corrections
- **Mascot interaction (answer to the open question):** it is interactive. The owner **grabs the mascot and throws it** (drag + release/fling), exactly like in the reference video; the snake must behave the same way: flies with a dotted trail, changes face, shows speech bubbles. Phrases: not decided yet (copy the reference's style, written for the snake, or ask the owner). Still waiting for his animation images.
- **One APK for all devices:** the owner got many APK files in one download and wants **a single APK that works on every device**. Cause: the app's `splits.abi` produces one APK per architecture plus a universal one. Decision: CI now uploads **only the universal APK** (`*universal*.apk`) in `build_r.yml`. Do not hand the owner per-architecture files. Size note: universal debug is big; the permanent-key release build (minified) will be much smaller.
- **Logo:** the owner said the launcher icon currently in the app (made from `branding/icon-source.jpg`, the Y badge) is **not the logo he meant** and that he will send the new logo. Not received yet. Do not touch the icon again until it arrives.

### Owner input #9: logo image `557510.jpg` (2026-11-18, recorded before comparing it)
- After saying the icon in the app was not the logo he meant, the owner sent an image with the message \"this is the logo\". Saved as `branding/logo-owner-557510.jpg`. Comparison with `branding/icon-source.jpg` is noted right below.

### Owner input #10 (2026-11-18 11:19): logo again, one icon, downloads still failing, mascot question
- Owner sent the logo image once more (same bytes as `branding/logo-owner-557510.jpg` / `icon-source.jpg`, md5 60fe690f...). He said: the OLD logo is black and white, and **the icon he sees on his side is black and white too**, so it is not what he wants; this green Y is the one he wants.
- He wants **only ONE icon version in the program** (not the 6-7 copies = the icon picker in Settings > Appearance) and **one build that works on any device without problems**.
- **Downloads still fail** (he said "start work" = go). Root cause found 2026-11-18 (see change log): page files were named with the device-language digits (Arabic UI -> `٠٠٢.jpg`) while the tall-image split looks for `002`; Mihon passes `Locale.ENGLISH`. So it is the Arabic language, not Xiaomi itself. Fix on `build/r`, **not tested on the phone yet**. The separate crash "press download -> app closes and reopens" is still not investigated (needs a logcat).
- Mascot: he asked whether the small animated creature that reacts to him is part of the final app. Answer given: **not built yet**, waiting for his animation images; goes behind an R switch.
- Why the icon may still look black and white to him (unconfirmed): he may be looking at the original Yokai app (installed side by side), or the splash screen (fixed now), or the icon picker (hidden now). The launcher icon itself was verified colored (adaptive foreground = the green badge).

### Owner input #11 (2026-11-18 12:26): screenshot `557615.png`, "slow to open", "where is the character?"
- Screenshot of his home screen: next to another app, our app's icon labeled **"Yōkai Debug"** with a gray/white Y and a gray flame. He said that is not the logo he wanted added, and that the app opens very slowly. Then asked where the small character (mascot) is.
- **Cause of the gray icon (found in code):** `app/src/debug/res/mipmap-*` (and `app/src/nightly/res/mipmap-*`) are upstream's per-build-type icons and override `app/src/main`, so every debug build kept the gray upstream icon no matter what we put in main. Fixed on `build/r`: those folders deleted; debug/nightly names are now "Yokai R Debug" / "Yokai R Nightly". Release/main already used the green logo.
- **Slow opening:** the debug build is not minified and is debuggable (slow by design). CI (`build_r.yml`) now builds `assembleStandardRelease -PrDebugSign=true` (R8 + resource shrinking, signed with the debug key so it installs). Artifact name: `yokai-r-release` (one universal APK). Its package is `com.elrfaay.yokair.yokai` (release suffix), different from the old debug app (`...debugYokai`), so the owner should uninstall the old "Yōkai Debug" to avoid two icons.
- **Open decision: signing key.** The debug key is regenerated on every CI run, so each new APK has a different signature and cannot update the previous one in place (uninstall first = data loss unless he restores a backup). A permanent keystore is needed; storing it as a GitHub secret is the clean way (needs the owner to add secrets; the pasted token does not allow that), committing it to the private repo is the quick way. Not decided, not asked yet.
- **Mascot built (v1, untested):** `RMascotView` + `drawable-nodpi/r_mascot.webp` (his cobra cut out of `branding/mascot-source.jpg`), branch `feat/r-mascot`, merged into `build/r`. Behavior: full-screen transparent overlay in `MainActivity` that only takes touches on the snake; drag it, release with speed and it flies with a dotted trail, bounces on the screen edges (hurt tint + shake on a hard hit), rests again; tap = happy squash; speech bubbles with phrases I wrote in Egyptian Arabic (lists at the bottom of `RMascotView.kt`; the owner has NOT approved them). Shown only when the library controller is on top, switch `r_mascot` in Settings > R. Limits: one picture only, so "faces" are tint/squash/tilt; real expressions need more images from him.

### Owner input #12 (2026-11-18 12:35): signing + mascot images
- He wants **every build to update the previous one in place** (answer to the signing question). Done on `build/r`: permanent key `r-signing/yokai-r.keystore` (PKCS12, alias `yokair`, password in `app/build.gradle.kts`; committed to this PRIVATE repo, so the repo must never become public; safer later: move it to a GitHub secret) signs the release build, and `versionCode = 162 + commit count` so each build is newer. One-time cost: the first build with this key must be installed after uninstalling any older build (different signature); restore from an in-app backup if needed.
- Mascot: he asked me to say which angle/images I need so he can make them. Answer given: front view, same framing as `mascot-source.jpg`, same dark background, one image per mood: happy, hurt (X/squeezed eyes), scared/surprised, dizzy (spiral eyes), sad/crying, blink (eyes closed). Code side: `RMascotView` currently only tints/squashes the single picture; when the images arrive, cut them out with the same script logic (flood-fill the dark background), put them in `drawable-nodpi`, and swap the bitmap by mood.

### Owner input #13 (2026-11-18 12:42): mascot mood sheet `557623.jpg` (recorded before use)
- Owner sent one 1024x1024 sheet with the snake in 6 moods on a dark background, 2 columns x 3 rows, each labeled: HAPPY (top-left), HURT (top-right), SCARED (middle-left), DIZZY (middle-right), SAD (bottom-left), BLINK (bottom-right). Saved as `branding/mascot-moods-557623.jpg`.
- Decisions the owner confirmed in the same message (12:40): final app name is **Yokai Private**; keep the link with the original developer (Firebase/updater untouched) so extension updates keep arriving. Done: `app_name` -> "Yokai Private" (+ Debug/Nightly) on `build/r` (8a3102b).
- Plan: cut each cell out (flood-fill the dark background), save as `drawable-nodpi/r_mascot_<mood>.webp`, make `RMascotView` swap the bitmap by mood (idle=blink/happy, drag=scared, hard hit=hurt, many bounces=dizzy, rest after hits=sad, tap=happy).

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
| 2026-11-18 | `build/r` | Mascot now has 6 faces from the owner's sheet (`branding/mascot-moods-557623.jpg` cut into `drawable-nodpi/r_mascot_{happy,hurt,scared,dizzy,sad,blink}.webp`; the old single `r_mascot.webp` was removed). `RMascotView.currentMood`: idle=happy with a blink every 3.6 s, grabbed/flying=scared, wall hit=hurt, 3+ hard hits in one flight=dizzy, 1-2 hits then rest=sad, tap/soft landing=happy. New phrases for dizzy/sad (not approved by the owner). Cut-out script is not stored in the repo (flood-fill of bg #181C1B, keep components >= 3000 px, 384x384 lossless WebP). Not compiled or tested. | CI |
| 2026-11-18 | `build/r` (1e3ae7a) | Deleted upstream debug/nightly launcher icon overrides (they hid the green logo), renamed debug/nightly app names, CI builds the minified release APK (`-PrDebugSign=true`, artifact `yokai-r-release`). | CI |
| 2026-11-18 | `feat/r-mascot` + `build/r` (fd9b895) | Snake mascot overlay (`RMascotView.kt`, `r_mascot.webp`), `r_mascot` pref + Settings > R switch, `MainActivity.updateRMascot` (shown on `LibraryController` only). Not compiled locally (no Android SDK) and not tested on a device. | CI |
| 2026-11-18 | `build/r` (commit 7e215cc) | Splash screen uses the green Y logo (`drawable-nodpi/ic_yokai_splash_r.webp`, themes.xml); new switch `r_single_app_icon` hides the icon picker; `Downloader.getOrDownloadImage` names page files with `Locale.ENGLISH` digits (fixes the Arabic-UI "page not found during splitting"). Notification small icon `ic_yokai` left as is on purpose (must be a flat glyph). Not tested on a device. | CI |
| 2026-11-18 | `feat/r-autoscroll` | Auto-scroll for webtoon mode: `WebtoonViewer.start/stop/toggleAutoScroll` (Choreographer, fractional px accumulation, stops on user drag / end of content / onPause), play button `auto_scroll` in `reader_nav.xml`, prefs `rAutoScroll` + `rAutoScrollSpeed`, controls in Settings > R. Stacked on `feat/r-settings`. Known limit: stops if the next chapter is not loaded yet when the end is reached. Not tested on a device. | pending test |
| 2026-11-18 | `build/r` | Merge of `feat/r-settings` + `feat/r-branding` + `feat/r-autoscroll`, plus `.github/workflows/build_r.yml` (debug APK as artifact) and `google-services.json` entries for the new application ids (first build failed without them). First green build was before auto-scroll; build with auto-scroll was started after. | CI |
| 2026-11-18 | `main` | Initial import of upstream `843a59b` | done |
| 2026-11-18 | `feat/r-branding` | `applicationId` -> `com.elrfaay.yokair`, `app_name` -> "Yokai R" (placeholder), new default launcher icon (all densities), monochrome layer removed. Based on `main`. **Not compiled or tested.** | pending test |
| 2026-11-18 | `feat/r-settings` | New "R" entry in main Settings (`SettingsRController`, legacy preference DSL) + pref `rFixSplitTallImageError` in `PreferencesHelper`; the download fix is now gated by that switch. **Stacked on `fix/download-split-not-found`** (contains its commit), so use this branch, not the fix branch alone. **Not compiled or tested.** | pending test |
| 2026-11-18 | `fix/download-split-not-found` | `Downloader.splitTallImageIfNeeded`: `throw Error(...)` -> `error(...)` so a missing file during tall-image split is logged instead of failing the page. 1 line. **Not compiled or tested** (no Android SDK in the session); needs a build and a test on the Xiaomi phone. Not merged into `main`. | pending test |
