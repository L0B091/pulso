package com.l0b091.pulso.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.l0b091.pulso.R
import com.l0b091.pulso.data.Consola
import com.l0b091.pulso.data.Destino
import com.l0b091.pulso.data.Estados
import com.l0b091.pulso.data.Redes
import com.l0b091.pulso.data.Tipos
import com.l0b091.pulso.databinding.FragmentDiaBinding
import com.l0b091.pulso.databinding.ItemPosteoBinding
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch

/** Orquestador: pregunta cuántos posteos, y la hora y el destino de cada uno. No propone horas. */
class DiaFragment : Fragment() {
    private var _b: FragmentDiaBinding? = null
    private val b get() = _b!!
    private lateinit var consola: Consola

    private var destinos: List<Destino> = emptyList()
    private val horas = arrayOfNulls<Int>(3)
    private val elegidosIg = arrayOfNulls<Long>(3)
    private val elegidosFb = arrayOfNulls<Long>(3)
    private var cantidad = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentDiaBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        consola = Consola.get(requireContext())
        b.cantidad.addOnButtonCheckedListener { _, id, marcado ->
            if (!marcado) {
                if (b.cantidad.checkedButtonId == View.NO_ID) { cantidad = 0; armarFilas() }
                return@addOnButtonCheckedListener
            }
            cantidad = when (id) { R.id.cant1 -> 1; R.id.cant2 -> 2; else -> 3 }
            armarFilas()
        }
        b.btnAgendar.setOnClickListener { agendar() }
        cargar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun cargar() {
        viewLifecycleOwner.lifecycleScope.launch {
            destinos = consola.dao.destinos()
            val nuevo = consola.hayContenidoNuevoHoy()
            val listos = consola.dao.itemsListos()
            val flyers = listos.count { it.tipo == Tipos.FLYER }
            val clips = listos.count { it.tipo == Tipos.CLIP }
            val hoy = consola.dao.enviosDelDia(consola.hoy())
            val resumen = hoy.map { e ->
                val ig = destinos.firstOrNull { it.id == e.destinoIgId }?.nombre ?: "?"
                val fb = destinos.firstOrNull { it.id == e.destinoFbId }?.nombre ?: "?"
                "${e.horaTexto()} · Instagram ($ig): ${e.estadoIg} · Facebook ($fb): ${e.estadoFb}"
            }
            val vista = _b ?: return@launch
            vista.mensaje.text = buildString {
                append("Buen día. ")
                if (nuevo) append("Ya bajó el contenido de hoy. ")
                append("¿Cuántos posteos hacemos hoy?")
                append("\nCada posteo sale igual en Instagram y en Facebook.")
                if (destinos.none { it.red == Redes.INSTAGRAM } || destinos.none { it.red == Redes.FACEBOOK }) {
                    append("\n\nNecesitás al menos un destino de Instagram y uno de Facebook: agregalos en Cuentas.")
                }
            }
            vista.sinContenido.visibility = if (nuevo) View.GONE else View.VISIBLE
            vista.disponibles.text = "Listos para usar: $flyers flyers y $clips clips."
            vista.tituloHoy.visibility = if (resumen.isEmpty()) View.GONE else View.VISIBLE
            vista.resumenHoy.text = resumen.joinToString("\n")
            armarFilas()
        }
    }

    private fun armarFilas() {
        val vista = _b ?: return
        vista.posteos.removeAllViews()
        for (i in 0 until cantidad) {
            val fila = ItemPosteoBinding.inflate(layoutInflater, vista.posteos, false)
            fila.titulo.text = "Posteo ${i + 1}"
            horas[i]?.let { fila.btnHora.text = "%02d:%02d".format(it / 60, it % 60) }
            fila.btnHora.setOnClickListener {
                val picker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setTitleText("Hora del posteo ${i + 1}")
                    .build()
                picker.addOnPositiveButtonClickListener {
                    horas[i] = picker.hour * 60 + picker.minute
                    fila.btnHora.text = "%02d:%02d".format(picker.hour, picker.minute)
                }
                picker.show(childFragmentManager, "hora$i")
            }
            enlazarDestino(fila.destinoIg, destinos.filter { it.red == Redes.INSTAGRAM }, elegidosIg, i)
            enlazarDestino(fila.destinoFb, destinos.filter { it.red == Redes.FACEBOOK }, elegidosFb, i)
            vista.posteos.addView(fila.root)
        }
    }

    private fun enlazarDestino(
        campo: MaterialAutoCompleteTextView,
        lista: List<Destino>,
        elegidos: Array<Long?>,
        i: Int,
    ) {
        val opciones = lista.map { "${it.nombre} (${it.tipo})" }
        campo.setSimpleItems(opciones.toTypedArray())
        elegidos[i]?.let { id ->
            val pos = lista.indexOfFirst { it.id == id }
            if (pos >= 0) campo.setText(opciones[pos], false)
        }
        campo.setOnItemClickListener { _, _, pos, _ -> elegidos[i] = lista[pos].id }
    }

    private fun agendar() {
        if (cantidad == 0) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage("Elegí cuántos posteos hacemos hoy: 1, 2 o 3. Sin respuesta, no agendo nada.")
                .setPositiveButton("Ok", null).show()
            return
        }
        val posteos = (0 until cantidad).map { Consola.Posteo(it + 1, horas[it], elegidosIg[it], elegidosFb[it]) }
        viewLifecycleOwner.lifecycleScope.launch {
            val mensajes = consola.planificar(posteos)
            val agendados = consola.dao.enviosDelDia(consola.hoy()).count { it.estado == Estados.PENDIENTE }
            if (_b == null) return@launch
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (agendados > 0) "Agendado" else "No se agendó nada")
                .setMessage(mensajes.joinToString("\n"))
                .setPositiveButton("Ok") { _, _ ->
                    if (agendados > 0) (activity as? MainActivity)?.abrirEnviar(null) else cargar()
                }
                .show()
        }
    }
}
