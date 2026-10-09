package com.l0b091.pulso.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object Medios {
    const val AZUL = 0xFF0B1F3A.toInt()
    const val NARANJA = 0xFFFF7A1A.toInt()
    const val CLARO = 0xFFF4F7FB.toInt()

    fun carpeta(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cargar(scope: CoroutineScope, vista: ImageView, ruta: String?, anchoPx: Int) {
        vista.tag = ruta
        vista.setImageDrawable(null)
        if (ruta == null) return
        val archivo = File(ruta)
        val clave = "$ruta:${archivo.lastModified()}:$anchoPx"
        cache.get(clave)?.let { vista.setImageBitmap(it); return }
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { decodificar(ruta, anchoPx) }
            if (bmp != null) {
                cache.put(clave, bmp)
                if (vista.tag == ruta) vista.setImageBitmap(bmp)
            }
        }
    }

    fun decodificar(ruta: String, anchoPx: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ruta, opts)
        if (opts.outWidth <= 0) return null
        var muestra = 1
        while (opts.outWidth / (muestra * 2) >= anchoPx) muestra *= 2
        return BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = muestra })
    }

    /** Guarda un cuadro del video como miniatura JPG. */
    fun miniaturaDeVideo(video: File, destino: File): Boolean {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(video.absolutePath)
            val cuadro = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: r.frameAtTime ?: return false
            val escala = 720f / cuadro.width.coerceAtLeast(1)
            val chico = if (escala < 1f) Bitmap.createScaledBitmap(
                cuadro, 720, (cuadro.height * escala).toInt().coerceAtLeast(1), true
            ) else cuadro
            destino.outputStream().use { chico.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        } catch (e: Exception) {
            false
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    /** Flyer de prueba 1080x1350 (4:5): fondo azul marino, acento naranja, poco texto. */
    fun flyerDePrueba(destino: File, titulo: String, bajada: String) {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(AZUL)
        val naranja = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NARANJA }
        val trazo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NARANJA; style = Paint.Style.STROKE; strokeWidth = 18f
        }
        // Teléfono estilizado
        val tel = RectF(w / 2f - 190f, 170f, w / 2f + 190f, 830f)
        c.drawRoundRect(tel, 60f, 60f, trazo)
        val claroP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CLARO; alpha = 230 }
        for (i in 0 until 3) {
            val y = 300f + i * 150f
            c.drawRoundRect(RectF(tel.left + 60f, y, tel.right - 60f, y + 90f), 24f, 24f, claroP)
            c.drawCircle(tel.right - 100f, y + 45f, 22f, naranja)
        }
        val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CLARO; textSize = 84f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        c.drawText(titulo, w / 2f, 1010f, texto)
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NARANJA; textSize = 56f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        c.drawText(bajada, w / 2f, 1100f, sub)
        c.drawRect(0f, h - 40f, w.toFloat(), h.toFloat(), naranja)
        destino.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
    }

    @Suppress("unused")
    private val transparente = Color.TRANSPARENT
}
