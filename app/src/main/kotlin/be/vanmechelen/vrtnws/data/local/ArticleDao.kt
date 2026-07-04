package be.vanmechelen.vrtnws.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import be.vanmechelen.vrtnws.model.ContentBlock
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ArticleDao {

    @Query("SELECT * FROM articles ORDER BY publishedEpochMs DESC")
    abstract fun observeAll(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE url = :url LIMIT 1")
    abstract suspend fun getByUrl(url: String): ArticleEntity?

    @Query("SELECT * FROM articles ORDER BY publishedEpochMs DESC LIMIT 1")
    abstract suspend fun latest(): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(items: List<ArticleEntity>)

    @Query(
        "UPDATE articles SET title = :title, summary = :summary, url = :url, " +
            "imageUrl = :imageUrl, publishedEpochMs = :published, category = :category WHERE id = :id",
    )
    abstract suspend fun updateHeadline(
        id: String,
        title: String,
        summary: String,
        url: String,
        imageUrl: String?,
        published: Long,
        category: String?,
    )

    @Query("UPDATE articles SET body = :body, bodyFetchedEpochMs = :ts WHERE id = :id")
    abstract suspend fun updateBody(id: String, body: List<ContentBlock>?, ts: Long)

    /** Insert new headlines and refresh metadata of existing ones without clobbering cached bodies. */
    @Transaction
    open suspend fun upsertHeadlines(items: List<ArticleEntity>) {
        insertIgnore(items)
        items.forEach {
            updateHeadline(it.id, it.title, it.summary, it.url, it.imageUrl, it.publishedEpochMs, it.category)
        }
    }
}
