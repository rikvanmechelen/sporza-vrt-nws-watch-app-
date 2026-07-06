package me.vanmechelen.vrtsporza.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [ArticleEntity::class, ArticleBodyEntity::class], version = 2, exportSchema = false)
@TypeConverters(BlockConverters::class)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    companion object {
        fun build(context: Context): NewsDatabase =
            Room.databaseBuilder(context.applicationContext, NewsDatabase::class.java, "vrtnws.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
