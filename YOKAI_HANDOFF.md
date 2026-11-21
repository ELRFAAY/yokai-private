# Yokai (private fork) — Handoff (SHORT, current state)

Read ONLY this file when starting a session or when another account takes over. Older history, every owner input (#1-#30), branch map and the full change log are in `YOKAI_HANDOFF_ARCHIVE.md` (open it only for a specific old detail). Keep this file under ~100 lines: when something becomes history, move it to the archive.
Last updated: 2026-11-21 (third account; see its section).

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
- **Run 44889187381 = `b66284c`, started 2026-11-21 09:21 UTC, the owner is following it himself. Result: GREEN (completed success, checked 2026-11-21 ~11:41 Cairo); artifact `yokai-r-release`, the owner installs it and tests.** It re-runs the build GitHub refused on 2026-11-19 for billing (run 44533110328). Last green builds: 44530575708 (`58a4c3f`) and 44528048541 (`3c55ed0`). If it fails: read the check-run annotations "Gradle errors"/"Gradle tail" (`gh api repos/elrfaayr-ux/yokai-private/check-runs/<job id>/annotations`) and fix one push at a time.
- Workflow `build_r.yml` uses no `secrets.*`; it signs with `-PrDebugSign=true` and the in-repo keystore, so the repo transfer needed no secret re-adding. Artifact `yokai-r-release` (universal APK); `versionCode = 1000 + GITHUB_RUN_NUMBER`, so builds from any branch update each other.
- Contents of the branch (newest last): crash fixes in `MangaDetailsPresenter.onCreate` (`manga`, `chapterSort`); reader cobra with poses from sheet 14; wise dragon art (sheet 15); home mascot = small dragon FULL BODY (sheets 16-19, cut-outs in `branding/mascot-models/cutouts/`, scripts in `branding/tools/`); typewriter bubble with blip sound (`r_mascot_sound`); stick scene in the reader (`RReaderCobraView`, `r_reader_cobra_scene` + frequency); tap transformation knight/ninja/pirate/detective (`r_mascot_transform`) with ninja "poof" sound; Settings > R crash fix (`a47021b`: never use `dependency =` in this repo's settings DSL, use `changesIn(viewScope) { isEnabled = it }`); welcome drop-in with time-away message (`58a4c3f`); sleep after a minute without touch with blanket (`e580e37`); Settings > R rows glide in (`b66284c`).
- NOTHING from this line is tested on the owner's phone yet (Poco M2102J20SG). First thing after a green build: he installs it and reports.

## FIRST THING FOR THE NEXT ACCOUNT
1. Get access: the owner pastes a fine-grained GitHub token (repo `elrfaayr-ux/yokai-private`, Contents + Actions read/write); use it only per command (`git -c credential.helper= -c http.extraheader="Authorization: Basic <base64 x-access-token:TOKEN>" push ...`), never store it. The repo is PUBLIC right now on purpose (he will make it private after the work): it holds the signing key, so remind him to make it private again and consider the key exposed.
2. Check build run 44897247105 (`f2cba35`) with `GET /repos/elrfaayr-ux/yokai-private/actions/runs/44897247105`. If it failed, read the annotations (see \"How to work\"). If green, the owner installs `yokai-r-release`.
3. Wait for his report on: hide-lists menu, reader opening (crash?), stick scene. For a crash ask for the text of the app's crash screen.
4. Then do the open items 2-4 below (coherent stick scene, direction, owner's animation frames).

## Session log 2026-11-21 (account hand-over: the previous account's last message was only a progress note, its code state is exactly origin `b66284c`)
DONE (pushed, branch `fix/stats-and-name`, commit `0838801`, NOT built, NOT tested on the phone):
- Impact star back on `r_stick_wise_hit` (the cutter `branding/tools/make_small_dragon_fullbody.py` treated its black outline as a text label; new `STICK_KEEP` box protects it and closing+hole-fill makes the inside white). Cut-out `stick_wise_hit.png` and `r_stick_wise_hit.webp` regenerated (402x312, was 379x290: the star widened it).
- Scene timing in `RReaderCobraView.SCENE`: hit hold 450 -> 850 ms, pause after the \"raise\" line 600 -> 300 ms.
- Checked `MangaDetailsController.updateChapters`: it is safe (`view ?: return`, no lateinit). The only lateinit (`manga`, `chapterSort`) are in the Presenter and were already fixed in `onCreate`.
NOT DONE / FOR THE NEXT ACCOUNT (owner message of 2026-11-21, relayed by him):
1. **Hide a list**: DONE in code, commit `05a0e8e` on `fix/stats-and-name` ("Library: hide / show lists"), NOT compiled/built/tested (no SDK here). 3-dot menu (`OverflowDialog`, new row `hide_lists_item`) opens a multi-choice dialog of the library lists (categories, not the Default one); checked = hidden from the library until unchecked or "إظهار الكل". Prefs `r_hide_lists` (switch, Settings > R > المكتبة) and `r_hidden_lists` (ids); filter in `LibraryPresenter.getLibraryFlow` (skipped in sub-controllers). I ASSUMED "list" = library category (he never answered which list): confirm with him. If it fails to compile, suspects: `hide_lists_item` binding, `lifecycleScope` import, the 5-flow `combine` in the presenter.
2. **Stick scene does not look coherent** (the owner's words): the wise dragon swings at empty air, then the small dragon appears already hit; the blow on the head is never shown. Cause: the scene is a slideshow of single poses (`SCENE`, `RReaderCobraView`), no frame shows contact. Plan: draw both sprites on screen together, wise one swings the stick (rotate around the hand), small one gets a bump/squash + the star on contact, then the small one is shown hit. NOT DONE.
3. **Direction**: the small dragon should be at the LEFT of the screen and the wise dragon must come in from the RIGHT and hit it on the left side (the owner said the green one comes out from the left; he wants the other from the right). Verify what the code does now (commit 50e5724 says \"from the right\") on the phone before changing. NOT DONE.
4. The owner asked which AI tool can ANIMATE the model sheets so he can make it himself: answered in chat only (image-to-video tools give video with a background, not transparent frames; better: ask an image model for consistent frame sets of the same pose sheet, then animate in code). If he sends frames, record them in `branding/` first.
5. Reader-open crash: root cause still NOT found by reading all our reader changes (ReaderActivity, nav wrapper, vertical slider, cobra, auto-scroll, read-time card, view model). Commit `f2cba35` makes the decorative parts unable to stop the reader opening (cobra creation in try/catch, vertical bar falls back to the original bar, read-time card timer guarded); logs go to logcat tag `YokaiR`. If it still crashes, the app's own crash screen (CrashActivity) has the stack trace: ask the owner to copy it.
6. Run 44897247105 (`f2cba35`) finished GREEN (13:14 Cairo, 2026-11-21). The owner installs `yokai-r-release` and reports (reader opens? stick scene? hide-lists).

## Third account, 2026-11-21 (owner said "go"): hidden-files vault + R icons (branch `fix/stats-and-name`, commit `d7467f1`, NOT compiled, NOT built, NOT tested)

- Owner's design for "hide lists" (Egyptian, his words): pick a library category to hide; all its manga disappear from Library, Updates and History (and search suggestions) but keep refreshing with library refresh and stay readable; "إخراج" brings them back with their ORIGINAL read/fetch dates (display filter only, nothing deleted). Entry is disguised: 3-dot menu row **"الملفات"** with a green arrow (name is my pick, he did not object); opens only after fingerprint OR the phone's pattern/PIN (`AuthenticatorUtil`, BIOMETRIC_WEAK + DEVICE_CREDENTIAL). No phone lock set = refuses with a toast.
- Code: `util/RHiddenLists.kt`; `OverflowDialog` (vault dialog: hidden lists with green down arrow -> `MainActivity.showRVaultList` pushes `FilteredLibraryController(filterCategories=[id])`, so the grid is the normal library look and display options apply; button "اختيار التصنيفات": check = hide, un-check = إخراج); `RecentsPresenter.runRecents` filters hidden manga after the query (paging counts use raw rows); `LibraryPresenter` hides a manga in ANY hidden list even if also in a visible one (my choice, he never answered) and search suggestions skip them.
- Not done: arrow opens a screen per list (no inline expand); app-wide animation polish (only Settings > R got icons: `rIcon` + new `ic_r_*` vectors). If compile fails, suspects: `OverflowDialog` (ArrayAdapter/TextViewCompat imports), `RecentsPresenter` rawReading/cReading rename, `SettingsRController.rIcon`.
- Owner wants a build check ~every 20 min: Claude cannot run in the background (free plan, no scheduling); check the last build whenever he writes ("شيّك").
- This file lives on `main`; the branch still has an old long copy.

## Next steps
1. Owner installs the APK from run 44889187381 (green) and reports what he sees on the phone.
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
