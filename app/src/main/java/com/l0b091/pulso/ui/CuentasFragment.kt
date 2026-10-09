package com.l0b091.pulso.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.l0b091.pulso.data.Consola
import com.l0b091.pulso.data.Destino
import com.l0b091.pulso.data.Redes
import com.l0b091.pulso.databinding.DialogoDestinoBinding
import com.l0b091.pulso.databinding.FragmentCuentasBinding
import com.l0b091.pulso.databinding.ItemCuentaBinding
import com.l0b091.pulso.databinding.ItemDestinoBinding
import kotlinx.coroutines.launch

class CuentasFragment : Fragment() {
    private var _b: FragmentCuentasBinding? = null
    private val b get() = _b!!
    private lateinit var consola: Consola

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentCuentasBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        consola = Consola.get(requireContext())
        preparar(b.tarjetaInstagram, Redes.INSTAGRAM)
        preparar(b.tarjetaFacebook, Redes.FACEBOOK)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                consola.dao.observarDestinos().collect { lista ->
                    pintar(b.tarjetaInstagram, lista.filter { it.red == Redes.INSTAGRAM })
                    pintar(b.tarjetaFacebook, lista.filter { it.red == Redes.FACEBOOK })
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun preparar(t: ItemCuentaBinding, red: String) {
        t.nombreRed.text = Redes.nombre(red)
        t.btnAbrirApp.text = "Abrir ${Redes.nombre(red)}"
        t.cabecera.setOnClickListener {
            val abierta = t.cuerpo.visibility == View.VISIBLE
            t.cuerpo.visibility = if (abierta) View.GONE else View.VISIBLE
            t.flecha.rotation = if (abierta) 0f else 180f
        }
        t.btnAbrirApp.setOnClickListener { abrirRed(red) }
        t.btnAgregar.setOnClickListener { agregarDestino(red) }
    }

    private fun pintar(t: ItemCuentaBinding, destinos: List<Destino>) {
        t.estadoRed.text = if (destinos.isEmpty()) "no configurada" else "lista"
        t.sinDestinos.visibility = if (destinos.isEmpty()) View.VISIBLE else View.GONE
        t.listaDestinos.removeAllViews()
        for (d in destinos) {
            val fila = ItemDestinoBinding.inflate(layoutInflater, t.listaDestinos, false)
            fila.nombre.text = "${d.nombre} · ${d.tipo}"
            fila.url.text = d.url
            fila.root.setOnClickListener { abrirUrl(d.url) }
            fila.root.setOnLongClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("¿Borrar ${d.nombre}?")
                    .setPositiveButton("Borrar") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch { consola.dao.borrarDestino(d) }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
                true
            }
            t.listaDestinos.addView(fila.root)
        }
    }

    /** Abre la app oficial si está instalada; si no, el navegador. */
    private fun abrirRed(red: String) {
        val ctx = requireContext()
        val paquete = if (red == Redes.INSTAGRAM) "com.instagram.android" else "com.facebook.katana"
        val lanzar = ctx.packageManager.getLaunchIntentForPackage(paquete)
        if (lanzar != null) {
            startActivity(lanzar)
        } else {
            abrirUrl(if (red == Redes.INSTAGRAM) "https://www.instagram.com/" else "https://www.facebook.com/")
        }
    }

    private fun abrirUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No pude abrir $url", Toast.LENGTH_SHORT).show()
        }
    }

    private fun agregarDestino(red: String) {
        val d = DialogoDestinoBinding.inflate(layoutInflater)
        if (red == Redes.INSTAGRAM) d.tipo.visibility = View.GONE
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nuevo destino en ${Redes.nombre(red)}")
            .setView(d.root)
            .setPositiveButton("Agregar") { _, _ ->
                val nombre = d.nombre.text?.toString()?.trim().orEmpty()
                var url = d.url.text?.toString()?.trim().orEmpty()
                if (nombre.isBlank() || url.isBlank()) {
                    Toast.makeText(requireContext(), "Poné nombre y URL.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
                val tipo = if (red == Redes.FACEBOOK && d.tipoGrupo.isChecked) "grupo" else "perfil"
                viewLifecycleOwner.lifecycleScope.launch {
                    consola.dao.insertarDestino(Destino(red = red, tipo = tipo, nombre = nombre, url = url))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
