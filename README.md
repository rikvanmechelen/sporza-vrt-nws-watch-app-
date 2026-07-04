# VRT NWS — Wear OS

A standalone Wear OS app (built for the Pixel Watch 4) that lists the latest VRT NWS
headlines and lets you read the full article natively on-wrist, with offline caching,
a Tile, and a watch-face complication.

Feed source: `https://www.vrt.be/vrtnws/nl.rss.articles.xml` (an **Atom** feed).

## Features

- **Three sources in a horizontal pager**, with a dot indicator at the top — swipe
  left/right between **Kort** (VRT NWS top headlines), **Sport** (Sporza) and **Nieuws**
  (VRT NWS latest). Each is its own headline list.
- Scrollable headline list with thumbnails and relative timestamps (rotary-crown + touch).
- Native article reader — the article page is fetched and its body extracted/reformatted
  for the round screen; a **"Open op telefoon"** button hands off to the phone browser.
- Offline caching (Room): headlines and previously read articles remain available offline.
- **Tap the source title** to force-refresh that feed. Swipe from the left edge returns
  from an article to the list.
- **Tile** and **complication** showing the latest headline, tap to open the app.

## Architecture

Single `:app` module, Kotlin + Jetpack Compose for Wear OS.

```
data/
  remote/ AtomFeedParser (Jsoup XML), ArticleExtractor (Jsoup DOM), OkHttp services
  local/  Room: ArticleEntity (id+source) + ArticleBodyEntity (url) + BlockConverters,
          ArticleDao, NewsDatabase, RoomArticleCache
  NewsContracts.kt (FeedService/ArticleService/ArticleCache/NewsRepository), DefaultNewsRepository
model/    Article, ArticleContent (ContentBlock: HEADING/PARAGRAPH/QUOTE), NewsSource (feeds)
ui/       MainActivity, AppRoot (HorizontalPager over sources + SwipeToDismissBox reader), theme,
          headlines/ (Screen + ViewModel, per-source), article/ (Screen + ViewModel)
tile/         LatestHeadlineTileService (ProtoLayout)
complication/ LatestHeadlineComplicationService
AppGraph.kt (manual DI), VrtNwsApp.kt (Application)
```

**Sources:** `NewsSource` enum holds the three Atom feed URLs (all parsed by the one
`AtomFeedParser`). Headlines are cached per `(id, source)` — feeds overlap — while article
bodies are cached once, keyed by `url`.

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

Add the **Tile** (swipe the tile carousel → `+` → VRT NWS) and the **complication**
(long-press watch face → Edit → pick a slot → VRT NWS) from the watch UI.

## Notes

- Version stack chosen for stability: AGP 8.7.3, Kotlin 2.0.21, Wear Compose 1.4.1,
  navigation-compose 2.7.7, Room 2.6.1 (`compileSdk 36` warning suppressed in
  `gradle.properties`).
- Navigation uses a manual `SwipeToDismissBox` rather than `SwipeDismissableNavHost`
  (the latter would not render its start destination with this dependency set).
- The Wear **emulator** has no usable internet unless paired with a phone companion and
  its `screencap` renders black under SwiftShader — verify visuals on the real watch.
- Unofficial personal use of a public feed; no VRT branding assets are bundled.
```
