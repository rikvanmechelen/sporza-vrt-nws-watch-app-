# Design updates — post-implementation

Changes made while implementing `VRT NWS Wear Redesign.dc.html` that the design doc should be
updated to match. Hand the block below to Claude Design to update the doc; the notes after it
are optional finer points.

## Instructions for Claude Design

> Please update "VRT NWS Wear Redesign.dc.html" to match the implemented app. Apply each change
> **both** in the Foundations spec tables **and** in every rendered 480×480 watch-frame mockup
> that uses the value, so the doc and the frames stay consistent.
>
> 1. **Headline / list-card title — shrink it.**
>    Was: `title1`, 24/600 (frames rendered ~22px). Now: **16sp, weight 600**. This is the
>    article title in the Kort & Sport list cards. Update the type-scale row
>    ("title1 · headline card") to note the card title renders at 16, and redraw the Kort/Sport
>    card mockups with 16px titles (they now fit ~3 fuller lines and a second card peeks more).
>
> 2. **Section header — shrink it.**
>    Was: `display3`, 30/700. Now: **26sp, weight 700** (still the largest chrome, just less
>    shouty). Update the "display3 · section header" row and the "Kort / Sport / Nieuws /
>    Matches" labels in every frame to 26px.
>
> 3. **Nieuws category label + match team/player names — shrink to 18.**
>    Was: `title2`, 20 (category "Alles/Politiek…" rows and the stacked team/player names). Now:
>    **18sp**. Update the Nieuws category-row mockup labels and the Matches team-name stacks to
>    18px. (Count pills, competition caption, and sport section labels are unchanged.)
>
>    **Do NOT change** (kept deliberately larger as the focal/hero elements):
>    - Match **score** hero — `display1` 40 (detail) / ~24 (list) stays.
>    - Article **reader** title — `title1` 24 stays (it's the one focal heading over 16px body).
>
> 4. **Sport headlines are green, not purple.** The colour legend currently groups
>    "Sport-headlines" under NEWS·purple, but the app colours by brand: VRT NWS (Kort, Nieuws) =
>    purple, Sporza (Sport feed **and** Matches) = green — which is what the Sport mockup frame
>    already shows. Fix the legend text to move "Sport headlines" from the purple group to the
>    green/Sporza group, so it matches the rendered green Sport frame.
>
> 5. **Drop the article-reader top progress bar.** On a round display a top-edge bar is clipped
>    to a thin chord and barely visible; making the title the first scaling list item already
>    solves the "clipped title header" problem it was meant to fix. Remove the 5px gradient top
>    bar from the Article reader frame and its caption note.

## Optional finer points

Reflect these only if you want the design to mirror the current code exactly:

- **Offline banner** renders just **"Offline"** (no "· 5 min oud" staleness) — cache age isn't
  tracked yet.
- **Complication** identity is carried by the title text ("VRT NWS" / "NWS · 1u"); accent colour
  is the watch face's to control, so the purple in that mockup is illustrative only.

## Resulting type scale (as shipped)

| Element                          | Size (sp) | Weight |
|----------------------------------|-----------|--------|
| Match score hero (detail)        | 40        | 800    |
| Section header                   | 26        | 700    |
| Article reader title             | 24        | 600    |
| Match score (list)               | 24        | 700/800|
| Category label · team/player name| 18        | 600    |
| Headline list-card title         | 16        | 600    |
| Article body                     | 16        | 400    |
| Timestamp                        | 13        | 400    |
| Competition label                | 11        | 600    |
