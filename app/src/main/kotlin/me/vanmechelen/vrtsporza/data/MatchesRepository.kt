package me.vanmechelen.vrtsporza.data

import me.vanmechelen.vrtsporza.model.Match
import me.vanmechelen.vrtsporza.model.MatchDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** Fetches the Sporza match calendar and per-match detail pages. */
interface MatchesService {
    suspend fun fetchCalendar(): List<Match>
    suspend fun fetchDetail(url: String): MatchDetail
}

/**
 * Single source of truth for the Matches section. Scores are ephemeral, so this keeps an
 * in-memory cache (no Room): the calendar is a hot flow refreshed on demand; match details
 * are cached by url for the lifetime of the process.
 */
interface MatchesRepository {
    /** Emits the cached calendar (grouped voetbal-first); updates on [refresh]. */
    fun matches(): Flow<List<Match>>

    /** Fetches a fresh calendar and publishes it. The last good calendar is kept on failure. */
    suspend fun refresh(): Result<Unit>

    /** Returns a match detail, cache-first by url; fetches and caches it on a miss. */
    suspend fun detail(url: String): Result<MatchDetail>
}

class DefaultMatchesRepository(
    private val service: MatchesService,
) : MatchesRepository {

    private val calendar = MutableStateFlow<List<Match>>(emptyList())
    private val detailCache = ConcurrentHashMap<String, MatchDetail>()

    override fun matches(): Flow<List<Match>> = calendar.asStateFlow()

    override suspend fun refresh(): Result<Unit> = runCatching {
        // runCatching leaves `calendar` untouched if the fetch throws → last good is preserved.
        calendar.value = service.fetchCalendar()
    }

    override suspend fun detail(url: String): Result<MatchDetail> = runCatching {
        detailCache[url]?.let { return@runCatching it }
        val fresh = service.fetchDetail(url)
        if (!fresh.isEmpty) detailCache[url] = fresh
        fresh
    }
}
