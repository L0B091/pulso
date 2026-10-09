package com.l0b091.pulso.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Token, enlaces y aviso de rehacer, guardados cifrados con EncryptedSharedPreferences.
 * El estado no sensible (último día abierto, último sync) va en preferencias comunes.
 */
class Ajustes private constructor(context: Context) {
    private val seguro: SharedPreferences = crear(context)
    private val comun: SharedPreferences =
        context.getSharedPreferences("pulso_estado", Context.MODE_PRIVATE)

    private fun crear(context: Context): SharedPreferences = try {
        abrir(context)
    } catch (e: Exception) {
        // Si el keystore se reseteó, el archivo cifrado queda ilegible: se rehace vacío.
        context.deleteSharedPreferences(ARCHIVO)
        abrir(context)
    }

    private fun abrir(context: Context): SharedPreferences {
        val clave = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ARCHIVO,
            clave,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun s(clave: String, porDefecto: String = "") = seguro.getString(clave, porDefecto) ?: porDefecto
    private fun ps(clave: String, valor: String) = seguro.edit().putString(clave, valor.trim()).apply()

    var token: String
        get() = s("token")
        set(v) = ps("token", v)
    var repo: String
        get() = s("repo", REPO_POR_DEFECTO).ifBlank { REPO_POR_DEFECTO }
        set(v) = ps("repo", v)
    var rama: String
        get() = s("rama", "main").ifBlank { "main" }
        set(v) = ps("rama", v)

    var enlaceWhatsapp: String
        get() = s("enlace_whatsapp")
        set(v) = ps("enlace_whatsapp", v)
    var enlaceInstagram: String
        get() = s("enlace_instagram")
        set(v) = ps("enlace_instagram", v)
    var enlaceFacebook: String
        get() = s("enlace_facebook")
        set(v) = ps("enlace_facebook", v)

    var avisoUrl: String
        get() = s("aviso_url")
        set(v) = ps("aviso_url", v)
    var avisoClave: String
        get() = s("aviso_clave")
        set(v) = ps("aviso_clave", v)
    var avisoHeader: String
        get() = s("aviso_header", "Authorization").ifBlank { "Authorization" }
        set(v) = ps("aviso_header", v)

    var ultimoDiaAbierto: String
        get() = comun.getString("ultimo_dia", "") ?: ""
        set(v) = comun.edit().putString("ultimo_dia", v).apply()
    var permisosPedidos: Boolean
        get() = comun.getBoolean("permisos_pedidos", false)
        set(v) = comun.edit().putBoolean("permisos_pedidos", v).apply()
    var ultimoSync: String
        get() = comun.getString("ultimo_sync", "") ?: ""
        set(v) = comun.edit().putString("ultimo_sync", v).apply()
    var contadorPrueba: Int
        get() = comun.getInt("contador_prueba", 0)
        set(v) = comun.edit().putInt("contador_prueba", v).apply()

    companion object {
        const val ARCHIVO = "pulso_seguro"
        const val REPO_POR_DEFECTO = "L0B091/pulso"

        @Volatile
        private var instancia: Ajustes? = null

        fun get(context: Context): Ajustes = instancia ?: synchronized(this) {
            instancia ?: Ajustes(context.applicationContext).also { instancia = it }
        }
    }
}
