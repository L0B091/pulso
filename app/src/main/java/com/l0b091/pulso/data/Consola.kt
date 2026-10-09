package com.l0b091.pulso.data

import android.content.Context
import android.net.Uri
import com.l0b091.pulso.alarm.Alarmas
import com.l0b091.pulso.alarm.Avisos
import com.l0b091.pulso.net.GitHub
import com.l0b091.pulso.util.Medios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Lógica de la consola: sync, galería, orquestador y envíos. La app nunca publica sola. */
class Consola private constructor(private val context: Context) {
    val dao = PulsoDb.get(context).dao()
    val ajustes = Ajustes.get(context)
    private val candado = Mutex()

    fun hoy(): String = LocalDate.now().toString()

    private fun inicioDeHoyMs(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun msDeHora(dia: String, horaMin: Int): Long =
        LocalDateTime.of(LocalDate.parse(dia), LocalTime.of(horaMin / 60, horaMin % 60))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ---------- Enlaces y texto ----------

    fun enlaces(): LinkedHashMap<String, String> = linkedMapOf(
        "WhatsApp" to ajustes.enlaceWhatsapp,
        "Comentar en Instagram" to ajustes.enlaceInstagram,
        "Comentar en Facebook" to ajustes.enlaceFacebook,
    )

    fun enlacesFaltantes(): List<String> = enlaces().filterValues { it.isBlank() }.keys.toList()

    /** Texto final con los 3 enlaces al final. Los clips llevan solo los enlaces. Null si falta alguno. */
    fun armarTexto(item: Item): String? {
        if (enlacesFaltantes().isNotEmpty()) return null
        val bloque = enlaces().entries.joinToString("\n") { "${it.key}: ${it.value}" }
        return if (item.tipo == Tipos.CLIP) bloque else item.texto.trim() + "\n\n" + bloque
    }

    // ---------- Sync con GitHub ----------

    /** Baja paquetes nuevos o rehechos. Devuelve un resumen. Tira IOException si GitHub falla. */
    suspend fun sincronizar(): String = withContext(Dispatchers.IO) {
        candado.withLock {
            val token = ajustes.token
            if (token.isBlank()) return@withLock "Sin token: seguís con lo que ya bajaste."
            val gh = GitHub(token, ajustes.repo, ajustes.rama)
            val carpetas = gh.listarCarpetas("paquetes/pendientes")
            var nuevos = 0
            var rehechos = 0
            var errores = 0
            for (carpeta in carpetas) {
                val existente = dao.itemPorCarpeta(carpeta.nombre)
                if (existente != null && existente.remoteSha == carpeta.sha) continue
                if (existente != null && existente.descartado && !existente.rehaciendo) {
                    dao.guardarItem(existente.copy(remoteSha = carpeta.sha))
                    continue
                }
                try {
                    val base = "paquetes/pendientes/${carpeta.nombre}"
                    val m = JSONObject(gh.bajarTexto("$base/manifiesto.json"))
                    val tipo = m.optString("tipo", Tipos.FLYER)
                    if (tipo != Tipos.FLYER) continue
                    val id = m.optString("id").ifBlank { carpeta.nombre }
                    val nombreFlyer = m.optString("flyer", "flyer.jpg").ifBlank { "flyer.jpg" }
                    val archivo = File(Medios.carpeta(context), "${limpiarNombre(id)}.jpg")
                    gh.bajarArchivo("$base/$nombreFlyer", archivo)
                    val item = Item(
                        id = existente?.id ?: id,
                        tipo = Tipos.FLYER,
                        estado = Estados.PENDIENTE,
                        texto = m.optString("texto"),
                        angulo = m.optString("angulo").ifBlank { null },
                        redSugerida = m.optString("redSugerida").takeIf { it == Redes.INSTAGRAM || it == Redes.FACEBOOK },
                        mediaPath = archivo.absolutePath,
                        thumbPath = archivo.absolutePath,
                        creadoEn = m.optString("creadoEn").ifBlank { Instant.now().toString() },
                        bajadoMs = System.currentTimeMillis(),
                        carpeta = carpeta.nombre,
                        remoteSha = carpeta.sha,
                        descartado = false,
                        rehaciendo = false,
                    )
                    dao.guardarItem(item)
                    if (existente == null) nuevos++ else rehechos++
                } catch (e: Exception) {
                    errores++
                }
            }
            val hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            val resumen = buildString {
                append("Sync $hora: $nuevos nuevos")
                if (rehechos > 0) append(", $rehechos rehechos")
                if (errores > 0) append(", $errores con error")
                append(".")
            }
            ajustes.ultimoSync = resumen
            resumen
        }
    }

    private fun limpiarNombre(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_")

    suspend fun hayContenidoNuevoHoy(): Boolean = dao.flyersBajadosDesde(inicioDeHoyMs()) > 0

    // ---------- Galería ----------

    suspend fun usar(item: Item) = dao.guardarItem(item.copy(estado = Estados.LISTO, descartado = false))

    suspend fun descartar(item: Item) = dao.guardarItem(item.copy(descartado = true))

    suspend fun restaurar(item: Item) = dao.guardarItem(item)

    /** POST al aviso de rehacer con {"id": ...}. Devuelve un mensaje para mostrar. */
    suspend fun rehacer(item: Item): String = withContext(Dispatchers.IO) {
        if (item.tipo != Tipos.FLYER) return@withContext "Los clips no se rehacen."
        val url = ajustes.avisoUrl
        if (url.isBlank()) return@withContext "Falta la URL del aviso de rehacer en Ajustes."
        try {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "pulso-android")
                val clave = ajustes.avisoClave
                if (clave.isNotBlank()) {
                    val header = ajustes.avisoHeader
                    val valor = if (header.equals("Authorization", ignoreCase = true)) "Bearer $clave" else clave
                    setRequestProperty(header, valor)
                }
            }
            val cuerpo = JSONObject().put("id", item.id).toString()
            c.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            c.disconnect()
            if (code in 200..299) {
                dao.guardarItem(item.copy(estado = Estados.PENDIENTE, rehaciendo = true))
                "Pedido enviado. Cuando llegue el nuevo, aparece acá después de sincronizar."
            } else {
                "El aviso respondió $code. No se pidió el cambio."
            }
        } catch (e: Exception) {
            "No pude mandar el aviso: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Copia un .mp4 elegido del teléfono a la app. Nunca va a GitHub. */
    suspend fun agregarClip(uri: Uri): Item = withContext(Dispatchers.IO) {
        val id = "clip-" + System.currentTimeMillis()
        val video = File(Medios.carpeta(context), "$id.mp4")
        val entrada = context.contentResolver.openInputStream(uri) ?: throw IOException("No pude abrir el video.")
        entrada.use { e -> video.outputStream().use { e.copyTo(it) } }
        val mini = File(Medios.carpeta(context), "$id-mini.jpg")
        val conMini = Medios.miniaturaDeVideo(video, mini)
        val item = Item(
            id = id, tipo = Tipos.CLIP, estado = Estados.PENDIENTE, texto = "",
            angulo = null, redSugerida = null, mediaPath = video.absolutePath,
            thumbPath = if (conMini) mini.absolutePath else null,
            creadoEn = Instant.now().toString(), bajadoMs = System.currentTimeMillis(),
        )
        dao.guardarItem(item)
        item
    }

    /** Paquete de prueba local, sin GitHub. Queda listo para agendar. */
    suspend fun cargarPaquetePrueba(): Item = withContext(Dispatchers.IO) {
        val n = ajustes.contadorPrueba + 1
        ajustes.contadorPrueba = n
        val angulos = listOf("problema", "incluye", "no-es")
        val textos = listOf(
            "¿Tus clientes te preguntan los precios todo el día? Con una app en su teléfono los ven cuando quieren.",
            "Precios, servicios y turnos en el teléfono de tu cliente. Sin servidor y sin cuota mensual.",
            "No es un bot ni un chat automático. Es una app Android para tu negocio, con tus datos guardados.",
        )
        val titulos = listOf("¿Otra vez el precio?", "Todo en su teléfono", "No es un bot")
        val i = (n - 1) % 3
        val id = "prueba-local-%03d".format(n)
        val archivo = File(Medios.carpeta(context), "$id.jpg")
        Medios.flyerDePrueba(archivo, titulos[i], "Paquete de prueba $n")
        val item = Item(
            id = id, tipo = Tipos.FLYER, estado = Estados.LISTO, texto = textos[i],
            angulo = angulos[i], redSugerida = null, mediaPath = archivo.absolutePath,
            thumbPath = archivo.absolutePath, creadoEn = Instant.now().toString(),
            bajadoMs = System.currentTimeMillis(),
        )
        dao.guardarItem(item)
        item
    }

    /** Borra archivos de descartados y de lo no usado con más de 30 días. El repo no se toca. */
    suspend fun limpiar() = withContext(Dispatchers.IO) {
        val limite = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val agendados = dao.enviosActivos().mapNotNull { it.itemId }.toSet()
        for (item in dao.todosItems()) {
            val sinUsoViejo = item.estado != Estados.ENVIADO && item.bajadoMs < limite && item.id !in agendados
            val tieneArchivos = item.mediaPath != null || item.thumbPath != null
            if ((item.descartado || sinUsoViejo) && item.id !in agendados) {
                if (!tieneArchivos && item.descartado) continue
                listOfNotNull(item.mediaPath, item.thumbPath).forEach { File(it).delete() }
                if (item.tipo == Tipos.CLIP) {
                    dao.borrarItem(item)
                } else {
                    // Se guarda la fila (sin archivos) para no volver a bajar el paquete.
                    dao.guardarItem(item.copy(descartado = true, mediaPath = null, thumbPath = null))
                }
            }
        }
    }

    // ---------- Orquestador ----------

    data class Posteo(val orden: Int, val horaMin: Int?, val destinoId: Long?)

    /** Elige contenido: alterna flyer y clip si hay clips listos; no repite flyer ni ángulo en el día. */
    private suspend fun elegir(dia: String, excluirEnvio: Long?, tipoAnterior: String?): Item? {
        val delDia = dao.enviosDelDia(dia).filter { it.id != excluirEnvio }
        val usados = delDia.mapNotNull { it.itemId }.toSet()
        val angulosUsados = delDia.filter { it.estado != Estados.SALTADO }
            .mapNotNull { e -> e.itemId?.let { dao.item(it) } }
            .filter { it.tipo == Tipos.FLYER }
            .mapNotNull { it.angulo }.toSet()
        val listos = dao.itemsListos().filter { it.id !in usados && File(it.mediaPath!!).exists() }
        val clips = listos.filter { it.tipo == Tipos.CLIP }
        val flyers = listos.filter { it.tipo == Tipos.FLYER && (it.angulo == null || it.angulo !in angulosUsados) }
        return when {
            tipoAnterior == Tipos.FLYER && clips.isNotEmpty() -> clips.first()
            flyers.isNotEmpty() -> flyers.first()
            else -> clips.firstOrNull()
        }
    }

    private suspend fun tipoAnterior(dia: String, horaMin: Int, excluirEnvio: Long?): String? =
        dao.enviosDelDia(dia)
            .filter { it.id != excluirEnvio && it.horaMin <= horaMin && it.estado != Estados.SALTADO }
            .maxByOrNull { it.horaMin }
            ?.itemId?.let { dao.item(it) }?.tipo

    /** Agenda los posteos del día. Sin hora o sin destino, ese posteo no se agenda. */
    suspend fun planificar(posteos: List<Posteo>): List<String> = withContext(Dispatchers.IO) {
        candado.withLock {
            val dia = hoy()
            val mensajes = mutableListOf<String>()
            // Reemplaza lo que estaba agendado y todavía no sonó.
            for (e in dao.enviosDelDia(dia).filter { it.estado == Estados.PENDIENTE }) {
                Alarmas.cancelar(context, e.id)
                dao.borrarEnvio(e)
            }
            val ahora = System.currentTimeMillis()
            for (p in posteos.sortedBy { it.horaMin ?: Int.MAX_VALUE }) {
                val n = p.orden
                if (p.horaMin == null) { mensajes += "Posteo $n: sin hora, no se agendó."; continue }
                if (p.destinoId == null) { mensajes += "Posteo $n: sin destino, no se agendó."; continue }
                val destino = dao.destino(p.destinoId)
                if (destino == null) { mensajes += "Posteo $n: el destino ya no existe."; continue }
                val cuando = msDeHora(dia, p.horaMin)
                if (cuando <= ahora) { mensajes += "Posteo $n: esa hora ya pasó."; continue }
                val item = elegir(dia, null, tipoAnterior(dia, p.horaMin, null))
                val envio = Envio(
                    dia = dia, orden = n, horaMin = p.horaMin, destinoId = destino.id,
                    itemId = item?.id, estado = Estados.PENDIENTE,
                )
                val id = dao.insertarEnvio(envio)
                Alarmas.programar(context, id, 0, cuando)
                Alarmas.programarSync(context, id, cuando)
                mensajes += "Posteo $n: ${envio.horaTexto()} en ${Redes.nombre(destino.red)} · ${destino.nombre}" +
                    if (item == null) " (todavía sin contenido listo)" else ""
            }
            mensajes
        }
    }

    /** Verifica que el envío tenga contenido válido; si no, elige otro. */
    suspend fun asegurarItem(envio: Envio): Envio {
        if (envio.estado == Estados.ENVIADO || envio.estado == Estados.SALTADO) return envio
        val actual = envio.itemId?.let { dao.item(it) }
        val valido = actual != null && !actual.descartado && actual.mediaPath != null &&
            File(actual.mediaPath).exists() &&
            actual.estado == Estados.LISTO
        if (valido) return envio
        val nuevo = elegir(envio.dia, envio.id, tipoAnterior(envio.dia, envio.horaMin, envio.id))
        val e = envio.copy(itemId = nuevo?.id)
        dao.actualizarEnvio(e)
        return e
    }

    /** Lo llama la alarma. Intento 0: aviso. Intento 1: reintento a los 10 min. Intento 2: saltado. */
    suspend fun alDispararse(envioId: Long, intento: Int) = candado.withLock {
        var envio = dao.envio(envioId) ?: return@withLock
        if (envio.estado == Estados.ENVIADO || envio.estado == Estados.SALTADO) return@withLock
        if (intento >= 2) {
            dao.actualizarEnvio(envio.copy(estado = Estados.SALTADO, intento = intento))
            envio.itemId?.let { id -> dao.item(id)?.let { dao.guardarItem(it.copy(estado = Estados.SALTADO)) } }
            Avisos.cancelar(context, envioId)
            return@withLock
        }
        envio = asegurarItem(envio).copy(estado = Estados.LISTO, intento = intento)
        dao.actualizarEnvio(envio)
        val destino = dao.destino(envio.destinoId)
        val red = Redes.nombre(destino?.red)
        val titulo = if (intento == 0) "Es hora de publicar en $red" else "Recordatorio: falta publicar en $red"
        val texto = if (envio.itemId == null) {
            "Posteo ${envio.orden} de las ${envio.horaTexto()}: no hay contenido listo. Elegí uno en Galería."
        } else {
            "Posteo ${envio.orden} · ${destino?.nombre ?: "sin destino"}. Está todo armado: abrí y tocá Enviar."
        }
        Avisos.mostrar(context, envioId, titulo, texto)
        Alarmas.programar(context, envioId, intento + 1, System.currentTimeMillis() + Alarmas.REINTENTO_MS)
    }

    suspend fun marcarEnviado(envioId: Long) = candado.withLock {
        val e = dao.envio(envioId) ?: return@withLock
        dao.actualizarEnvio(e.copy(estado = Estados.ENVIADO, enviadoMs = System.currentTimeMillis()))
        e.itemId?.let { id -> dao.item(id)?.let { dao.guardarItem(it.copy(estado = Estados.ENVIADO)) } }
        Alarmas.cancelar(context, envioId)
        Avisos.cancelar(context, envioId)
    }

    suspend fun deshacerEnviado(envioId: Long) = candado.withLock {
        val e = dao.envio(envioId) ?: return@withLock
        dao.actualizarEnvio(e.copy(estado = Estados.LISTO, enviadoMs = null))
        e.itemId?.let { id -> dao.item(id)?.let { dao.guardarItem(it.copy(estado = Estados.LISTO)) } }
    }

    /** Qué muestra la pantalla Enviar: el pedido, o el próximo listo/pendiente de hoy, o el último enviado. */
    suspend fun envioParaMostrar(pedido: Long?): Envio? {
        pedido?.let { id -> dao.envio(id)?.let { return asegurarItem(it) } }
        val delDia = dao.enviosDelDia(hoy())
        val elegido = delDia.firstOrNull { it.estado == Estados.LISTO }
            ?: delDia.firstOrNull { it.estado == Estados.PENDIENTE }
            ?: return delDia.filter { it.estado == Estados.ENVIADO }.maxByOrNull { it.enviadoMs ?: 0 }
        return asegurarItem(elegido)
    }

    /** Tras reiniciar: vuelve a programar las alarmas de hoy. */
    suspend fun reprogramarTodo() {
        val ahora = System.currentTimeMillis()
        for (e in dao.enviosActivos()) {
            if (e.dia != hoy()) {
                dao.actualizarEnvio(e.copy(estado = Estados.SALTADO))
                continue
            }
            when (e.estado) {
                Estados.PENDIENTE -> {
                    val cuando = msDeHora(e.dia, e.horaMin).coerceAtLeast(ahora + 5_000)
                    Alarmas.programar(context, e.id, 0, cuando)
                    Alarmas.programarSync(context, e.id, cuando)
                }
                Estados.LISTO -> Alarmas.programar(context, e.id, (e.intento + 1).coerceAtMost(2), ahora + Alarmas.REINTENTO_MS)
            }
        }
    }

    companion object {
        @Volatile
        private var instancia: Consola? = null

        fun get(context: Context): Consola = instancia ?: synchronized(this) {
            instancia ?: Consola(context.applicationContext).also { instancia = it }
        }
    }
}
