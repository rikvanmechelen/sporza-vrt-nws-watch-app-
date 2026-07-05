# Play Store listing assets

Graphics for the Google Play listing (internal-testing track). All dynamic content is
**lorem ipsum** on purpose — no VRT/Sporza articles or scores appear in the public listing.

| File | Purpose | Play spec |
|---|---|---|
| `play_icon_512.png` | Hi-res app icon | 512×512, 32-bit PNG (alpha) |
| `screen1_matches.png` | Screenshot — Matches list | 480×480, 24-bit PNG |
| `screen2_detail.png` | Screenshot — match detail (lead hero) | 480×480, 24-bit PNG |
| `screen3_article.png` | Screenshot — article reader | 480×480, 24-bit PNG |
| `screen4_headlines.png` | Screenshot — headlines list | 480×480, 24-bit PNG |

Each PNG has its `*.svg` source alongside it. The icon SVG rasterizes the app's actual
launcher drawables (`app/src/main/res/.../ic_launcher_*`); the screenshots are vector mockups
matched to the real app's palette (`ui/theme/Theme.kt`) and layouts.

Regenerate after editing an SVG:

```bash
rsvg-convert -w 512 -h 512 icon.svg -o play_icon_512.png   # icon
rsvg-convert -w 480 -h 480 screen1_matches.svg -o screen1_matches.png   # etc.
# then normalise formats: icon → RGBA, screenshots → RGB (no alpha)
magick play_icon_512.png -alpha on -define png:color-type=6 play_icon_512.png
magick screenN.png -background black -alpha remove -define png:color-type=2 screenN.png
```

Still to produce before submitting: a **1024×500 feature graphic**.
