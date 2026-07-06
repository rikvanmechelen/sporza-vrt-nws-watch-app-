package me.vanmechelen.vrtsporza.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import me.vanmechelen.vrtsporza.model.BlockType
import me.vanmechelen.vrtsporza.model.ContentBlock

/** One headline row per (article, source) — feeds overlap, so both are needed as the key. */
@Entity(tableName = "articles", primaryKeys = ["id", "source"])
data class ArticleEntity(
    val id: String,
    val source: String,
    val title: String,
    val summary: String,
    val url: String,
    val imageUrl: String?,
    val publishedEpochMs: Long,
    val category: String?,
)

/** Extracted article body, cached once per url (source-independent). */
@Entity(tableName = "article_bodies")
data class ArticleBodyEntity(
    @PrimaryKey val url: String,
    val body: List<ContentBlock>,
    val fetchedEpochMs: Long,
)

/** Serialises [ContentBlock] lists to a single delimited string for Room. */
class BlockConverters {
    @TypeConverter
    fun fromBlocks(blocks: List<ContentBlock>?): String? =
        blocks?.joinToString(RECORD_SEP) { "${it.type.name}$UNIT_SEP${it.text}" }

    @TypeConverter
    fun toBlocks(value: String?): List<ContentBlock>? =
        value?.split(RECORD_SEP)?.mapNotNull { record ->
            val i = record.indexOf(UNIT_SEP)
            if (i < 0) return@mapNotNull null
            val type = runCatching { BlockType.valueOf(record.substring(0, i)) }.getOrNull()
                ?: return@mapNotNull null
            ContentBlock(type, record.substring(i + 1))
        }

    private companion object {
        const val RECORD_SEP = "\u001E" // record separator between blocks
        const val UNIT_SEP = "\u001F"   // unit separator between type and text
    }
}
