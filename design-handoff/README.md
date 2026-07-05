# Design handoff — VRT NWS Wear OS app

Everything needed to design a visual overhaul. Start with **`DESIGN_BRIEF.md`**.

## Contents

- **`DESIGN_BRIEF.md`** — the brief: what the app is, hard Wear OS / ProtoLayout
  constraints, the brand palette, the surfaces to redesign, and the deliverables.
- **`screenshots/`** — the current app on the watch (round 480×480), one per surface:
  - `01-news-kort.png` — headline list (Kort)
  - `02-news-sport.png` — headline list (Sport), refreshing
  - `03-news-categories.png` — Nieuws category browser
  - `04-matches-list.png` — Matches list, upcoming fixture
  - `05-matches-live-scores.png` — Matches list, live (football + tennis set scores)
  - `06-match-detail.png` — match detail header + fallback
  - `07-article-reader.png` — article reader
  - (The two Tiles and the complication are system-hosted glances and can't be
    screenshotted; they're described in the brief.)
- **`brand-reference/`** — the real websites, for brand color/voice (NOT to copy):
  - `vrt-nws-website-purple.png` — VRT NWS (news) — purple identity
  - `sporza-website-green.png` — Sporza (sport) — green/teal identity

## The one-line ask
Replace the current off-brand yellow with a cohesive dark, round-first system that honors
**purple (news)** and **green (sport)**, with strong glanceable hierarchy — mapped onto
Wear's `Colors`/`Typography` so it's implementable.
