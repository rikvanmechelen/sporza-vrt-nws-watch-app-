package be.vanmechelen.vrtnws.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import be.vanmechelen.vrtnws.model.BlockType
import be.vanmechelen.vrtnws.model.ContentBlock

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val url: String,
    val imageUrl: String?,
    val publishedEpochMs: Long,
    val category: String?,
    /** Extracted body, null until the article has been opened at least once. */
    val body: List<ContentBlock>?,
    val bodyFetchedEpochMs: Long?,
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
