package com.l0b091.pulso.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.l0b091.pulso.R
import com.l0b091.pulso.data.Consola
import com.l0b091.pulso.data.Destino
import com.l0b091.pulso.data.Envio
import com.l0b091.pulso.data.Estados
import com.l0b091.pulso.data.Item
import com.l0b091.pulso.data.Redes
import com.l0b091.pulso.data.Tipos
import com.l0b091.pulso.databinding.FragmentEnviarBinding
import com.l0b091.pulso.util.Medios
import kotlinx.coroutines.launch
import java.io.File

class EnviarFragment : Fragment() {
    private var _b: FragmentEnviarBinding? = null
    private val b get() = _b!!
    private lateinit var consola: Consola

    private var envio: Envio? = null
    private var item: Item? = null
    private var destino: Destino? = null
    private var texto: String? = null
    private var esperandoRetorno = false

    private val compartir = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        alVolverDeCompartir()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentEnviarBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        consola = Consola.get(requireContext())
        esperandoRetorno = savedInstanceState?.getBoolean("esperando") ?: false
        b.pestanas.addTab(b.pestanas.newTab().setText("Instagram"))
        b.pestanas.addTab(b.pestanas.newTab().setText("Facebook"))
        for (i in 0 until b.pestanas.tabCount) b.pestanas.getTabAt(i)?.view?.isClickable = false
        b.btnEnviar.setOnClickListener { enviar() }
        b.btnDeshacer.setOnClickListener { deshacer() }
        b.btnAbrirDia.setOnClickListener { (activity as? MainActivity)?.abrirDia() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("esperando", esperandoRetorno)
    }

    override fun onResume() {
        super.onResume()
        if (!esperandoRetorno) cargar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun cargar() {
        viewLifecycleOwner.lifecycleScope.launch {
            val pedido = arguments?.getLong(ARG_ENVIO, -1L)?.takeIf { it > 0 }
            val e = consola.envioParaMostrar(pedido)
            envio = e
            item = e?.itemId?.let { consola.dao.item(it) }
            destino = e?.let { consola.dao.destino(it.destinoId) }
            if (_b != null) mostrar()
        }
    }

    private fun mostrar() {
        val e = envio
        if (e == null) {
            b.vacio.visibility = View.VISIBLE
            b.contenido.visibility = View.GONE
            b.pestanas.visibility = View.GONE
            b.btnEnviar.isEnabled = false
            b.btnEnviar.text = getString(R.string.enviar)
            return
        }
        b.vacio.visibility = View.GONE
        b.contenido.visibility = View.VISIBLE
        b.pestanas.visibility = View.VISIBLE

        val d = destino
        val pestana = if (d?.red == Redes.FACEBOOK) 1 else 0
        b.pestanas.selectTab(b.pestanas.getTabAt(pestana))

        b.lineaEstado.text = "Posteo ${e.orden} · ${e.horaTexto()} · ${e.estado}"
        b.destino.setText(if (d == null) "Sin destino" else "${d.nombre} (${d.tipo}) · ${d.url}")

        val it = item
        val avisos = mutableListOf<String>()
        if (it == null) {
            b.medio.setImageDrawable(null)
            b.iconoClip.visibility = View.GONE
            b.sinMedio.visibility = View.VISIBLE
            b.sinMedio.text = "No hay contenido listo para este posteo.\nMarcá uno con Usar en la Galería."
            b.texto.text = ""
            texto = null
        } else {
            b.sinMedio.visibility = View.GONE
            b.iconoClip.visibility = if (it.tipo == Tipos.CLIP) View.VISIBLE else View.GONE
            Medios.cargar(viewLifecycleOwner.lifecycleScope, b.medio, it.thumbPath ?: it.mediaPath, 1080)
            texto = consola.armarTexto(it)
            if (texto == null) {
                val faltan = consola.enlacesFaltantes().joinToString(", ")
                avisos += "No se puede armar el envío: falta el enlace de $faltan. Cargalo en Ajustes."
                b.texto.text = if (it.tipo == Tipos.CLIP) "(Clip: el texto son solo los 3 enlaces)" else it.texto
            } else {
                b.texto.text = texto
            }
        }
        if (d == null) avisos += "Este posteo no tiene destino."
        b.aviso.visibility = if (avisos.isEmpty()) View.GONE else View.VISIBLE
        b.aviso.text = avisos.joinToString("\n")

        val enviado = e.estado == Estados.ENVIADO
        b.bandaEnviado.visibility = if (enviado) View.VISIBLE else View.GONE
        b.btnEnviar.text = getString(if (enviado) R.string.enviado else R.string.enviar)
        b.btnEnviar.isEnabled = !enviado && texto != null && d != null && it != null &&
            e.estado != Estados.SALTADO
    }

    private fun enviar() {
        val ctx = requireContext()
        val it = item ?: return
        val t = texto ?: run {
            Toast.makeText(ctx, "Faltan enlaces en Ajustes.", Toast.LENGTH_LONG).show(); return
        }
        val archivo = it.mediaPath?.let { p -> File(p) }
        if (archivo == null || !archivo.exists()) {
            Toast.makeText(ctx, "No encuentro el archivo de este contenido.", Toast.LENGTH_LONG).show(); return
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".archivos", archivo)
        val mime = if (it.tipo == Tipos.CLIP) "video/mp4" else "image/jpeg"

        // Algunas apps ignoran el texto compartido: queda copiado para pegarlo.
        val portapapeles = ctx.getSystemService(ClipboardManager::class.java)
        portapapeles.setPrimaryClip(ClipData.newPlainText("Texto del posteo", t))

        val enviarIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, t)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val paquete = when (destino?.red) {
            Redes.INSTAGRAM -> "com.instagram.android"
            Redes.FACEBOOK -> "com.facebook.katana"
            else -> null
        }
        val directo = paquete?.let { p -> Intent(enviarIntent).setPackage(p) }
        val intent = if (directo != null && directo.resolveActivity(ctx.packageManager) != null) {
            directo
        } else {
            Intent.createChooser(enviarIntent, "Enviar con")
        }
        Toast.makeText(ctx, "Texto copiado. Si la app no lo pega sola, mantené apretado y pegá.", Toast.LENGTH_LONG).show()
        try {
            esperandoRetorno = true
            compartir.launch(intent)
        } catch (e: ActivityNotFoundException) {
            esperandoRetorno = false
            Toast.makeText(ctx, "No hay ninguna app para compartir.", Toast.LENGTH_LONG).show()
        }
    }

    /** Android no avisa si se publicó de verdad: al volver se marca enviado y queda Deshacer. */
    private fun alVolverDeCompartir() {
        esperandoRetorno = false
        val e = envio ?: return
        val url = destino?.url
        lifecycleScope.launch {
            consola.marcarEnviado(e.id)
            if (_b != null) cargar()
            if (!url.isNullOrBlank()) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun deshacer() {
        val e = envio ?: return
        lifecycleScope.launch {
            consola.deshacerEnviado(e.id)
            if (_b != null) cargar()
        }
    }

    companion object {
        private const val ARG_ENVIO = "envio"

        fun nuevo(envioId: Long?) = EnviarFragment().apply {
            if (envioId != null) arguments = bundleOf(ARG_ENVIO to envioId)
        }
    }
}
