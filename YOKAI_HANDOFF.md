# Yokai (private fork) — Handoff (SHORT, current state)

Read ONLY this file when starting a session or when another account takes over. Older history, every owner input (#1-#30), branch map and the full change log are in `YOKAI_HANDOFF_ARCHIVE.md` (open it only for a specific old detail). Keep this file under ~100 lines: when something becomes history, move it to the archive.
Last updated: 2026-11-21 11:40 (Cairo).

## What this is
- Private fork of https://github.com/null2264/yokai (Android manga reader, Tachiyomi/Mihon lineage); installs alongside the original (own applicationId). Placeholder name "Yokai R", app name not decided.
- Repo: **`elrfaayr-ux/yokai-private`** (private, moved from `ELRFAAY` on 2026-11-21 because the old account ran out of Actions minutes). Use `-R elrfaayr-ux/yokai-private` in `gh`. Attach to the session with `add_repo` (owner `elrfaayr-ux`, repo `yokai-private`, access push).
- NEVER make the repo public (the signing key `r-signing/yokai-r.keystore` is inside). Billing follows the repo OWNER: when an account's free minutes (2000/month, ~80 builds) run out, an admin transfers the repo to the next of the owner's 5 GitHub accounts (Settings > Danger Zone > Transfer; receiver accepts by email), then give the new `owner/name` to Claude.
- Owner talks Egyptian Arabic: reply in short Egyptian Arabic. Names to use in every text shown to him and in UI strings: small green snake = **"التنين الصغير"**, wise old snake = **"التنين الحكيم"** (code names `RMascotView`, `r_sage_*` stay).

## Hard rules (owner)
- The app must stay fully usable (library refresh, re-downloading, browsing sources, extension updates untouched).
- Every change must be switchable (on/off or original behavior) from Settings > **R**; new things get their settings there.
- Hands rule: the small dragon has exactly TWO hands, the body's own. Never put the old head sprites' hands back.
- Anything the owner sends (images, ideas) is recorded in the repo first, then used. Phrases the mascots say are my drafts, NOT approved.
- Never write down or use the GitHub tokens he pasted in chat earlier; if access is lost, ask him.

## Current line of work: branch `fix/stats-and-name`
- Pushes to it do NOT auto-build (only `build/r` does). Build with `gh workflow run build_r.yml --ref fix/stats-and-name -R elrfaayr-ux/yokai-private`. **Each new dispatch CANCELS the running build** (concurrency group): dispatch only when none is running.
- **Run 44889187381 = `b66284c`, started 2026-11-21 09:21 UTC, the owner is following it himself. Result: (pending: update this line).** It re-runs the build GitHub refused on 2026-11-19 for billing (run 44533110328). Last green builds: 44530575708 (`58a4c3f`) and 44528048541 (`3c55ed0`). If it fails: read the check-run annotations "Gradle errors"/"Gradle tail" (`gh api repos/elrfaayr-ux/yokai-private/check-runs/<job id>/annotations`) and fix one push at a time.
- Workflow `build_r.yml` uses no `secrets.*`; it signs with `-PrDebugSign=true` and the in-repo keystore, so the repo transfer needed no secret re-adding. Artifact `yokai-r-release` (universal APK); `versionCode = 1000 + GITHUB_RUN_NUMBER`, so builds from any branch update each other.
- Contents of the branch (newest last): crash fixes in `MangaDetailsPresenter.onCreate` (`manga`, `chapterSort`); reader cobra with poses from sheet 14; wise dragon art (sheet 15); home mascot = small dragon FULL BODY (sheets 16-19, cut-outs in `branding/mascot-models/cutouts/`, scripts in `branding/tools/`); typewriter bubble with blip sound (`r_mascot_sound`); stick scene in the reader (`RReaderCobraView`, `r_reader_cobra_scene` + frequency); tap transformation knight/ninja/pirate/detective (`r_mascot_transform`) with ninja "poof" sound; Settings > R crash fix (`a47021b`: never use `dependency =` in this repo's settings DSL, use `changesIn(viewScope) { isEnabled = it }`); welcome drop-in with time-away message (`58a4c3f`); sleep after a minute without touch with blanket (`e580e37`); Settings > R rows glide in (`b66284c`).
- NOTHING from this line is tested on the owner's phone yet (Poco M2102J20SG). First thing after a green build: he installs it and reports.

## Next steps
1. Wait for run 44889187381; give the owner the result in short Egyptian Arabic.
2. Continue the stick scene in the reader: tune on the phone, fix the cutter flaw (impact star of `stick_wise_hit` was removed as "text" by `branding/tools/make_small_dragon_fullbody.py`), keep it from ever blocking reading (touches pass through, never while the reader menu is open).
3. Cost plan (agreed 2026-11-21): one build per batch of changes, never dispatch while one runs, optional cheap "check" mode (Kotlin compile only, no R8/signing) before a full build, maybe arm64-only APK (owner to decide; he wanted universal), no long polling by Claude (the owner follows builds in GitHub Mobile and reports failures), keep this file short.

## Open items
- Owner approval of the Egyptian phrases; "mascot judges a series" basis undecided (rating? chapters read?); app name; text-size setting for the reader bubble; tail wag; sleep/bed art for the full-body dragon; `back` pose currently uses "shocked" (no back view exists, owner may want another).
- Firebase/updater privacy: he said keep the link with the original developer; `feat/r-updater` intentionally not merged.
- Download-press crash (app closes and reopens) needs a logcat; Xiaomi tall-image download fix is on `feat/r-settings`/`build/r`, untested.
- Mihon downloads (`feat/r-mihon`, not merged): hash-suffix match only; Arabic manga/source folder names in Mihon may be hex-encoded, which this does not fix. Needs a screenshot of such a folder.
- Chapter-transition panel and reader tools panel (reference `branding/reader-controls-reference.jpg`): not started.
- Merging the feature branches into `build/r` only after the owner tests an APK.

## How to work from the sandbox
- No Android SDK here, so every compile check is a CI run (~20-25 min). `gh` works over REST only; GraphQL and the Actions secrets endpoints are blocked by the proxy; logs/artifacts cannot be downloaded (use the annotations). Repo raw/blob pages may be blocked for web fetch.
- Branch from `origin/<branch>` after `git fetch`, never from a stale local copy. The Stop hook "unpushed commits" for local helper branches (e.g. `feat-sound-port`) is a false positive: verify with `git ls-remote --heads origin <branch>`.
- A new `lateinit` crash in upstream code usually means an init-order issue in `onCreate`: read the next log line the same way.
