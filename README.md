# VRT NWS — Wear OS

A standalone Wear OS app (built for the Pixel Watch 4) that lists the latest VRT NWS
headlines and lets you read the full article natively on-wrist, with offline caching,
a Tile, and a watch-face complication.

Feed source: `https://www.vrt.be/vrtnws/nl.rss.articles.xml` (an **Atom** feed).

## Features

- Scrollable headline list with thumbnails and relative timestamps (rotary-crown + touch).
- Native article reader — the article page is fetched and its body extracted/reformatted
  for the round screen; a **"Open op telefoon"** button hands off to the phone browser.
- Offline caching (Room): headlines and previously read articles remain available offline.
- Manual refresh; swipe-from-left returns from an article to the list.
- **Tile** and **complication** showing the latest headline, tap to open the app.

## Architecture

Single `:app` module, Kotlin + Jetpack Compose for Wear OS.

```
data/
  remote/ AtomFeedParser (Jsoup XML), ArticleExtractor (Jsoup DOM), OkHttp services
  local/  Room: ArticleEntity + BlockConverters, ArticleDao, NewsDatabase, RoomArticleCache
  NewsContracts.kt (FeedService/ArticleService/ArticleCache/NewsRepository), DefaultNewsRepository
model/    Article, ArticleContent (ContentBlock: HEADING/PARAGRAPH/QUOTE)
ui/       MainActivity, AppRoot (SwipeToDismissBox navigation), theme,
          headlines/ (Screen + ViewModel), article/ (Screen + ViewModel)
tile/         LatestHeadlineTileService (ProtoLayout)
complication/ LatestHeadlineComplicationService
AppGraph.kt (manual DI), VrtNwsApp.kt (Application)
```

**Data flow:** `OkHttpFeedService` → `AtomFeedParser` → `NewsRepository` upserts into Room
and exposes a cache-first `Flow`. Opening an article returns the cached body if present,
otherwise `OkHttpArticleService` fetches the page → `ArticleExtractor` parses it → cached.

### Article extraction (the one fragile part)

`ArticleExtractor` is the single place to adjust if VRT changes their site. It is layered:

1. **DOM by `prose-article-*` classes** — primary; works for regular articles *and*
   liveblogs. Paragraphs use `prose-article-body-r/-sb`; headings `prose-article-h2/-h3`;
   quotes `prose-article-quote`. Byline/caption/tag variants (`-small-`, bare `-m`) and
   the page `h1` are excluded, which naturally drops sidebar noise ("Lees ook" etc.).
2. **JSON-LD `articleBody` / `liveBlogUpdate[].articleBody`** — fallback.
3. **Generic long-paragraph heuristic** — last resort.

If all fail, the reader shows the "Open op telefoon" fallback.

## Build & deploy

Requires Android Studio's JDK (21) for Gradle; SDK platform 36 + build-tools 36.1.0.

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew test                 # JVM unit tests (parser, extractor, repository, viewmodels)
./gradlew :app:assembleDebug   # build APK
./gradlew :app:installDebug    # install to a connected watch / emulator
```

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
