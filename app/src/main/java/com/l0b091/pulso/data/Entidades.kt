package com.l0b091.pulso.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object Estados {
    const val PENDIENTE = "pendiente"
    const val LISTO = "listo"
    const val ENVIADO = "enviado"
    const val SALTADO = "saltado"
}

object Tipos {
    const val FLYER = "flyer"
    const val CLIP = "clip"
}

object Redes {
    const val INSTAGRAM = "instagram"
    const val FACEBOOK = "facebook"

    fun nombre(red: String?): String = when (red) {
        INSTAGRAM -> "Instagram"
        FACEBOOK -> "Facebook"
        else -> "Sin red"
    }
}

/** Flyer bajado del repo o clip cargado desde el teléfono. */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey val id: String,
    val tipo: String,
    val estado: String,
    val texto: String,
    val angulo: String?,
    val redSugerida: String?,
    val mediaPath: String?,
    val thumbPath: String?,
    val creadoEn: String,
    val bajadoMs: Long,
    val carpeta: String? = null,
    val remoteSha: String? = null,
    val descartado: Boolean = false,
    val rehaciendo: Boolean = false,
)

/** Perfil o grupo donde se publica. */
@Entity(tableName = "destinos")
data class Destino(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val red: String,
    val tipo: String, // perfil | grupo
    val nombre: String,
    val url: String,
)

/** Un posteo agendado para un día. */
@Entity(tableName = "envios")
data class Envio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dia: String,
    val orden: Int,
    val horaMin: Int,
    val destinoId: Long,
    val itemId: String?,
    val estado: String,
    val intento: Int = 0,
    val enviadoMs: Long? = null,
) {
    fun horaTexto(): String = "%02d:%02d".format(horaMin / 60, horaMin % 60)
}
