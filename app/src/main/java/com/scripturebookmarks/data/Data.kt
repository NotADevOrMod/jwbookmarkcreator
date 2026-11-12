package com.scripturebookmarks.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.scripturebookmarks.bible.BibleBooks
import com.scripturebookmarks.bible.Reference
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: Int,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int?,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

// Ready for the share-target step: articles attached to a bookmark.
@Entity(
    tableName = "articles",
    foreignKeys = [ForeignKey(
        entity = BookmarkEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookmarkId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("bookmarkId")],
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookmarkId: Long,
    val title: String,
    val url: String,
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun all(): Flow<List<BookmarkEntity>>

    @Insert suspend fun insert(b: BookmarkEntity): Long
    @Update suspend fun update(b: BookmarkEntity)
    @Delete suspend fun delete(b: BookmarkEntity)

    @Query("SELECT * FROM articles WHERE bookmarkId = :id")
    fun articlesFor(id: Long): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles")
    fun allArticles(): Flow<List<ArticleEntity>>

    @Insert suspend fun insertArticle(a: ArticleEntity): Long
    @Delete suspend fun deleteArticle(a: ArticleEntity)
}

@Database(entities = [BookmarkEntity::class, ArticleEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarks(): BookmarkDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "bookmarks.db"
            ).build().also { instance = it }
        }
    }
}

fun BookmarkEntity.toReference() = Reference(BibleBooks.all[book - 1], chapter, verseStart, verseEnd)
