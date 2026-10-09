package com.l0b091.pulso.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Item::class, Destino::class, Envio::class], version = 1, exportSchema = false)
abstract class PulsoDb : RoomDatabase() {
    abstract fun dao(): PulsoDao

    companion object {
        @Volatile
        private var instancia: PulsoDb? = null

        fun get(context: Context): PulsoDb = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, PulsoDb::class.java, "pulso.db")
                .build().also { instancia = it }
        }
    }
}
