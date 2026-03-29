package com.getupandgetlit.dingshihai

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.getupandgetlit.dingshihai.data.db.AppDatabase
import com.getupandgetlit.dingshihai.domain.model.TaskStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `migration 1 to 2 defaults max playback minutes to zero`() {
        val dbName = "migration-test.db"
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val db = helper.writableDatabase
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT,
                startHour INTEGER NOT NULL,
                startMinute INTEGER NOT NULL,
                fileUri TEXT NOT NULL,
                fileName TEXT NOT NULL,
                playMode TEXT NOT NULL,
                loopCount INTEGER,
                intervalMinSec INTEGER,
                intervalMaxSec INTEGER,
                status TEXT NOT NULL,
                scheduledAtEpochMs INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO tasks (
                id, name, startHour, startMinute, fileUri, fileName, playMode,
                loopCount, intervalMinSec, intervalMaxSec, status, scheduledAtEpochMs,
                createdAt, updatedAt
            ) VALUES (
                1, 'demo', 8, 30, 'content://demo/file.mp3', 'demo.mp3', 'single',
                NULL, NULL, NULL, '${TaskStatus.UNTRIGGERED.value}', NULL, 1000, 1000
            )
            """.trimIndent()
        )

        AppDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT maxPlaybackMinutes FROM tasks WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }

        db.close()
        helper.close()
        dbFile.delete()
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()
    }

    @Test
    fun `migration 2 to 3 defaults force bluetooth playback to true`() {
        val dbName = "migration-test-v3.db"
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val db = helper.writableDatabase
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT,
                startHour INTEGER NOT NULL,
                startMinute INTEGER NOT NULL,
                fileUri TEXT NOT NULL,
                fileName TEXT NOT NULL,
                playMode TEXT NOT NULL,
                loopCount INTEGER,
                intervalMinSec INTEGER,
                intervalMaxSec INTEGER,
                maxPlaybackMinutes INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL,
                scheduledAtEpochMs INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO tasks (
                id, name, startHour, startMinute, fileUri, fileName, playMode,
                loopCount, intervalMinSec, intervalMaxSec, maxPlaybackMinutes, status,
                scheduledAtEpochMs, createdAt, updatedAt
            ) VALUES (
                1, 'demo', 8, 30, 'content://demo/file.mp3', 'demo.mp3', 'single',
                NULL, NULL, NULL, 0, '${TaskStatus.UNTRIGGERED.value}', NULL, 1000, 1000
            )
            """.trimIndent()
        )

        AppDatabase.MIGRATION_2_3.migrate(db)

        db.query("SELECT forceBluetoothPlayback FROM tasks WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }

        db.close()
        helper.close()
        dbFile.delete()
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()
    }
    @Test
    fun `migration 3 to 4 defaults selected for reserve to true`() {
        val dbName = "migration-test-v4.db"
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val db = helper.writableDatabase
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT,
                startHour INTEGER NOT NULL,
                startMinute INTEGER NOT NULL,
                fileUri TEXT NOT NULL,
                fileName TEXT NOT NULL,
                playMode TEXT NOT NULL,
                loopCount INTEGER,
                intervalMinSec INTEGER,
                intervalMaxSec INTEGER,
                maxPlaybackMinutes INTEGER NOT NULL DEFAULT 0,
                forceBluetoothPlayback INTEGER NOT NULL DEFAULT 1,
                status TEXT NOT NULL,
                scheduledAtEpochMs INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO tasks (
                id, name, startHour, startMinute, fileUri, fileName, playMode,
                loopCount, intervalMinSec, intervalMaxSec, maxPlaybackMinutes, forceBluetoothPlayback,
                status, scheduledAtEpochMs, createdAt, updatedAt
            ) VALUES (
                1, 'demo', 8, 30, 'content://demo/file.mp3', 'demo.mp3', 'single',
                NULL, NULL, NULL, 0, 1, '${TaskStatus.UNTRIGGERED.value}', NULL, 1000, 1000
            )
            """.trimIndent()
        )

        AppDatabase.MIGRATION_3_4.migrate(db)

        db.query("SELECT selectedForReserve FROM tasks WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }

        db.close()
        helper.close()
        dbFile.delete()
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()
    }
}
