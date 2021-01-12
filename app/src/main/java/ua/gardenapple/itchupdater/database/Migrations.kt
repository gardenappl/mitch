package ua.gardenapple.itchupdater.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultModel


class Migrations {
    companion object {
        val Migration_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE uploads ADD COLUMN platforms INTEGER NOT NULL DEFAULT 8")
            }
        }

        val Migration_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                //Fix unknown locales
                database.execSQL("""
                    UPDATE ${Game.TABLE_NAME}
                    SET
                        ${Game.LOCALE} = 'en'
                    WHERE
                        ${Game.LOCALE} = 'Unknown'
                """)
                database.execSQL("""
                    UPDATE uploads
                    SET
                        locale = 'en'
                    WHERE
                        locale = 'Unknown'
                """)

                //Thumbnail is nullable
                database.execSQL("""
                    CREATE TABLE games_copy(
                        ${Game.GAME_ID} INTEGER PRIMARY KEY NOT NULL,
                        ${Game.NAME} TEXT NOT NULL,
                        ${Game.STORE_URL} TEXT NOT NULL,
                        ${Game.DOWNLOAD_PAGE_URL} TEXT,
                        ${Game.AUTHOR} TEXT NOT NULL,
                        ${Game.LOCALE} TEXT NOT NULL,
                        ${Game.THUMBNAIL_URL} TEXT,
                        ${Game.LAST_UPDATED_TIMESTAMP} TEXT
                    )
                    """)

                database.execSQL("""
                    INSERT INTO games_copy (${Game.GAME_ID}, ${Game.NAME}, ${Game.STORE_URL}, ${Game.DOWNLOAD_PAGE_URL}, ${Game.AUTHOR}, ${Game.LOCALE}, ${Game.THUMBNAIL_URL}, ${Game.LAST_UPDATED_TIMESTAMP})
                        SELECT ${Game.GAME_ID}, ${Game.NAME}, ${Game.STORE_URL}, ${Game.DOWNLOAD_PAGE_URL}, ${Game.AUTHOR}, ${Game.LOCALE}, ${Game.THUMBNAIL_URL}, ${Game.LAST_UPDATED_TIMESTAMP} FROM games
                """)
                database.execSQL("DROP TABLE games")
                database.execSQL("ALTER TABLE games_copy RENAME TO games")
            }
        }
        
        val Migration_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE update_check_results(
                        install_id INTEGER PRIMARY KEY NOT NULL,
                        code INTEGER NOT NULL,
                        timestamp TEXT,
                        version TEXT,
                        file_size TEXT,
                        upload_id INTEGER,
                        download_url TEXT,
                        download_is_store_page INTEGER NOT NULL,
                        download_is_permanent INTEGER NOT NULL,
                        FOREIGN KEY(${UpdateCheckResultModel.INSTALLATION_ID}) REFERENCES 
                            ${Installation.TABLE_NAME}(${Installation.INTERNAL_ID}) ON DELETE CASCADE
                    )
                    """)

                database.execSQL("""
                    CREATE INDEX index_${UpdateCheckResultModel.TABLE_NAME}_${UpdateCheckResultModel.INSTALLATION_ID}
                        ON ${UpdateCheckResultModel.TABLE_NAME}(${UpdateCheckResultModel.INSTALLATION_ID})
                """)
            }
        }
        
        val Migration_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE installations ADD COLUMN locale TEXT NOT NULL DEFAULT 'Unknown'")
                database.execSQL("ALTER TABLE installations ADD COLUMN version TEXT")
                database.execSQL("ALTER TABLE installations ADD COLUMN name TEXT NOT NULL DEFAULT ' '")
                database.execSQL(" ALTER TABLE installations ADD COLUMN file_size TEXT NOT NULL DEFAULT '-'")
                database.execSQL(" ALTER TABLE installations ADD COLUMN timestamp TEXT")
                database.execSQL(" ALTER TABLE installations ADD COLUMN platforms INTEGER NOT NULL DEFAULT 0")
            }
        }

        val Migration_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE uploads")
            }
        }
        
        val Migration_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE installations ADD COLUMN external_file_name TEXT DEFAULT NULL")
            }
        }
    }
}