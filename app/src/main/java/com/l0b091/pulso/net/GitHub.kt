package com.l0b091.pulso.net

import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Carpeta(val nombre: String, val sha: String)

/** Cliente mínimo de la API REST de contenidos de GitHub. Solo lectura. */
class GitHub(private val token: String, private val repo: String, private val rama: String) {

    private fun abrir(ruta: String, accept: String): HttpURLConnection {
        val partes = ruta.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val url = URL("https://api.github.com/repos/$repo/contents/$partes?ref=" + URLEncoder.encode(rama, "UTF-8"))
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", accept)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "pulso-android")
        }
    }

    private fun verificar(c: HttpURLConnection, ruta: String) {
        val code = c.responseCode
        if (code in 200..299) return
        val msg = when (code) {
            401 -> "GitHub rechazó el token (401). Revisalo en Ajustes."
            403 -> "GitHub negó el acceso (403). El token necesita permiso de lectura de contenido."
            404 -> "No encontré $ruta en $repo (404)."
            else -> "GitHub respondió $code en $ruta."
        }
        c.disconnect()
        throw IOException(msg)
    }

    /** Lista las subcarpetas de una ruta. Si la ruta no existe, devuelve vacío. */
    fun listarCarpetas(ruta: String): List<Carpeta> {
        val c = abrir(ruta, "application/vnd.github+json")
        if (c.responseCode == 404) { c.disconnect(); return emptyList() }
        verificar(c, ruta)
        val cuerpo = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val arr = JSONArray(cuerpo)
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("type") == "dir" }
            .map { Carpeta(it.getString("name"), it.optString("sha")) }
    }

    fun bajarTexto(ruta: String): String {
        val c = abrir(ruta, "application/vnd.github.raw+json")
        verificar(c, ruta)
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    fun bajarArchivo(ruta: String, destino: File) {
        val c = abrir(ruta, "application/vnd.github.raw+json")
        verificar(c, ruta)
        val tmp = File(destino.parentFile, destino.name + ".tmp")
        c.inputStream.use { entrada -> tmp.outputStream().use { entrada.copyTo(it) } }
        c.disconnect()
        if (destino.exists()) destino.delete()
        if (!tmp.renameTo(destino)) throw IOException("No pude guardar ${destino.name}.")
    }
}
