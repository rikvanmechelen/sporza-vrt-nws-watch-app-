# CLAUDE.md

Guidance for working in this repo. See `README.md` for the user-facing overview.

## What this is

A standalone **Wear OS** app (built for the Pixel Watch 4) that browses VRT NWS / Sporza
headlines, reads full articles natively on-wrist, and follows live Sporza match scores —
with offline caching, two Tiles, and a complication. Kotlin + Jetpack Compose for Wear, single
`:app` module, manual DI.

## Workflow (important — the user's stated preference)

1. **TDD**: write the failing test first, then implement to green.
2. **Iterate on the emulator**, not the watch. Debug installs to the emulator are fast;
   Wi-Fi installs to the watch take minutes and the link sleeps.
3. **Only push a RELEASE build to the watch, and only once everything passes on the
   emulator.** Debug builds scroll janky (ART unoptimized under `debuggable=true`);
   release (`debuggable=false`) is smooth (measured: 65%→2% janky frames).
4. Commit only when the user asks. End commit messages with the `Co-Authored-By` trailer.

## Environment / commands

Gradle needs Android Studio's JDK (system `java` is 26, which AGP rejects):

```bash
export JAVA_HOME=/opt/android-studio/jbr        # JDK 21
export ANDROID_HOME=$HOME/Android/Sdk           # adb at $ANDROID_HOME/platform-tools/adb
```

- Unit tests (fast, JVM): `./gradlew :app:testDebugUnitTest`
- Instrumented / Compose UI tests (need a device — target the emulator explicitly):
  `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest`
- Iterate on emulator: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug`
- Release build: `./gradlew :app:assembleRelease` (per-ABI APKs; ABI splits are on)
- Push release to watch:
  `adb -s adb-5A231WRBNL30KN-VRzvYX._adb-tls-connect._tcp install -r \
     app/build/outputs/apk/release/app-armeabi-v7a-release.apk`

### Devices
- Emulator: `emulator-5554` (Wear AVD, API 36) — the iteration device.
- Watch: connects over **Wi-Fi wireless debugging** (no USB data). It auto-reconnects via
  mDNS (`adb mdns services`); if it dropped, wake it / re-check Wireless debugging. The
  stable serial is `adb-5A231WRBNL30KN-VRzvYX._adb-tls-connect._tcp`.
- `adb screencap` is **black on the emulator** (SwiftShader) — verify via `uiautomator dump`
  text, the Room DB, or the real watch. Screenshots on the real watch work.

## Architecture

```
data/
  remote/ AtomFeedParser (Jsoup XML), ArticleExtractor (Jsoup DOM), OkHttp services,
          MatchCalendarParser + MatchDetailExtractor (Jsoup, Sporza scores)
  local/  Room: ArticleEntity (PK id+source) + ArticleBodyEntity (PK url) + BlockConverters,
          ArticleDao, NewsDatabase (v2), RoomArticleCache
  NewsContracts.kt (FeedService/ArticleService/ArticleCache/NewsRepository), DefaultNewsRepository
  MatchesRepository.kt (MatchesService + MatchesRepository + in-memory DefaultMatchesRepository)
model/    Article, ArticleContent (ContentBlock: HEADING/PARAGRAPH/QUOTE), NewsSource,
          Match / MatchDetail (MatchEvent, StreamItem) + MatchSports
ui/       MainActivity, AppRoot (HorizontalPager over a PagerTab list + SwipeToDismissBox
          reader/detail), theme, headlines/ (Screen + ViewModel, per-source),
          article/ (Screen + ViewModel), matches/ (Screen + ViewModel, Detail Screen + ViewModel)
tile/     LatestHeadlineTileService + MatchesTileService (glances); MatchesTileModel (pure selector)
complication/             glanceable latest headline
AppGraph.kt (manual DI), VrtNwsApp.kt (Application)
```

- **Sources** (`NewsSource` enum, feeds parsed by the one `AtomFeedParser`): Kort
  (`nl.rss.headlines.xml`), Sport (`sporza.be/nl.rss.articles.xml`), Nieuws
  (`nl.rss.articles.xml`). Enum declaration order = pager order.
- **Pager tabs**: `AppRoot` iterates a `PagerTab` sealed type — `News(source)` for the three
  feeds, then `Matches`. `NewsSource` is coupled to Atom-feed→Article→Room, so Matches runs on
  a parallel path (its own service/repository), not as a `NewsSource` value.
- **Cache-first (news)**: `refresh(source)` fetches the feed → Room; `headlines(source)` is a
  `Flow`; `body(url)` is cache-first, keyed by url.
- **Matches** (`MatchesRepository`, in-memory — scores are ephemeral): `refresh()` scrapes the
  Sporza kalender via `MatchCalendarParser`; `detail(url)` is cache-first via
  `MatchDetailExtractor`. No Room. Tap-to-refresh, no auto-polling.

## Gotchas (don't accidentally revert these)

- **Navigation is manual** (`AppRoot`: `HorizontalPager` + `SwipeToDismissBox`), NOT
  `SwipeDismissableNavHost` — the NavHost silently renders nothing with this dependency set.
- **Rotary focus**: use `remember { FocusRequester() }` + `LaunchedEffect { requestFocus() }`,
  NOT `rememberActiveFocusRequester()` (crashes: "FocusRequester is not initialized" — no
  HierarchicalFocusCoordinator here). In the pager, gate focus to the active page.
- **ArticleExtractor** is the single place to tune if a site changes. Layered:
  1) VRT `prose-article-*` DOM, 2) JSON-LD `articleBody`/`liveBlogUpdate` (Sporza matches),
  3) main-scoped `<p>`/`<h2>` (Sporza articles have no prose-article-* classes).
  Empty result → UI shows the "Open op telefoon" fallback.
- **Match parsers** (`MatchCalendarParser` / `MatchDetailExtractor`) are the place to tune if
  Sporza changes. Sporza uses **hashed CSS-module class names** (`_scoreboard_mdatp_36`), so
  match on `[class*=prefix]`, never exact names (stable non-hashed classes: `.sw-timeline`,
  `.sw-timeline-item`). Scoreboard markup differs per sport (football teams+score, tennis
  set-columns, cycling no teams) → `Match.home/away/score` nullable, `Match.title` the fallback.
  Detail regions: field-timeline `[class*=hoverLabel]` (events), `.sw-timeline-item` (stream),
  article prose minus live widgets (recap). A live-scoreboard JSON API exists
  (`api.sporza.be/web/content/{sport}/matches/{id}`, poll `interval`) but is unused.
  The detail score header comes from the list `Match` snapshot; only events/stream/recap
  re-fetch on open (a live score can lag until the list is refreshed).
- Round screen: list/reader content needs horizontal `contentPadding` or text clips on the curve.
- Wear `HorizontalPageIndicator` forces itself to the bottom; the top dots are a custom Row.
- **Tiles don't scroll** — ProtoLayout has no `LazyColumn`. `MatchesTileService` shows a fixed
  `Column` of up to 3 live matches + a "+N meer" overflow line and funnels the rest into the app;
  the real scrollable list is the Matches tab. The row-selection logic is the pure
  `matchesTileModel()` (+ `matchRowLabel()`) in `MatchesTileModel.kt` — unit-tested, keep it
  Android-free.
- **Matches tile refreshes over the network on each tile request** — unlike the headline tile,
  which reads Room instantly, `matchesRepository` is in-memory and starts empty, so the tile
  calls `refresh()` then reads `matches().first()`. Requests a 1-min freshness interval (Wear
  throttles it), so it's "current score", not live-ticking.
- **Tile → tab deep-link**: `MatchesTileService`'s `LaunchAction` sets the `tab=matches` extra
  (`MainActivity.EXTRA_TAB`); `MainActivity` maps it to `MATCHES_TAB_INDEX` (defined in
  `NavGraph.kt` as the pager's last page) → `AppRoot(initialTab=…)`. No extra ⇒ tab 0, so the
  headline tile is unaffected.

## Versions (pinned for stability)

AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.13 · Wear Compose 1.4.1 · navigation-compose 2.7.7 ·
Room 2.6.1 · Coil 2.7 · OkHttp 4.12 · Jsoup 1.18.1 · compileSdk 36 (unsupported-warning
suppressed in `gradle.properties`) · minSdk 33.

## Deferred

- **R8 minify is OFF** (release is `isMinifyEnabled = false`). Enabling it shrinks the APK
  (faster installs) but needs keep-rules (model enums used via `valueOf`, Room, Coil; Jsoup
  has `-dontwarn`) + on-device testing. Parked.
- **Matches auto-polling**: v1 is tap-to-refresh. The scoreboard JSON API + its `interval`
  would drive live updates (and a live score in the detail header). Parked.
- **Matches offline caching**: currently in-memory only. A `MatchEntity`/detail table (DB v3,
  destructive migration) would make the section open offline like news. Parked.
