package ua.gardenapple.itchupdater.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation


class Migrations {
    companion object {
//        val Migration_1_2 = object : Migration(1, 2) {
//            override fun migrate(database: SupportSQLiteDatabase) {
//                database.execSQL("ALTER TABLE ${Game.TABLE_NAME} ADD COLUMN ${Game.LAST_SEEN_TIMESTAMP} TEXT")
//            }
//        }
//
//        val Migration_2_3 = object : Migration(2, 3) {
//            override fun migrate(database: SupportSQLiteDatabase) {
//                database.execSQL("ALTER TABLE ${Game.TABLE_NAME} ADD COLUMN ${Game.STORE_LAST_VISITED} INTEGER DEFAULT NULL")
//                database.execSQL(
//                    """CREATE TABLE ${Installation.TABLE_NAME} (
//                        ${Installation.UPLOAD_ID_INTERNAL} INTEGER PRIMARY KEY NOT NULL,
//                        ${Installation.PACKAGE_NAME} TEXT,
//                        ${Installation.TIMESTAMP} TEXT
//                    )""")
//            }
//        }
    }
}