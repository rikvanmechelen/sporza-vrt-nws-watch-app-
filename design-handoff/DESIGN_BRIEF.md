# Design overhaul brief — "VRT NWS" Wear OS app

## Your role
You are designing a visual overhaul for an existing **Wear OS** watch app. Produce a
cohesive, modern design system and per-screen redesigns that I can implement in Jetpack
Compose for Wear. Work from this brief plus the attached screenshots (`screenshots/` =
the current app on the watch; `brand-reference/` = the real VRT NWS and Sporza websites
for brand color/voice). Favor a few strong, reusable decisions over per-screen novelty.

## What the app is
A standalone, unofficial personal app for the **Pixel Watch 4** that browses **VRT NWS**
(Flemish public-broadcaster news) and **Sporza** (its sports brand) headlines, reads full
articles on-wrist, and follows **live Sporza match scores**. It's news-at-a-glance plus
live scores — used in quick, one-handed glances, often outdoors. No official VRT/Sporza
branding assets are available, so *evoke* the brands (see palette below) without using
real logos/wordmarks.

## Brand palette (from the real sites — see `brand-reference/`)
The two brands have **distinct identities**, and the app spans both:
- **VRT NWS (news) → purple.** Deep violet `#302070` with a lighter lavender accent
  `~#A0A0F0`, on near-black.
- **Sporza (sport) → green/teal.** A teal→green range, roughly `#0CE0A6` (teal) to
  `#23E061` (green).

Important: the **current app uses a VRT yellow `#FFD200` accent, which matches neither
brand** — please replace it. A strong option is to **color-code by section**: purple for
the news feeds, green for Sport/Matches (and a clear "live" treatment). Propose the
system you think reads best; section-accent theming is encouraged but not mandatory.

## Hard platform constraints (must respect — designs that ignore these can't ship)
- **Round screen, 480×480 px (Pixel Watch 4).** Content near the edges clips on the
  curve — keep important content within a safe inset and expect generous horizontal
  padding. Design round-first.
- **Dark-first, OLED.** Background is essentially black for battery/contrast. Assume a
  single dark theme (no light mode). Avoid large bright fills.
- **Two rendering systems:**
  - **App screens**: Jetpack Compose for **Wear** with Wear Material. Colors must map to
    Wear's `Colors` slots and type to Wear's `Typography` scale (both listed below).
    Lists are `ScalingLazyColumn` — items scale down and fade toward the top/bottom edges
    (visible in the screenshots), driven by the rotary crown + touch.
  - **Tiles & complication**: **ProtoLayout** — a *restricted* layout system. No
    scrolling, no `LazyColumn`, no custom fonts, limited components (Box/Row/Column/Text/
    Image/Chip), no true rich text (e.g. no real subscript — currently approximated with a
    smaller bottom-aligned text run). Tiles are single, fixed, glanceable viewports.
- **Glanceability first.** These surfaces are read in ~1–2 seconds. Strong hierarchy,
  legible at a glance, minimal chrome.

## Wear design tokens the output must map to (please deliver values for these)
- **Colors** (Wear `Colors` slots): `primary`, `onPrimary`, `secondary`, `background`,
  `onBackground`, `surface`, `onSurface`, `onSurfaceVariant`, `error`. Plus any extra
  semantic accents you introduce (section accents, a "live" color) as named constants.
- **Typography** (Wear `Typography` styles): `display1–3`, `title1–3`, `body1–2`,
  `button`, `caption1–3`. Tell me which style to use where (size/weight/spacing
  suggestions welcome, but they map onto these named styles).
- **Shape/elevation** for cards, a **spacing** scale, and an **iconography** approach.

## Current design (starting point — change freely)
- Dark: `background` black, `surface` `#1B1B1F`, text white / `onSurfaceVariant` `#BFC3C9`.
- Off-brand accent `primary` = VRT yellow `#FFD200`; `secondary` `#8AB4F8` (barely used);
  `error` `#CF6679`; live indicator red `#E53935`.
- Default Wear Material typography (unchanged). Wear Material `Card`s for list items.
- Assessment: functional but generic — weak hierarchy, flat all-white text, ad-hoc accent
  use, off-brand color, and the live-scores tile can feel cramped.

## Surfaces to redesign (screenshots attached)
1. **Top-level navigation** — a horizontal pager over 4 pages (Kort / Sport / Nieuws /
   Matches) with a small top dot indicator; swipe-from-left-edge dismiss for detail
   screens. See the yellow section title + refresh icon and the dot row at the top.
   → How should page identity/section headers read on a round screen?
   Screenshots: `01-news-kort.png`, `02-news-sport.png`.
2. **Headline list** (Kort, Sport) — scrolling cards: thumbnail, title, relative
   timestamp; a tappable section-title "refresh" header at top. (`01`, `02`.)
3. **Category browser** (Nieuws) — a list of categories with counts to drill into.
   (`03-news-categories.png`.)
4. **Article reader** — long-form text as blocks (heading / paragraph / quote); an
   "Open op telefoon" fallback button when extraction fails. (`07-article-reader.png`.)
5. **Matches list** — sections grouped by sport (football first); each match a card with
   competition, teams/players, and a **score-or-status** element with a live indicator.
   (`04-matches-list.png` upcoming; `05-matches-live-scores.png` live.)
6. **Match detail** — a score header, then key events (goals/cards/subs), a "Fase per
   fase" live stream, and an editorial recap; "Open op telefoon" fallback.
   (`06-match-detail.png`.)
7. **Live-scores Tile** (ProtoLayout, glanceable — no screenshot; system-hosted) — a
   scoreboard of up to 3 live matches: each row = sport emoji · home · **score (the
   hero)** · away, plus a "+N meer" overflow and an upcoming-match fallback. Football
   shows a goal score ("2 - 1"); tennis shows sets won with the current-set games as a
   subscript-style accent ("2₄-1₄"). Long tennis-doubles names crowd the row — help this
   read well. (Layout mirrors `05-matches-live-scores.png`.)
8. **Latest-headline Tile** (ProtoLayout) — a single centered headline, tap to open.
9. **Complication** — latest headline as SHORT_TEXT / LONG_TEXT.
10. **States** for the above: loading (see the spinners), offline banner, empty, error/retry.

## Design goals
- A distinctive but restrained identity that reads as "public news + sport," honoring the
  purple (news) and green (sport) brand palettes.
- Clear typographic hierarchy and purposeful color (define the accent roles and a
  dedicated "live" treatment).
- Maximum legibility and glanceability on a small round OLED, one-handed, outdoors.
- Consistency across all surfaces, including the ProtoLayout tiles.

## Deliverables
1. A **design system**: color values and type/shape/spacing/iconography, expressed so they
   map cleanly onto Wear `Colors`/`Typography`.
2. **Annotated mockups** for each surface (round 480×480 frame), including the states.
3. **Component specs**: list card, section header, score/status element, buttons, the tile
   scoreboard row, indicators.
4. Notes on what changes vs. the current design and any Wear/ProtoLayout caveats per choice
   (especially the tile, given ProtoLayout's limits).

## Please avoid
- Light mode, large bright backgrounds, tiny low-contrast text, edge-to-edge content that
  clips on the curve, custom fonts in tiles, real VRT/Sporza logos, or interactions
  ProtoLayout/Wear can't do (rich text, scrollable tiles, hover).
