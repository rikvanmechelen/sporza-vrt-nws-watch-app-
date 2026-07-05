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
### Screenshots
`adb exec-out screencap -p` works on **both** the emulator and the real watch (an older
SwiftShader black-screen issue is gone). Capture a surface:

```bash
adb -s emulator-5554 exec-out screencap -p > shot.png   # 480x480 round frame
```

- Launch a specific screen first: `adb -s emulator-5554 shell am start \
    -n be.vanmechelen.vrtnws/.ui.MainActivity --es tab matches` (extra opens the Matches
  tab; omit for tab 0). Deep-link only applies on a **cold** start — `force-stop` first,
  since `MainActivity` reads the extra in `onCreate` (no `onNewIntent`).
- Navigate with `adb shell input swipe <x1> <y1> <x2> <y2> <ms>` (page swipes) and
  `input tap <x> <y>` (open an item). Give screens a moment to render/settle before capturing.
- **Tiles/complication can't be screenshotted** (system-hosted glances, not launchable via
  `am`) — verify those on the real watch.
- `adb -s <serial> ...` also works against the watch serial for on-wrist screenshots.

## Architecture

```
data/
  remote/ AtomFeedParser (Jsoup XML), ArticleExtractor (Jsoup DOM), OkHttp services,
          MatchCalendarParser + MatchDetailExtractor (Jsoup, Sporza scores),
          ScheduleParser (regex over the schedule API JSON: real kickoff per fixture)
  local/  Room: ArticleEntity (PK id+source) + ArticleBodyEntity (PK url) + BlockConverters,
          ArticleDao, NewsDatabase (v2), RoomArticleCache
  NewsContracts.kt (FeedService/ArticleService/ArticleCache/NewsRepository), DefaultNewsRepository
  MatchesRepository.kt (MatchesService + MatchesRepository + in-memory DefaultMatchesRepository)
model/    Article, ArticleContent (ContentBlock: HEADING/PARAGRAPH/QUOTE), NewsSource,
          Match / MatchDetail (MatchEvent, StreamItem) + MatchSports
ui/       MainActivity, AppRoot (HorizontalPager over a PagerTab list + SwipeToDismissBox
          reader/detail), theme, headlines/ (Screen + ViewModel, per-source),
          article/ (lead-image-first Screen + ViewModel + ArticleReaderModel pure helpers),
          matches/ (Screen + ViewModel, Detail Screen + ViewModel)
tile/     LatestHeadlineTileService + MatchesTileService (glances); MatchesTileModel (pure:
          live-first selector + dedup, sportEmoji, matchMidText, localizeKickoffTime)
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
  Sporza kalender via `MatchCalendarParser`, then (only if promoted carousel cards are present)
  patches their real kickoff in from the schedule API via `ScheduleParser`/`applyScheduleKickoffs`;
  `detail(url)` is cache-first via `MatchDetailExtractor`. No Room. Tap-to-refresh, no auto-polling.

## Gotchas (don't accidentally revert these)

- **Navigation is manual** (`AppRoot`: `HorizontalPager` + `SwipeToDismissBox`), NOT
  `SwipeDismissableNavHost` — the NavHost silently renders nothing with this dependency set.
- **Rotary focus**: use `remember { FocusRequester() }` + `LaunchedEffect { requestFocus() }`,
  NOT `rememberActiveFocusRequester()` (crashes: "FocusRequester is not initialized" — no
  HierarchicalFocusCoordinator here). In the pager, gate focus to the active page.
- **Article reader is lead-image-first, and NOT a `ScalingLazyColumn`** — it's a plain
  `Column(verticalScroll)` + `rotaryScrollable`, with a `graphicsLayer(Offscreen)` +
  `drawWithContent`/`BlendMode.DstIn` gradient that fades both scroll edges (the top fade ramps in
  with scroll so the lead image sits flush to the arc at rest). SLC was rejected because it scales
  the first item, which breaks a full-bleed hero. The lead is a fixed-height image (or
  section-tinted diagonal `stripeBrush` fallback when `imageUrl` is null) pinned to the top under a
  scrim; the kicker + title are anchored ~42% down the image and flow *downward* — a long title is
  shown in full and continues past the image onto black, so it never grows up into the narrow top
  arc and is never clipped (don't re-add a `maxLines` cap on the title). Body items carry their own
  horizontal `BodyPadding` (the column itself has none, so the hero bleeds edge-to-edge). Source
  mark/name + tidy one-tag kicker are pure, unit-tested helpers in `ArticleReaderModel.kt`
  (`readerSourceFor(section)`, `kickerLabel(category)` — the feed's `nstag` can be pipe-joined).
  `ArticleScreen` takes the `Article` + `Section` (not just the title) so the hero/meta render from
  the list snapshot instantly, before the body finishes extracting.
- **ArticleExtractor** is the single place to tune if a site changes. Layered:
  1) VRT `prose-article-*` DOM, 2) JSON-LD `articleBody`/`liveBlogUpdate` (Sporza matches),
  3) main-scoped `<p>`/`<h2>` (Sporza articles have no prose-article-* classes).
  Empty result → UI shows the "Open op telefoon" fallback.
- **Match parsers** (`MatchCalendarParser` / `MatchDetailExtractor`) are the place to tune if
  Sporza changes. Sporza uses **hashed CSS-module class names** (`_scoreboard_mdatp_36`), so
  match on `[class*=prefix]`, never exact names (stable non-hashed classes: `.sw-timeline`,
  `.sw-timeline-item`). Scoreboard markup differs per sport → `Match.home/away/score` nullable,
  `Match.title` the fallback: football = `teamname` blocks + goal score (`[class*=numbers]`);
  tennis = two `setsPlayer` sides (names via `[class*=name]`.ownText, dropping the `playername`
  wrapper + ranking `meta`), score = **sets won** with the live set's games in `Match.subScore`
  from the `_set_` spans (note the delimited `_set_`/`_sets_` vs `setsPlayer`/`setsPlayers`);
  cycling = no teams. **Status**: `_live_` class or hidden "live" → LIVE, `notStarted`/`vandaag`/a
  time → UPCOMING, `end`/"afgelopen"/"einde" → FINISHED — plus Sporza's **`nu`** ("now") label
  (on court, no `_live_` class or score yet) is treated as LIVE, else it would linger as an
  "upcoming" fixture showing a now-past kickoff time. The calendar is also `distinctBy { detailUrl }` — Sporza repeats featured
  fixtures — and the tile additionally dedups by `id` (catches url-variant repeats).
  Detail regions: field-timeline `[class*=hoverLabel]` (events), `.sw-timeline-item` (stream),
  article prose minus live widgets (recap). A per-match live-scoreboard JSON API exists
  (`api.sporza.be/web/content/{sport}/matches/{id}`, poll `interval`) but is unused.
  The detail score header comes from the list `Match` snapshot; only events/stream/recap
  re-fetch on open (a live score can lag until the list is refreshed).
- **Kickoff time comes from two places — only one is a real kickoff.** Scoreboard entries carry
  the kickoff in their a11y hidden text (`"X tegen Y, vandaag om 22:00"`). But promoted
  **livestream-card** carousel fixtures (`Match.id` prefixed `livestream-`, no scoreboard) carry
  only a TV **broadcast window** (`"time":{"text":"20:30 - 23:20"}`) — the pre-show start, ~10–30
  min before kickoff, and the lead varies per match so it can't be derived. `OkHttpMatchesService`
  therefore enriches card matches from the **schedule API** `api.sporza.be/web/content/schedule?date=YYYY-MM-DD`
  (the endpoint the calendar's own date-nav uses; fetched today..+2 days), whose `ariaLabel`
  states the real kickoff. `ScheduleParser` regex-parses it (no JSON lib) → `applyScheduleKickoffs`
  matches by team pair and overwrites only `livestream-`/`UPCOMING` cards' `statusText`.
- **Match times are Europe/Brussels wall-clock; display converts to the watch's zone.** Sporza
  (Belgian) renders every kickoff in CET/CEST as a bare `HH:mm` string, stored verbatim in
  `Match.statusText`. Conversion happens at the **display boundary** (parsing stays "what Sporza
  said" so the fixture-based parser tests are zone-independent): `localizeKickoffTime()` in
  `MatchesTileModel.kt` rewrites any `HH:mm` token from `Europe/Brussels` into
  `ZoneId.systemDefault()`, and is called at both render sites — `MatchesScreen.ScoreOrStatus`
  and (wrapping `matchMidText`) `MatchesTileService`. `matchMidText` itself stays pure.
- **Featured matches lead.** `Match.featured` = the fixture is promoted in Sporza's carousel
  (`livestreamMatches`' team-keys — including a scoreboard match that's also promoted, whose
  duplicate card is dropped). The parser keeps its sport-rank sort (so the `groupsVoetbalFirst`
  contract holds); "featured first" is applied per consumer: the list (`MatchesScreen.toEntries`)
  pulls featured into an **"Uitgelicht"** section above the per-sport sections, and the tile
  (`matchesTileModel`) uses `sortedBy { !featured }` (stable) for both the live rows and the
  upcoming fallback.
- Round screen: list/reader content needs horizontal `contentPadding` or text clips on the curve.
- Wear `HorizontalPageIndicator` forces itself to the bottom; the top dots are a custom Row.
- **Tiles don't scroll** — ProtoLayout has no `LazyColumn`. `MatchesTileService` shows a fixed
  `Column` of up to 3 live matches as scoreboard rows (sport emoji · home · accented score ·
  away) + a "+N meer" overflow line, and funnels the rest into the app; the real scrollable list
  is the Matches tab. The pure, Android-free, unit-tested logic lives in `MatchesTileModel.kt`:
  `matchesTileModel()` (selector + dedup), `matchMidText()` (score-or-status), `sportEmoji()`.
  Tennis renders the games as a subscript on each set count ("2₄-1₄") by splitting `score`/
  `subScore`; ProtoLayout has no real subscript, so it's a smaller bottom-aligned run.
- **Matches list `LazyColumn` keys must be unique** — key match cards by `detailUrl`, not
  `Match.id` (ids repeat when Sporza lists a fixture twice; duplicate keys crash on scroll).
- **Matches tile refreshes over the network on each tile request** — unlike the headline tile,
  which reads Room instantly, `matchesRepository` is in-memory and starts empty, so the tile
  calls `refresh()` then reads `matches().first()`. Requests a 1-min freshness interval (Wear
  throttles it), so it's "current score", not live-ticking. There's also a manual **refresh
  button** (teal `ic_refresh` in a card-tinted pill): a nested `Clickable` whose `LoadAction`
  re-triggers `onTileRequest` (→ `refresh()`) in place — so a tap here refetches scores without
  opening the app, while a tap anywhere else still deep-links to the Matches tab. Nested clickables:
  the inner refresh wins in its own bounds. It sits inline in `liveHeader()` (next to "● Live nu")
  when live; the upcoming/empty fallbacks have no header, so they keep the button at the bottom. The
  icon is mapped in `onTileResourcesRequest` (`RES_REFRESH`); bump `RES_VERSION` when tile resources
  change.
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
