package ua.gardenapple.itchupdater.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.database.updatecheck.UpdateCheckResultModel
import ua.gardenapple.itchupdater.database.upload.Upload


class Migrations {
    companion object {
//        val Migration_1_2 = object : Migration(1, 2) {
//            override fun migrate(database: SupportSQLiteDatabase) {
//                database.execSQL("""
//                    CREATE TABLE games_copy(
//                        ${Game.GAME_ID} INTEGER PRIMARY KEY NOT NULL,
//                        ${Game.NAME} TEXT NOT NULL,
//                        ${Game.STORE_URL} TEXT NOT NULL,
//                        ${Game.DOWNLOAD_PAGE_URL} TEXT,
//                        ${Game.AUTHOR} TEXT NOT NULL,
//                        ${Game.LOCALE} TEXT NOT NULL,
//                        ${Game.THUMBNAIL_URL} TEXT NOT NULL,
//                        ${Game.LAST_UPDATED_TIMESTAMP} TEXT
//                    )""")
//                database.execSQL("""
//                    INSERT INTO games_copy (${Game.GAME_ID}, ${Game.NAME}, ${Game.STORE_URL}, ${Game.DOWNLOAD_PAGE_URL}, ${Game.AUTHOR}, ${Game.LOCALE}, ${Game.THUMBNAIL_URL}, ${Game.LAST_UPDATED_TIMESTAMP})
//                        SELECT ${Game.GAME_ID}, ${Game.NAME}, ${Game.STORE_URL}, ${Game.DOWNLOAD_PAGE_URL}, ${Game.AUTHOR}, ${Game.LOCALE}, ${Game.THUMBNAIL_URL}, ${Game.LAST_UPDATED_TIMESTAMP} FROM games
//                """)
//                database.execSQL("DROP TABLE games")
//                database.execSQL("ALTER TABLE games_copy RENAME TO games")
//
//
//                database.execSQL("ALTER TABLE ${Upload.TABLE_NAME} ADD COLUMN ${Upload.IS_PENDING} INTEGER NOT NULL DEFAULT 0")
//                database.execSQL("""
//                    CREATE TABLE uploads_copy(
//                        ${Upload.INTERNAL_ID} INTEGER PRIMARY KEY NOT NULL,
//                        ${Upload.UPLOAD_ID} INTEGER,
//                        ${Upload.NAME} TEXT NOT NULL,
//                        ${Upload.GAME_ID} INTEGER NOT NULL,
//                        ${Upload.LOCALE} TEXT NOT NULL,
//                        ${Upload.FILE_SIZE} TEXT NOT NULL,
//                        ${Upload.IS_PENDING} INTEGER NOT NULL,
//                        ${Upload.TIMESTAMP} TEXT,
//                        ${Upload.VERSION} TEXT,
//                        FOREIGN KEY(${Upload.GAME_ID}) REFERENCES games(${Game.GAME_ID}) ON DELETE CASCADE
//                    )""")
//                database.execSQL("""
//                    INSERT INTO uploads_copy (${Upload.INTERNAL_ID}, ${Upload.UPLOAD_ID}, ${Upload.NAME}, ${Upload.GAME_ID}, ${Upload.LOCALE}, ${Upload.FILE_SIZE}, ${Upload.IS_PENDING}, ${Upload.TIMESTAMP}, ${Upload.VERSION})
//                        SELECT internalId, ${Upload.UPLOAD_ID}, ${Upload.NAME}, ${Upload.GAME_ID}, ${Upload.LOCALE}, ${Upload.FILE_SIZE}, ${Upload.IS_PENDING}, ${Upload.TIMESTAMP}, ${Upload.VERSION} FROM uploads
//                """)
//                database.execSQL("DROP TABLE ${Upload.TABLE_NAME}")
//                database.execSQL("ALTER TABLE uploads_copy RENAME TO ${Upload.TABLE_NAME}")
//                database.execSQL("""
//                    CREATE INDEX index_uploads_upload_id
//                        ON uploads(${Upload.UPLOAD_ID})
//                """.trimIndent())
//                database.execSQL("""
//                    CREATE INDEX index_uploads_game_id
//                        ON uploads(${Upload.GAME_ID})
//                """.trimIndent())
//
//
//
//                database.execSQL("""
//                    CREATE TABLE installs_copy(
//                        ${Installation.INTERNAL_ID} INTEGER PRIMARY KEY NOT NULL,
//                        ${Installation.PACKAGE_NAME} TEXT,
//                        ${Installation.UPLOAD_ID} INTEGER NOT NULL,
//                        ${Installation.GAME_ID} INTEGER NOT NULL,
//                        ${Installation.IS_PENDING} INTEGER NOT NULL,
//                        FOREIGN KEY(${Installation.GAME_ID}) REFERENCES games(${Game.GAME_ID}) ON DELETE CASCADE,
//                        FOREIGN KEY(${Installation.UPLOAD_ID}) REFERENCES uploads(${Upload.INTERNAL_ID}) ON DELETE CASCADE
//                    )""")
//                database.execSQL("""
//                    INSERT INTO installs_copy (${Installation.INTERNAL_ID}, ${Installation.PACKAGE_NAME}, ${Installation.UPLOAD_ID}, ${Installation.GAME_ID}, ${Installation.IS_PENDING})
//                        SELECT ${Installation.UPLOAD_ID}, name, ${Installation.UPLOAD_ID}, ${Installation.GAME_ID}, NOT download_finished FROM ${Installation.TABLE_NAME}
//                """)
//                database.execSQL("DROP TABLE ${Installation.TABLE_NAME}")
//                database.execSQL("ALTER TABLE installs_copy RENAME TO ${Installation.TABLE_NAME}")
//
//                database.execSQL("""
//                    CREATE INDEX index_installations_upload_id
//                        ON ${Installation.TABLE_NAME}(${Installation.UPLOAD_ID})
//                """.trimIndent())
//                database.execSQL("""
//                    CREATE INDEX index_installations_game_id
//                        ON ${Installation.TABLE_NAME}(${Installation.GAME_ID})
//                """.trimIndent())
//            }
//        }

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
                    UPDATE ${Upload.TABLE_NAME}
                    SET
                        ${Upload.LOCALE} = 'en'
                    WHERE
                        ${Upload.LOCALE} = 'Unknown'
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
                    """.trimIndent())

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
                    CREATE TABLE ${UpdateCheckResultModel.TABLE_NAME}(
                        ${UpdateCheckResultModel.INSTALLATION_ID} INTEGER PRIMARY KEY NOT NULL,
                        ${UpdateCheckResultModel.CODE} INTEGER NOT NULL,
                        ${UpdateCheckResultModel.TIMESTAMP} TEXT,
                        ${UpdateCheckResultModel.VERSION} TEXT,
                        ${UpdateCheckResultModel.FILE_SIZE} TEXT,
                        ${UpdateCheckResultModel.UPLOAD_ID} INTEGER,
                        ${UpdateCheckResultModel.DOWNLOAD_URL} TEXT,
                        ${UpdateCheckResultModel.DOWNLOAD_IS_STORE_PAGE} INTEGER NOT NULL,
                        ${UpdateCheckResultModel.DOWNLOAD_IS_PERMANENT} INTEGER NOT NULL,
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
    }
}