package com.dondeloexan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.SearchHistoryDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.entity.Converters
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.SearchHistoryEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.UserPlatformEntity

@Database(
    entities = [
        MovieEntity::class,
        TvShowEntity::class,
        TvShowProgressEntity::class,
        SearchHistoryEntity::class,
        UserPlatformEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao
    abstract fun tvShowDao(): TvShowDao
    abstract fun tvShowProgressDao(): TvShowProgressDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun userPlatformDao(): UserPlatformDao

    companion object {
        private const val DB_NAME = "dondeloexan.db"

        private val PLATFORMS = listOf(
            "Netflix", "Prime Video", "Disney+", "HBO Max",
            "Movistar+", "Apple TV+", "Paramount+", "SkyShowtime",
            "Filmin", "Atresplayer", "Mitele", "RTVE Play"
        )

        private val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                for (platform in PLATFORMS) {
                    db.execSQL(
                        "INSERT OR REPLACE INTO user_platforms (platform_name, is_active) VALUES (?, 1)",
                        arrayOf(platform)
                    )
                }
            }
        }

        private val MIGRATION_4_5 = Migration(4, 5) { db ->
            db.execSQL("ALTER TABLE tv_shows ADD COLUMN last_watched_at INTEGER")
        }

        private val MIGRATION_5_6 = Migration(5, 6) { db ->
            db.execSQL("ALTER TABLE movies ADD COLUMN release_date TEXT")
            db.execSQL("ALTER TABLE movies ADD COLUMN watched_at INTEGER")
        }

        private val MIGRATION_6_7 = Migration(6, 7) { db ->
            db.execSQL("ALTER TABLE tv_shows ADD COLUMN in_production INTEGER")
            db.execSQL("ALTER TABLE tv_shows ADD COLUMN num_seasons INTEGER")
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .addCallback(seedCallback)
                .build()
        }
    }
}
