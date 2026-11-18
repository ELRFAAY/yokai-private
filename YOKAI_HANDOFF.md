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

## Hard requirement for every change
The app must stay fully usable after any modification:
- library refresh works
- downloading (and re-downloading) works
- browsing sources works
- **source (extension) updates keep working normally** — do not touch the extension loader, extension repos or extension installer unless the owner asks.

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

## Planned changes (to be filled in as the owner decides)
- [ ] Change `applicationId` so it installs next to the original
- [ ] App name
- [ ] App logo / launcher icon
- [ ] Fix downloads (see above)

## Change log
| Date | Branch | What changed | Status |
|------|--------|--------------|--------|
| 2026-11-18 | `main` | Initial import of upstream `843a59b` | done |
