package com.getupandgetlit.dingshihai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.getupandgetlit.dingshihai.data.dao.RuntimeStateDao
import com.getupandgetlit.dingshihai.data.dao.TaskDao
import com.getupandgetlit.dingshihai.data.entity.RuntimeStateEntity
import com.getupandgetlit.dingshihai.data.entity.TaskEntity

@Database(
    entities = [TaskEntity::class, RuntimeStateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun runtimeStateDao(): RuntimeStateDao

    companion object {
        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "dingshihai.db",
            ).build()
        }
    }
}

