package com.example.zodiactableapp.shared.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DrawEntity::class,
        GroupInitEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        LevelOverrideEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun drawDao(): DrawDao
    abstract fun groupInitDao(): GroupInitDao
    abstract fun orderDao(): OrderDao
    abstract fun levelOverrideDao(): LevelOverrideDao

    companion object {
        private val instances = linkedMapOf<String, AppDatabase>()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS level_override (
                        dateKey TEXT NOT NULL,
                        groupId INTEGER NOT NULL,
                        level INTEGER NOT NULL,
                        PRIMARY KEY(dateKey, groupId)
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context, dbName: String): AppDatabase {
            return synchronized(this) {
                instances.getOrPut(dbName) {
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        dbName
                    )
                        .addMigrations(MIGRATION_1_2)
                        .build()
                }
            }
        }

        fun delete(context: Context, dbName: String) {
            synchronized(this) {
                instances.remove(dbName)?.close()
                context.applicationContext.deleteDatabase(dbName)
            }
        }
    }
}
