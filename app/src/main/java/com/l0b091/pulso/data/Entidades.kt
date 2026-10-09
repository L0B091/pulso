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

/**
 * Un posteo agendado para un día. El mismo contenido se manda a Instagram y a Facebook,
 * con un estado por red. [estado] resume el posteo entero.
 */
@Entity(tableName = "envios")
data class Envio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dia: String,
    val orden: Int,
    val horaMin: Int,
    val destinoIgId: Long,
    val destinoFbId: Long,
    val itemId: String?,
    val estado: String,
    val estadoIg: String = Estados.PENDIENTE,
    val estadoFb: String = Estados.PENDIENTE,
    val intento: Int = 0,
    val enviadoIgMs: Long? = null,
    val enviadoFbMs: Long? = null,
) {
    fun horaTexto(): String = "%02d:%02d".format(horaMin / 60, horaMin % 60)

    fun estadoDe(red: String): String = if (red == Redes.INSTAGRAM) estadoIg else estadoFb

    fun destinoDe(red: String): Long = if (red == Redes.INSTAGRAM) destinoIgId else destinoFbId

    fun conEstado(red: String, nuevo: String, ms: Long? = null): Envio =
        if (red == Redes.INSTAGRAM) copy(estadoIg = nuevo, enviadoIgMs = ms) else copy(estadoFb = nuevo, enviadoFbMs = ms)

    /** Redes que todavía no se mandaron ni se saltaron. */
    fun redesAbiertas(): List<String> = REDES.filter { estadoDe(it) == Estados.PENDIENTE || estadoDe(it) == Estados.LISTO }

    fun algunaEnviada(): Boolean = REDES.any { estadoDe(it) == Estados.ENVIADO }

    fun ultimoEnvioMs(): Long = maxOf(enviadoIgMs ?: 0, enviadoFbMs ?: 0)

    /** Estado general: enviado si salió en las dos; saltado si cerró sin salir en alguna. */
    fun recalcular(): Envio {
        val abiertas = redesAbiertas()
        val nuevo = when {
            abiertas.isNotEmpty() -> if (estado == Estados.PENDIENTE && REDES.all { estadoDe(it) == Estados.PENDIENTE }) Estados.PENDIENTE else Estados.LISTO
            REDES.all { estadoDe(it) == Estados.ENVIADO } -> Estados.ENVIADO
            else -> Estados.SALTADO
        }
        return copy(estado = nuevo)
    }

    companion object {
        val REDES = listOf(Redes.INSTAGRAM, Redes.FACEBOOK)
    }
}
