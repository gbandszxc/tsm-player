# Play History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a yellow history entry beside favorites on the SMB browser playback controls, backed by the main SQLite database with 1000-item retention, 50-item pagination, and fuzzy metadata search.

**Architecture:** Reuse the current `tsm-player.db` SQLiteOpenHelper and the existing compact playback button renderer. Add a focused history data package, a standalone TV activity for browsing/searching history, and a single playback-service recording point so all playback origins are covered.

**Tech Stack:** Android Kotlin, SQLiteOpenHelper, Media3, Leanback-style XML UI, JUnit/Robolectric tests, Gradle debug APK packaging.

---

### Task 1: History Data Model And Retention

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoritesDbHelper.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/history/PlayHistoryTrack.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/history/PlayHistoryRepository.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/history/PlayHistoryRepositoryTest.kt`

- [ ] Write tests for upsert de-duplication, 1000-row retention, search, and 50-row pagination.
- [ ] Run `.\gradlew.bat testDebugUnitTest --tests "*PlayHistoryRepositoryTest"` and confirm the new tests fail because the history repository does not exist.
- [ ] Add `play_history` table and indexes in `FavoritesDbHelper`, bump `DB_VERSION`, and implement the repository.
- [ ] Re-run the targeted tests and confirm they pass.

### Task 2: History Recording From Playback

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/PlaybackService.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivity.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/history/PlayHistoryTrackFactoryTest.kt`

- [ ] Write tests for converting a `MediaItem` plus `SmbConfig` into a history track, including fallback filename title.
- [ ] Run the targeted test and confirm it fails because the factory does not exist.
- [ ] Add a small factory/helper for history records and call it from `PlaybackService.onMediaItemTransition`.
- [ ] After playback tag parsing succeeds in `PlaybackActivity`, update the current history record metadata.
- [ ] Re-run targeted tests.

### Task 3: Browser Entry Button

**Files:**
- Modify: `app/src/main/res/layout/fragment_tv_browser.xml`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackButtonPresentation.kt`
- Create: `app/src/main/res/drawable/ic_history.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackButtonPresentationTest.kt`

- [ ] Write a presentation test proving browser history is expandable, uses `ic_history`, and labels itself “历史”.
- [ ] Run the targeted test and confirm it fails.
- [ ] Add the SVG vector, presentation spec, XML button, focus rendering, and click handler to open `HistoryActivity`.
- [ ] Re-run the targeted test.

### Task 4: History Activity

**Files:**
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/HistoryActivity.kt`
- Create: `app/src/main/res/layout/activity_history.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] Implement title, search input, previous/next page buttons, status text, rows, and return button using existing `@style/Tsm*` and `@color/ui_*` tokens.
- [ ] Render rows with title, artist/album/file fallback, and played time.
- [ ] Play from the selected row by building a current-page queue, setting `PlaybackConfigStore`, preparing Media3, and launching `PlaybackActivity`.
- [ ] Keep empty lists focused on Back or Search instead of dead text.

### Task 5: Docs And Build Verification

**Files:**
- Modify: `DESIGN.md`
- Modify: `docs/MANUAL.md`

- [ ] Update browser playback controls, history page behavior, main database contents, and recent UI changelog.
- [ ] Run `.\gradlew.bat testDebugUnitTest`.
- [ ] Run `.\gradlew.bat assembleDebug`.
- [ ] Review `git diff` for accidental hardcoded generic UI colors/sizes and unrelated churn.
