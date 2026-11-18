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

## Main bug to fix first: downloads
Symptom (as reported by the owner): downloading chapters does not work. The progress bar in the download queue keeps moving forward then jumping back, over and over, and the chapter never finishes.

Findings so far (not confirmed, nothing has been changed yet):
- Code reviewed: `app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt`, `download/model/Download.kt`, `ui/download/DownloadHolder.kt`, `Page.kt`, `OkioExtensions.kt`. The download queue UI uses a determinate `LinearProgressIndicator`.
- Leading hypothesis: pages fail mid-download and are retried automatically (`downloadImage` retries 3 times with 2s/4s/8s waits and progress restarts from 0), which would look exactly like a bar that fills and falls back.
- A similar report exists upstream (issue #552), tied to one specific source and probably the image server, with no fix.
- Still needed from the owner: which source/extension, any error notification text, whether it happens on every source, and a logcat from the moment it happens.

## Planned changes (to be filled in as the owner decides)
- [ ] Change `applicationId` so it installs next to the original
- [ ] App name
- [ ] App logo / launcher icon
- [ ] Fix downloads (see above)

## Change log
| Date | Branch | What changed | Status |
|------|--------|--------------|--------|
| 2026-11-18 | `main` | Initial import of upstream `843a59b` | done |
