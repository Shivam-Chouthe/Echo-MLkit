package com.example.signal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MemoryEntity::class],
    version = 4,
    exportSchema = false
)
abstract class SignalDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao

    companion object {

        @Volatile
        private var INSTANCE: SignalDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memories ADD COLUMN reminderDate TEXT")
                db.execSQL("ALTER TABLE memories ADD COLUMN reminderAt INTEGER")
                db.execSQL("ALTER TABLE memories ADD COLUMN detectedUrl TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memories ADD COLUMN reminderTime TEXT")
            }
        }

        fun getDatabase(context: Context): SignalDatabase {
            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SignalDatabase::class.java,
                    "signal_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}
