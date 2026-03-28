package com.getupandgetlit.dingshihai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.getupandgetlit.dingshihai.data.dao.RuntimeStateDao
import com.getupandgetlit.dingshihai.data.dao.TaskDao
import com.getupandgetlit.dingshihai.data.entity.RuntimeStateEntity
import com.getupandgetlit.dingshihai.data.entity.TaskEntity

@Database(
    entities = [TaskEntity::class, RuntimeStateEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun runtimeStateDao(): RuntimeStateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE tasks ADD COLUMN maxPlaybackMinutes INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "dingshihai.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
