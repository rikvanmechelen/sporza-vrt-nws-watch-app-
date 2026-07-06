package me.vanmechelen.vrtsporza.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import me.vanmechelen.vrtsporza.R
import me.vanmechelen.vrtsporza.ui.theme.VrtAccents

/** Card background base (all list cards); the top/accent card tints this with the section accent. */
val CardColor = Color(0xFF141318)

/** Standard list-card corner radius (design: 26dp). */
val CardRadius = 26.dp

/**
 * The tappable section header: an accent-tinted [display3] label with the refresh affordance
 * built in (a glyph that swaps to an inline spinner while refreshing). Reused by every list
 * screen so page identity + refresh read the same everywhere.
 */
@Composable
fun SectionHeader(title: String, isRefreshing: Boolean, onRefresh: () -> Unit) {
    val refreshLabel = stringResource(R.string.refresh)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRefresh)
            .semantics { contentDescription = refreshLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.display3,
            color = MaterialTheme.colors.primary,
        )
        Spacer(Modifier.width(8.dp))
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                indicatorColor = MaterialTheme.colors.primary,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colors.primary,
            )
        }
    }
}

/** Amber "offline / stale cache" pill — cached content stays readable underneath. */
@Composable
fun OfflineBanner(text: String = stringResource(R.string.offline_pill)) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(VrtAccents.OfflineContainer)
            .border(BorderStroke(1.dp, VrtAccents.OfflineBorder), CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_wifi_off),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = VrtAccents.OfflineText,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.caption2,
            color = VrtAccents.OfflineText,
        )
    }
}

/** The live indicator: a coral dot that pulses (scale + fade). Never used as a fill. */
@Composable
fun LiveDot(size: Dp = 9.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(VrtAccents.Live.copy(alpha = alpha)),
    )
}

/** The "LIVE · 67'" pill on the match-detail header: pulsing dot + coral label on a coral tint. */
@Composable
fun LivePill(text: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(VrtAccents.LiveContainer)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LiveDot(size = 7.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.caption2,
            color = VrtAccents.LiveText,
        )
    }
}

/** A filled accent pill that hands off to the phone. Used for the "Open op telefoon" fallback. */
@Composable
fun HandoffButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colors.primary)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phone),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colors.onPrimary,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = stringResource(R.string.open_on_phone),
            style = MaterialTheme.typography.button,
            color = MaterialTheme.colors.onPrimary,
        )
    }
}

/** A base list card: the section-accent top card gets a tinted fill + a 1dp accent inset ring. */
@Composable
fun ListCard(
    onClick: () -> Unit,
    accent: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(CardRadius)
    val primary = MaterialTheme.colors.primary
    val bg = if (accent) primary.copy(alpha = 0.06f).compositeOver(CardColor) else CardColor
    var m = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(bg)
    if (accent) m = m.border(BorderStroke(1.dp, primary.copy(alpha = 0.16f)), shape)
    Box(m.clickable(onClick = onClick).padding(14.dp)) { content() }
}

/** Loading: a section-tinted spinner over dim skeleton cards mirroring the real layout. */
@Composable
fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(26.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                indicatorColor = MaterialTheme.colors.primary,
            )
            Spacer(Modifier.height(20.dp))
            SkeletonCard(alpha = 1f)
            Spacer(Modifier.height(10.dp))
            SkeletonCard(alpha = 0.6f)
        }
    }
}

@Composable
private fun SkeletonCard(alpha: Float) {
    val bar = Color(0xFF22212A).copy(alpha = alpha)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardColor.copy(alpha = alpha))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(bar))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(11.dp).clip(CircleShape).background(bar))
            Box(Modifier.fillMaxWidth(0.65f).height(11.dp).clip(CircleShape).background(bar))
        }
    }
}

/** Friendly empty state: a section-tinted glyph, a headline, and a supporting line. */
@Composable
fun EmptyState(iconRes: Int, title: String, subtitle: String? = null) {
    Box(Modifier.fillMaxSize().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colors.primary,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Error state: an error-tinted glyph over one clear outlined retry (≥48dp tap target). */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = 36.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colors.error,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(BorderStroke(1.5.dp, MaterialTheme.colors.error), CircleShape)
                    .clickable(onClick = onRetry)
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 22.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colors.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.retry),
                    style = MaterialTheme.typography.button,
                    color = MaterialTheme.colors.error,
                )
            }
        }
    }
}
