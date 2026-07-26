package neth.iecal.curbox.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReelStatsEntity::class, ScrollPatternEntity::class, FocusStatsEntity::class, WebsiteStatsEntity::class, IntentLogEntity::class, AppUsageEntity::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reelStatsDao(): ReelStatsDao
    abstract fun scrollPatternDao(): ScrollPatternDao
    abstract fun focusStatsDao(): FocusStatsDao
    abstract fun websiteStatsDao(): WebsiteStatsDao
    abstract fun intentLogDao(): IntentLogDao
    abstract fun appUsageDao(): AppUsageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE website_stats ADD COLUMN hourlyUsage BLOB NOT NULL DEFAULT X''"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "curbox_db"
                ).addMigrations(MIGRATION_8_9)
                    .enableMultiInstanceInvalidation()
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
