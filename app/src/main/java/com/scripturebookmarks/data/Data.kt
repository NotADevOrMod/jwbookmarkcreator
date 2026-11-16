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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val tags: String = "", // comma separated
)

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

    @Query("SELECT * FROM articles")
    fun allArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM bookmarks")
    suspend fun allOnce(): List<BookmarkEntity>

    @Query("SELECT * FROM articles")
    suspend fun allArticlesOnce(): List<ArticleEntity>

    @Query("SELECT url FROM articles WHERE bookmarkId = :id")
    suspend fun articleUrls(id: Long): List<String>

    @Query(
        "SELECT * FROM bookmarks WHERE book = :book AND chapter = :chapter AND verseStart = :vs " +
            "AND ((verseEnd IS NULL AND :ve IS NULL) OR verseEnd = :ve) LIMIT 1"
    )
    suspend fun find(book: Int, chapter: Int, vs: Int, ve: Int?): BookmarkEntity?

    @Insert suspend fun insert(b: BookmarkEntity): Long
    @Update suspend fun update(b: BookmarkEntity)
    @Delete suspend fun delete(b: BookmarkEntity)
    @Insert suspend fun insertArticle(a: ArticleEntity): Long
    @Delete suspend fun deleteArticle(a: ArticleEntity)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [BookmarkEntity::class, ArticleEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarks(): BookmarkDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "bookmarks.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

fun BookmarkEntity.toReference() = Reference(BibleBooks.all[book - 1], chapter, verseStart, verseEnd)

fun parseTags(s: String): List<String> =
    s.split(',', ';').map { it.trim().trimStart('#').trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
