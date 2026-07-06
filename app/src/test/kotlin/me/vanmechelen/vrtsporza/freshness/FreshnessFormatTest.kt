package me.vanmechelen.vrtsporza.freshness

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class FreshnessFormatTest {

    private val s = 1_000L
    private val min = 60 * s
    private val h = 60 * min
    private val d = 24 * h

    private fun age(deltaMs: Long) = relativeAge(syncedAtEpochMs = 0L, nowEpochMs = deltaMs)

    // --- relativeAge: the tiered Dutch label from the handoff spec ---

    @Test
    fun underTenSecondsIsZonet() {
        assertEquals("zonet", age(0))
        assertEquals("zonet", age(9 * s))
    }

    @Test
    fun secondsRange() {
        assertEquals("10 s geleden", age(10 * s))
        assertEquals("59 s geleden", age(59 * s))
    }

    @Test
    fun minutesRange() {
        assertEquals("1 min geleden", age(60 * s))
        assertEquals("1 min geleden", age(119 * s)) // floors
        assertEquals("2 min geleden", age(2 * min))
        assertEquals("59 min geleden", age(3599 * s))
    }

    @Test
    fun hoursRange() {
        assertEquals("1 u geleden", age(h))
        assertEquals("23 u geleden", age(23 * h + 59 * min))
    }

    @Test
    fun daysRange() {
        assertEquals("1 d geleden", age(d))
        assertEquals("3 d geleden", age(3 * d + 5 * h))
    }

    @Test
    fun futureOrClockSkewReadsAsZonet() {
        // A synced-at slightly in the future (device clock skew) must never render a negative age.
        assertEquals("zonet", relativeAge(syncedAtEpochMs = 5_000L, nowEpochMs = 0L))
    }

    // --- syncedClock: the tile's always-correct absolute wall-clock time ---

    private val utc = ZoneId.of("UTC")

    @Test
    fun clockIsZeroPaddedWallTimeInTheGivenZone() {
        assertEquals("00:00", syncedClock(0L, utc))
        // 1e9 s after the epoch = 2001-09-09T01:46:40Z.
        assertEquals("01:46", syncedClock(1_000_000_000_000L, utc))
    }

    @Test
    fun clockConvertsToTheTargetZone() {
        // Same instant, Tokyo (UTC+9): 01:46Z → 10:46.
        assertEquals("10:46", syncedClock(1_000_000_000_000L, ZoneId.of("Asia/Tokyo")))
    }
}
