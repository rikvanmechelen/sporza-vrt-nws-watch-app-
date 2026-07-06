package me.vanmechelen.vrtsporza.freshness

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure, Android-free formatting for the "data freshness" marker (see the Freshness Indicator
 * handoff). Kept here so both the Compose screens and the ProtoLayout tiles share one definition
 * and it stays unit-testable off-device.
 *
 * Two forms, deliberately different per surface:
 *  - [relativeAge] — the tiered Dutch "X geleden" string for **screens**, which re-render it on a
 *    ticker so it ages while visible (always correct because the screen recomputes it).
 *  - [syncedClock] — the absolute wall-clock time for **tiles**. A tile is a frozen snapshot that
 *    only re-renders every 1–30 min, so a relative "X geleden" baked into it would go stale and
 *    lie. An absolute "14:23" never becomes wrong no matter when the user glances at it.
 */

private const val SECOND = 1_000L
private const val MINUTE = 60 * SECOND
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/**
 * The relative-time fragment for a screen marker (the "bijgewerkt " prefix is added at the string
 * resource). Tiers per the handoff: <10s "zonet", <60s "N s geleden", <60min "N min geleden",
 * <24u "N u geleden", else "N d geleden". A [nowEpochMs] before [syncedAtEpochMs] (device clock
 * skew) clamps to "zonet" rather than showing a negative age.
 */
fun relativeAge(syncedAtEpochMs: Long, nowEpochMs: Long): String {
    val delta = nowEpochMs - syncedAtEpochMs
    return when {
        delta < 10 * SECOND -> "zonet"
        delta < MINUTE -> "${delta / SECOND} s geleden"
        delta < HOUR -> "${delta / MINUTE} min geleden"
        delta < DAY -> "${delta / HOUR} u geleden"
        else -> "${delta / DAY} d geleden"
    }
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** The zero-padded 24h wall-clock time of [syncedAtEpochMs] in [zone] (default: the watch's zone). */
fun syncedClock(syncedAtEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(syncedAtEpochMs).atZone(zone).format(CLOCK)
