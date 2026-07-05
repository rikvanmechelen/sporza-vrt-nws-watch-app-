# VRT NWS — Wear OS

A standalone Wear OS app (built for the Pixel Watch 4) that lists the latest VRT NWS
headlines, lets you read the full article natively on-wrist, and follows live Sporza
match scores — with offline caching, two Tiles, and a watch-face complication.

Feed source: `https://www.vrt.be/vrtnws/nl.rss.articles.xml` (an **Atom** feed).
Live scores come from the Sporza calendar (`https://sporza.be/nl/kalender`, scraped HTML).

## Features

- **Four pages in a horizontal pager**, with a dot indicator at the top — swipe left/right
  between **Kort** (VRT NWS top headlines), **Sport** (Sporza), **Nieuws** (VRT NWS latest,
  browsable by category) and **Matches** (live Sporza scores). Each is its own screen.
- Scrollable headline list with thumbnails and relative timestamps (rotary-crown + touch).
- Native article reader — the article page is fetched and its body extracted/reformatted
  for the round screen; a **"Open op telefoon"** button hands off to the phone browser.
- **Matches**: live match results grouped by sport (voetbal first) with scores and status.
  Tap a match for its detail — quick events (goals / subs / cards), the **"Fase per fase"**
  live update stream, and the match recap.
- Offline caching (Room) for headlines and read articles; match scores are cached in-memory
  (they are ephemeral) and refreshed on demand.
- **Tap the section title** to force-refresh that page. Swipe from the left edge returns from
  an article or match detail to the list.
- Two **Tiles** — a *latest headline* glance and a *live scores* glance — plus a
  **complication** showing the latest headline. The live-scores tile is a scoreboard of up to
  3 live matches (football first): a sport emoji, home/away, and the score as the accented hero;
  it shows a "+N meer" overflow and falls back to the next upcoming match when nothing is live.
  Tapping a tile opens the app; the live-scores tile deep-links straight to the Matches page.

## Architecture

Single `:app` module, Kotlin + Jetpack Compose for Wear OS.

```
data/
  remote/ AtomFeedParser (Jsoup XML), ArticleExtractor (Jsoup DOM), OkHttp services,
          MatchCalendarParser + MatchDetailExtractor (Jsoup, Sporza scores),
          ScheduleParser (real kickoff times from the schedule API)
  local/  Room: ArticleEntity (id+source) + ArticleBodyEntity (url) + BlockConverters,
          ArticleDao, NewsDatabase, RoomArticleCache
  NewsContracts.kt (FeedService/ArticleService/ArticleCache/NewsRepository), DefaultNewsRepository
  MatchesRepository.kt (MatchesService + MatchesRepository + in-memory DefaultMatchesRepository)
model/    Article, ArticleContent (ContentBlock: HEADING/PARAGRAPH/QUOTE), NewsSource (feeds),
          Match / MatchDetail (MatchEvent, StreamItem) + MatchSports
ui/       MainActivity, AppRoot (HorizontalPager over a PagerTab list + SwipeToDismissBox
          reader/detail), theme, headlines/ (Screen + ViewModel, per-source),
          article/ (Screen + ViewModel), matches/ (Screen + ViewModel, Detail Screen + ViewModel)
tile/         LatestHeadlineTileService + MatchesTileService (ProtoLayout); MatchesTileModel
              (pure: live-first selector + dedup, sportEmoji, matchMidText, localizeKickoffTime)
complication/ LatestHeadlineComplicationService
AppGraph.kt (manual DI), VrtNwsApp.kt (Application)
```

**Sources:** `NewsSource` enum holds the three Atom feed URLs (all parsed by the one
`AtomFeedParser`). Headlines are cached per `(id, source)` — feeds overlap — while article
bodies are cached once, keyed by `url`. The pager iterates a `PagerTab` sealed type
(`News(source)` × 3, then `Matches`) rather than `NewsSource` directly, so Matches — which
is not feed/article-shaped — lives on a parallel path with its own repository.

**Data flow:** `OkHttpFeedService.fetchHeadlines(source.feedUrl)` → `AtomFeedParser` →
`NewsRepository.refresh(source)` upserts into Room and `headlines(source)` exposes a cache-first
`Flow`. Opening an article returns the cached body (by url) if present, otherwise
`OkHttpArticleService` fetches the page → `ArticleExtractor` parses it → cached.

### Article extraction (the one fragile part)

`ArticleExtractor` is the single place to adjust if VRT changes their site. It is layered:

1. **DOM by `prose-article-*` classes** — primary for VRT NWS; works for regular articles
   *and* liveblogs. Paragraphs use `prose-article-body-r/-sb`; headings `prose-article-h2/-h3`;
   quotes `prose-article-quote`. Byline/caption/tag variants (`-small-`, bare `-m`) and
   the page `h1` are excluded, which naturally drops sidebar noise ("Lees ook" etc.).
2. **JSON-LD `articleBody` / `liveBlogUpdate[].articleBody`** — fallback (Sporza live match pages).
3. **Main-scoped `<p>`/`<h2>` heuristic** — catches sites without `prose-article-*` classes
   (e.g. regular Sporza articles, whose body sits in `<p>` inside `mainBody`), scoped to the
   main container to skip nav/related teasers; falls back to whole-document if needed.

If all fail, the reader shows the "Open op telefoon" fallback.

### Matches (live scores)

The **Matches** section is HTML-scraped (no JSON dependency), mirroring the feed/article
extractors, and cached in memory (`DefaultMatchesRepository`) since scores are ephemeral:

- **List** — `MatchCalendarParser` parses `https://sporza.be/nl/kalender`. Each match is a
  `<a>` scoreboard whose href (`/nl/sport/{sport}/~{id}/`) gives the sport; matches are
  grouped voetbal-first and de-duplicated (Sporza repeats featured fixtures). Matches that Sporza
  **promotes** in its carousel are flagged `featured` and shown first — under an "Uitgelicht"
  section at the top of the tab, and ahead of the rest on the live-scores tile. Scoreboard markup
  differs per sport, so `Match.home/away/score` are nullable and `Match.title` is the
  always-present fallback:
  - **football** = two `teamname` blocks (with logos) + a goal score (`3 - 2`);
  - **tennis** = two `setsPlayer` sides (singles one name, doubles two per side); the score is
    sets won (`1 - 2`) with the current set's games in `Match.subScore` (`4-3`), read from the
    `_set_` game spans — the tile shows those games as a subscript on each set count;
  - **cycling / no-team sports** = neither, so only `Match.title` renders.
- **Detail** — `MatchDetailExtractor` parses the match page into three regions: quick events
  from the field-timeline widget (pre-formatted "29' - Doelpunt - …"), the **"Fase per fase"**
  live stream (`.sw-timeline-item`), and the editorial recap (article prose, excluding the
  live widgets, reusing `ContentBlock`). Empty result → "Open op telefoon" fallback.
- Sporza uses hashed CSS-module class names (`_scoreboard_mdatp_36`), so selectors match on
  `[class*=prefix]`, never exact names — the single place to adjust if Sporza changes markup.
- **Status** is read from the scoreboard's CSS state and a11y text (LIVE / UPCOMING / FINISHED).
  A match Sporza labels `nu` ("now" — on court, but no live class or score yet) counts as live, so
  a just-started match isn't left showing its (now-past) kickoff time as if still upcoming.
- **Kickoff times** — scoreboard fixtures carry the kickoff in their text, but Sporza's promoted
  "livestream-card" carousel matches carry only a TV *broadcast window* (which starts a variable
  10–30 min before kickoff). For those, `ScheduleParser` reads the real kickoff from the schedule
  API (`https://api.sporza.be/web/content/schedule?date=YYYY-MM-DD`) and patches it in. All times
  are Sporza's Europe/Brussels wall-clock, and are **converted to the watch's own timezone** for
  display (`localizeKickoffTime`), so a 22:00 Brussels kickoff reads 16:00 on a US-East watch.
- Sporza also exposes a per-match live-scoreboard JSON API
  (`https://api.sporza.be/web/content/{sport}/matches/{id}`, with a poll `interval`) for
  live match scores; it is **not** used yet — the app is tap-to-refresh — but is the natural
  upgrade path for auto-polling.

## Build & deploy

Requires Android Studio's JDK (21) for Gradle; SDK platform 36 + build-tools 36.1.0.

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew test                 # JVM unit tests (parser, extractor, repository, viewmodels)
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest   # Compose UI tests (on emulator)
./gradlew :app:assembleDebug   # build APK
./gradlew :app:installDebug    # install to a connected watch / emulator
```

Development happens on the emulator; only the **release** build is pushed to the watch
(debug scrolling is janky). See `CLAUDE.md` for the full workflow and gotchas.

### Deploy to the Pixel Watch 4

On the watch: Settings → Developer options → enable **Wireless debugging**, then:

```bash
adb pair <watch-ip:pair-port>       # one-time, code shown on watch
adb connect <watch-ip:debug-port>
./gradlew :app:installDebug
```

Add a **Tile** (swipe the tile carousel → `+` → VRT NWS: *Laatste nieuws* or *Live scores*)
and the **complication** (long-press watch face → Edit → pick a slot → VRT NWS) from the
watch UI.

## Notes

- Version stack chosen for stability: AGP 8.7.3, Kotlin 2.0.21, Wear Compose 1.4.1,
  navigation-compose 2.7.7, Room 2.6.1 (`compileSdk 36` warning suppressed in
  `gradle.properties`).
- Navigation uses a manual `SwipeToDismissBox` rather than `SwipeDismissableNavHost`
  (the latter would not render its start destination with this dependency set).
- The Wear **emulator** has no usable internet unless paired with a phone companion.
- **Screenshots** work on both emulator and watch: `adb -s <serial> exec-out screencap -p >
  shot.png` (480×480). Launch a screen first with `am start … --es tab matches` (cold start
  only), and drive navigation with `adb shell input swipe/tap`. Tiles/complication are
  system-hosted glances and can't be screenshotted — check those on the watch. See `CLAUDE.md`.
- Unofficial personal use of a public feed; no VRT branding assets are bundled.
```
