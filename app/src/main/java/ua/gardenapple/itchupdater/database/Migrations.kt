package ua.gardenapple.itchupdater.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ua.gardenapple.itchupdater.database.game.Game


class Migrations {
    companion object {
        val Migration_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE ${Game.TABLE_NAME} ADD COLUMN ${Game.LAST_DOWNLOAD_TIMESTAMP} TEXT")
            }

        }
    }
}