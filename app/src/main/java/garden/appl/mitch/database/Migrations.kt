package garden.appl.mitch.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import garden.appl.mitch.database.updatecheck.UpdateCheckResultModel


val Migrations = arrayOf(
    object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE uploads ADD COLUMN platforms INTEGER NOT NULL DEFAULT 8")
        }
    },
    object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            //Fix unknown locales
            database.execSQL(
                """
                    UPDATE games
                    SET
                        locale = 'en'
                    WHERE
                        locale = 'Unknown'
                """
            )
            database.execSQL(
                """
                    UPDATE uploads
                    SET
                        locale = 'en'
                    WHERE
                        locale = 'Unknown'
                """
            )

            //Thumbnail is nullable
            database.execSQL(
                """
                    CREATE TABLE games_copy(
                        game_id INTEGER PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        store_url TEXT NOT NULL,
                        download_page_url TEXT,
                        author TEXT NOT NULL,
                        locale TEXT NOT NULL,
                        thumbnail_url TEXT,
                        last_timestamp TEXT
                    )
                    """
            )

            database.execSQL(
                """
                    INSERT INTO games_copy (game_id, name, store_url, download_page_url, author, locale, thumbnail_url, last_timestamp)
                        SELECT game_id, name, store_url, download_page_url, author, locale, thumbnail_url, last_timestamp FROM games
                """
            )
            database.execSQL("DROP TABLE games")
            database.execSQL("ALTER TABLE games_copy RENAME TO games")
        }
    },
    object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
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
                        FOREIGN KEY(install_id) REFERENCES 
                            installations(internal_id) ON DELETE CASCADE
                    )
                    """
            )

            database.execSQL(
                """
                    CREATE INDEX index_${UpdateCheckResultModel.TABLE_NAME}_${UpdateCheckResultModel.INSTALLATION_ID}
                        ON ${UpdateCheckResultModel.TABLE_NAME}(${UpdateCheckResultModel.INSTALLATION_ID})
                """
            )
        }
    },
    object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE installations ADD COLUMN locale TEXT NOT NULL DEFAULT 'Unknown'")
            database.execSQL("ALTER TABLE installations ADD COLUMN version TEXT")
            database.execSQL("ALTER TABLE installations ADD COLUMN name TEXT NOT NULL DEFAULT ' '")
            database.execSQL(" ALTER TABLE installations ADD COLUMN file_size TEXT NOT NULL DEFAULT '-'")
            database.execSQL(" ALTER TABLE installations ADD COLUMN timestamp TEXT")
            database.execSQL(" ALTER TABLE installations ADD COLUMN platforms INTEGER NOT NULL DEFAULT 0")
        }
    },
    object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE uploads")
        }
    },
    object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE installations ADD COLUMN external_file_name TEXT DEFAULT NULL")
        }
    },
    object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE installations ADD COLUMN available_uploads TEXT DEFAULT NULL")
        }
    },
    object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE update_check_results ADD COLUMN is_installing INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE update_check_results ADD COLUMN upload_name TEXT DEFAULT NULL")
        }
    },
    object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE update_check_results ADD COLUMN error_message TEXT DEFAULT NULL")
        }
    },
    object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE games ADD COLUMN web_entry_point TEXT DEFAULT NULL")
            database.execSQL("ALTER TABLE games ADD COLUMN web_cached INTEGER NOT NULL DEFAULT 0")
        }
    }
)