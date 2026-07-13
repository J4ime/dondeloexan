package com.dondeloexan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dondeloexan.data.local.dao.BlacklistDao
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.SearchHistoryDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.entity.BlacklistedEntity
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
        UserPlatformEntity::class,
        BlacklistedEntity::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao
    abstract fun tvShowDao(): TvShowDao
    abstract fun tvShowProgressDao(): TvShowProgressDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun userPlatformDao(): UserPlatformDao
    abstract fun blacklistDao(): BlacklistDao

    companion object {
        private const val DB_NAME = "dondeloexan.db"

        private val PLATFORMS = listOf(
            "Netflix", "Prime Video", "Disney+", "HBO Max",
            "Movistar+", "Apple TV+", "Paramount+", "SkyShowtime",
            "Filmin", "Atresplayer", "Mitele", "RTVE Play", "Cine"
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

        private val MIGRATION_7_8 = Migration(7, 8) { db ->
            db.execSQL(
                "INSERT OR REPLACE INTO user_platforms (platform_name, is_active) VALUES ('Cine', 1)"
            )
        }

        private val MIGRATION_8_9 = Migration(8, 9) { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS blacklist (content_id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, type TEXT NOT NULL, added_at INTEGER NOT NULL)"
            )
        }

        private val MIGRATION_10_11 = Migration(10, 11) { db ->
            db.execSQL("ALTER TABLE tv_shows ADD COLUMN imdb_id TEXT")
            db.execSQL("ALTER TABLE movies ADD COLUMN imdb_id TEXT")
        }

        private val MIGRATION_11_12 = Migration(11, 12) { db ->
            db.execSQL("ALTER TABLE tv_shows ADD COLUMN finished_at INTEGER")
        }

        private val MIGRATION_9_10 = Migration(9, 10) { db ->
            db.execSQL("""
                CREATE TABLE movies_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    content_id TEXT,
                    tmdb_id INTEGER,
                    title TEXT NOT NULL,
                    year INTEGER,
                    release_date TEXT,
                    poster_url TEXT,
                    rating_tmdb REAL,
                    rating_imdb REAL,
                    status TEXT NOT NULL DEFAULT 'POR_VER',
                    liked INTEGER NOT NULL DEFAULT 0,
                    streaming_platforms TEXT,
                    watched_at INTEGER,
                    added_at INTEGER NOT NULL
                )
            """)
            db.execSQL("""
                INSERT INTO movies_new (id, content_id, tmdb_id, title, year, release_date, poster_url, rating_tmdb, rating_imdb, status, liked, streaming_platforms, watched_at, added_at)
                SELECT id, content_id, tmdb_id, title, year, release_date, poster_url, rating_tmdb, rating_imdb, status, liked, streaming_platforms, watched_at, added_at FROM movies
            """)
            db.execSQL("DROP TABLE movies")
            db.execSQL("ALTER TABLE movies_new RENAME TO movies")

            db.execSQL("""
                CREATE TABLE tv_shows_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    content_id TEXT,
                    tmdb_id INTEGER,
                    title TEXT NOT NULL,
                    year INTEGER,
                    poster_url TEXT,
                    rating_tmdb REAL,
                    rating_imdb REAL,
                    status TEXT NOT NULL DEFAULT 'POR_VER',
                    liked INTEGER NOT NULL DEFAULT 0,
                    total_episodes INTEGER,
                    streaming_platforms TEXT,
                    added_at INTEGER NOT NULL,
                    next_episode_air_date TEXT,
                    next_episode_number INTEGER,
                    next_episode_season INTEGER,
                    series_status TEXT,
                    in_production INTEGER,
                    num_seasons INTEGER,
                    last_watched_at INTEGER
                )
            """)
            db.execSQL("""
                INSERT INTO tv_shows_new (id, content_id, tmdb_id, title, year, poster_url, rating_tmdb, rating_imdb, status, liked, total_episodes, streaming_platforms, added_at, next_episode_air_date, next_episode_number, next_episode_season, series_status, in_production, num_seasons, last_watched_at)
                SELECT id, content_id, tmdb_id, title, year, poster_url, rating_tmdb, rating_imdb, status, liked, total_episodes, streaming_platforms, added_at, next_episode_air_date, next_episode_number, next_episode_season, series_status, in_production, num_seasons, last_watched_at FROM tv_shows
            """)
            db.execSQL("DROP TABLE tv_shows")
            db.execSQL("ALTER TABLE tv_shows_new RENAME TO tv_shows")
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigration()
                .addCallback(seedCallback)
                .build()
        }
    }
}
