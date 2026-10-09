package com.l0b091.pulso.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PulsoDao {
    @Query("SELECT * FROM items WHERE descartado = 0 ORDER BY bajadoMs DESC")
    fun observarGaleria(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun item(id: String): Item?

    @Query("SELECT * FROM items WHERE carpeta = :carpeta LIMIT 1")
    suspend fun itemPorCarpeta(carpeta: String): Item?

    @Query("SELECT * FROM items")
    suspend fun todosItems(): List<Item>

    @Query("SELECT * FROM items WHERE estado = 'listo' AND descartado = 0 AND mediaPath IS NOT NULL ORDER BY bajadoMs ASC")
    suspend fun itemsListos(): List<Item>

    @Query("SELECT COUNT(*) FROM items WHERE tipo = 'flyer' AND descartado = 0 AND bajadoMs >= :desde")
    suspend fun flyersBajadosDesde(desde: Long): Int

    @Upsert
    suspend fun guardarItem(item: Item)

    @Delete
    suspend fun borrarItem(item: Item)

    @Query("SELECT * FROM destinos ORDER BY red, nombre")
    fun observarDestinos(): Flow<List<Destino>>

    @Query("SELECT * FROM destinos ORDER BY red, nombre")
    suspend fun destinos(): List<Destino>

    @Query("SELECT * FROM destinos WHERE id = :id")
    suspend fun destino(id: Long): Destino?

    @Insert
    suspend fun insertarDestino(destino: Destino): Long

    @Delete
    suspend fun borrarDestino(destino: Destino)

    @Query("SELECT * FROM envios WHERE dia = :dia ORDER BY horaMin")
    suspend fun enviosDelDia(dia: String): List<Envio>

    @Query("SELECT * FROM envios WHERE id = :id")
    suspend fun envio(id: Long): Envio?

    @Query("SELECT * FROM envios WHERE estado IN ('pendiente', 'listo')")
    suspend fun enviosActivos(): List<Envio>

    @Insert
    suspend fun insertarEnvio(envio: Envio): Long

    @Update
    suspend fun actualizarEnvio(envio: Envio)

    @Delete
    suspend fun borrarEnvio(envio: Envio)
}
