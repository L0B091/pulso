package com.l0b091.pulso.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Item::class, Destino::class, Envio::class], version = 2, exportSchema = false)
abstract class PulsoDb : RoomDatabase() {
    abstract fun dao(): PulsoDao

    companion object {
        @Volatile
        private var instancia: PulsoDb? = null

        /**
         * 0.1 → 0.2: cada posteo va a Instagram y a Facebook. Se conservan flyers, clips y
         * destinos; los posteos agendados con la versión anterior se borran (hay que agendar de nuevo).
         */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `envios`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `envios` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dia` TEXT NOT NULL, `orden` INTEGER NOT NULL, `horaMin` INTEGER NOT NULL, " +
                        "`destinoIgId` INTEGER NOT NULL, `destinoFbId` INTEGER NOT NULL, `itemId` TEXT, " +
                        "`estado` TEXT NOT NULL, `estadoIg` TEXT NOT NULL, `estadoFb` TEXT NOT NULL, " +
                        "`intento` INTEGER NOT NULL, `enviadoIgMs` INTEGER, `enviadoFbMs` INTEGER)"
                )
            }
        }

        fun get(context: Context): PulsoDb = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, PulsoDb::class.java, "pulso.db")
                .addMigrations(MIGRACION_1_2)
                .build().also { instancia = it }
        }
    }
}
