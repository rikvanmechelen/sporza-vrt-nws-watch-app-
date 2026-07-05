# Typography spec — match the design to the implementation

**The implementation is now the source of truth for type sizes.** Please update the mockups so
every text element uses the size / weight / letter-spacing below. (Colors are already brand-coded
in code — purple for news, green for sport — matching the brief; this doc is about *type*.)

## Two separate scales — do not share them
- **App screens** (Kotlin/Compose) use the custom scale in §1. Canvas convention: a **480×480
  round** frame is the whole watch, and the app is density-tuned so **1 design px = 1 sp** — author
  text at these sp values as px on the 480 canvas and it matches the device pixel-for-pixel.
- **Tiles** (ProtoLayout) use the **smaller, separate** scale in §3, and are forced to the
  **system font** (no custom font, no real subscript). Don't reuse the app sizes on the tile.

Hierarchy comes from **weight + size + letter-spacing + color**, not font family (so it survives
on tiles). Keep the weights and tracking, not just the sizes.

## §1 App type scale (the named styles)
| Style      | Size | Weight         | Letter-spacing | Line-height | Used for |
|------------|------|----------------|----------------|-------------|----------|
| display1   | 40   | ExtraBold (800)| −0.5           | —           | match-**detail** score hero |
| display3   | 26   | Bold (700)     | −0.3           | —           | page **section header** ("Kort", "Matches") |
| title1     | 24   | SemiBold (600) | −0.2           | —           | article title; match-card **score** (ExtraBold, tabular figures) |
| title2     | 20   | SemiBold (600) | −0.2           | —           | article H2; detail home/away; empty-state title |
| title3     | 16   | SemiBold (600) | —              | —           | detail sub-section titles; error title |
| body1      | 16   | Normal (400)   | —              | 24          | article paragraphs (quote = italic) |
| body2      | 14   | Normal (400)   | —              | 20          | event / live-stream text |
| button     | 15   | Medium (500)   | —              | —           | buttons; category **count pill** |
| caption1   | 13   | Normal (400)   | —              | —           | timestamps; subtitles; event minute |
| caption2   | 12   | Medium (500)   | —              | —           | offline banner |
| caption3   | 11   | SemiBold (600) | +0.8           | —           | competition label (UPPERCASE); score caption |

(Sizes in `sp`. Only these slots are defined; anything else keeps Wear defaults but is unused.)

## §2 Per-element sizes that override the scale
A few elements deliberately deviate from their base style — use these exact values:
| Element | Size | Weight | Notes |
|---------|------|--------|-------|
| Headline card **title** (Kort/Sport list) | **16** | SemiBold | base title2 shrunk to 16; up to 3 lines |
| Category row **label** (Nieuws) | **18** | SemiBold | title2 shrunk to 18 |
| Match **teams/players** (Matches list) | **18** | SemiBold | title2 shrunk to 18 |
| Match **score** (Matches list) | 24 | ExtraBold | title1 + tabular figures |
| Tennis **games subscript** (on the score) | ≈13 (**0.55×** the score) | Bold | subscript baseline, teal/secondary color |
| Group header ("Uitgelicht", "Voetbal") | 13 | Bold | caption1 bold, secondary color |

## §3 Tile type scale (ProtoLayout — separate, system font)
The live-scores tile is much smaller than the app list. Use these on the tile mockup:
| Tile element | Size | Weight |
|--------------|------|--------|
| "LIVE NU" header | 10 | Bold |
| Sport emoji | 13 | — |
| Team / player name | 12 | Medium |
| **Score** (hero) | 18 | Bold |
| Games subscript | 10 | Bold |
| "+N meer" footer | 10 | Bold |
| Empty-state text | 12 | Normal |

The *latest-headline* tile uses ProtoLayout's built-in Material presets (TITLE1 / CAPTION1 /
CAPTION2) at their platform-default sizes — no custom sizes there.

## Ask
Re-set every text layer in the mockups to the values above (app screens = §1/§2, tiles = §3),
keeping the weights and letter-spacing. Flag anything that now feels too big/small at these sizes
so we can tune the scale rather than diverge from the build.
