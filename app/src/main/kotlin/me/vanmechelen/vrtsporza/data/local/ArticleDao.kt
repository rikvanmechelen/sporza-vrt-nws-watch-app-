package me.vanmechelen.vrtsporza.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ArticleDao {

    @Query("SELECT * FROM articles WHERE source = :source ORDER BY publishedEpochMs DESC")
    abstract fun observeBySource(source: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE source = :source ORDER BY publishedEpochMs DESC LIMIT 1")
    abstract suspend fun latestForSource(source: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(items: List<ArticleEntity>)

    @Query(
        "UPDATE articles SET title = :title, summary = :summary, url = :url, imageUrl = :imageUrl, " +
            "publishedEpochMs = :published, category = :category WHERE id = :id AND source = :source",
    )
    abstract suspend fun updateHeadline(
        id: String,
        source: String,
        title: String,
        summary: String,
        url: String,
        imageUrl: String?,
        published: Long,
        category: String?,
    )

    /** Insert new headlines and refresh metadata of existing ones for [source]. */
    @Transaction
    open suspend fun upsertHeadlines(source: String, items: List<ArticleEntity>) {
        insertIgnore(items)
        items.forEach {
            updateHeadline(it.id, it.source, it.title, it.summary, it.url, it.imageUrl, it.publishedEpochMs, it.category)
        }
    }

    @Query("SELECT * FROM article_bodies WHERE url = :url LIMIT 1")
    abstract suspend fun getBody(url: String): ArticleBodyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBody(body: ArticleBodyEntity)

    /** Emits the last-synced time for [source], or null until it has been synced at least once. */
    @Query("SELECT lastSyncedEpochMs FROM sync_state WHERE source = :source LIMIT 1")
    abstract fun observeSyncedAt(source: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSyncState(row: SyncStateEntity)
}
