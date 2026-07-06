package me.vanmechelen.vrtsporza.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography

/**
 * The two brand identities the app spans. Colour follows the *brand of the content*: VRT NWS
 * (Kort, Nieuws) reads purple; Sporza (Sport headlines, Matches) reads green.
 */
enum class Section { NEWS, SPORT }

// --- Shared neutrals (dark-first OLED) ---
private val Background = Color.Black // true OLED black
private val Surface = Color(0xFF17171B) // card base
private val OnSurfaceVariant = Color(0xFFA6AAB2) // timestamps, meta
private val ErrorCoral = Color(0xFFFF8A80)

// --- News · purple (VRT NWS) ---
private val NewsColors = Colors(
    primary = Color(0xFFA99CFF), // lavender — headers, links, focus
    primaryVariant = Color(0xFF302070), // deep violet — badges, accent bar
    onPrimary = Color(0xFF0B0B0F), // text on lavender fills
    secondary = Color(0xFFC7C0FF), // lighter lavender — count chips
    secondaryVariant = Color(0xFF302070),
    background = Background,
    onBackground = Color.White,
    surface = Surface,
    onSurface = Color.White,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorCoral,
)

// --- Sport · green/teal (Sporza) ---
private val SportColors = NewsColors.copy(
    primary = Color(0xFF2FE07A), // green — scores, headers
    primaryVariant = Color(0xFF0A5E4A),
    onPrimary = Color(0xFF04120C),
    secondary = Color(0xFF8FE9BC), // teal — competition labels, set-games subscript, section labels
    secondaryVariant = Color(0xFF0CE0A6),
)

/**
 * Extra semantic accents that don't map onto a Wear [Colors] slot. Referenced directly by name
 * (they're the same in both sections).
 */
object VrtAccents {
    /** Live indicator — coral. Used ONLY as a pulsing dot, never as a fill. */
    val Live = Color(0xFFFF5147)

    /** "LIVE · 67'" pill text + its translucent background. */
    val LiveText = Color(0xFFFF7A72)
    val LiveContainer = Color(0x1FFF5147) // rgba(255,81,71,.12)

    /**
     * Data-freshness marker label — a dim neutral gray. Deliberately quieter than
     * [OnSurfaceVariant] meta text so "bijgewerkt …" never competes with real content or per-item
     * timestamps; the section accent lives only in the marker's dot. (Nudged up from the handoff's
     * #6E7078, which read too dark on-device.)
     */
    val FreshnessLabel = Color(0xFF9A9DA4)

    /** Offline staleness banner — amber pill. */
    val OfflineText = Color(0xFFFFC38A)
    val OfflineContainer = Color(0xFF2A2118)
    val OfflineBorder = Color(0x4DFFB464) // rgba(255,180,100,.3)

    /** Sport "Open op telefoon" handoff button gradient (teal → green). */
    val SportGradientStart = Color(0xFF0CE0A6)
    val SportGradientEnd = Color(0xFF2FE07A)
}

private fun colorsFor(section: Section) = if (section == Section.SPORT) SportColors else NewsColors

/**
 * The design mockups are drawn on a 480px canvas that equals the whole watch — but the device is
 * 480 physical px at density 2.0, so a naive `dp`/`sp` == design-px mapping renders everything at
 * 2× the intended scale. Since every size in the UI *is* authored equal to its design px, scaling
 * the density down by this factor makes 1dp ≈ 1 design px, so the app matches the mockups
 * pixel-for-pixel. Tune this single knob to make the whole app a touch bigger/smaller.
 */
private const val DESIGN_SCALE = 1.0f

/**
 * Type scale mapped to Wear's [Typography] slots. Hierarchy is weight + size + colour (survives
 * on tiles, which force the system font). Slots we don't override keep Wear's defaults.
 */
private val VrtTypography = Typography(
    display1 = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = (-0.5).sp), // score hero
    display3 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp), // section header
    title1 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.2).sp), // headline card
    title2 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp), // teams
    title3 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    body1 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp), // article
    body2 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    button = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    caption1 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp), // timestamp
    caption2 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    caption3 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp), // competition
)

/**
 * Applies the dark, brand-coded theme for a [section] — [Section.NEWS] is purple,
 * [Section.SPORT] green. Wrap each pager page / detail screen so `Colors.primary` swaps per
 * section without a global re-theme.
 */
@Composable
fun VrtNwsTheme(section: Section = Section.NEWS, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val scaled = Density(density = base.density * DESIGN_SCALE, fontScale = base.fontScale)
    MaterialTheme(colors = colorsFor(section), typography = VrtTypography) {
        CompositionLocalProvider(LocalDensity provides scaled, content = content)
    }
}
