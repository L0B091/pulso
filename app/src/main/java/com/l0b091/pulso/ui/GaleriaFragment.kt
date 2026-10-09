package com.l0b091.pulso.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.l0b091.pulso.R
import com.l0b091.pulso.data.Consola
import com.l0b091.pulso.data.Item
import com.l0b091.pulso.databinding.FragmentGaleriaBinding
import kotlinx.coroutines.launch

class GaleriaFragment : Fragment() {
    private var _b: FragmentGaleriaBinding? = null
    private val b get() = _b!!
    private lateinit var consola: Consola

    private val elegirClip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val ctx = requireContext().applicationContext
        Toast.makeText(ctx, "Copiando el clip…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                consola.agregarClip(uri)
                Toast.makeText(ctx, "Clip agregado. Tocá Usar para que se pueda agendar.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "No pude agregar el clip: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentGaleriaBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        consola = Consola.get(requireContext())
        val adapter = GaleriaAdapter(viewLifecycleOwner.lifecycleScope, ::usar, ::rehacer, ::descartar)
        b.grilla.layoutManager = GridLayoutManager(requireContext(), 2)
        b.grilla.adapter = adapter
        b.refrescar.setColorSchemeResources(R.color.naranja)
        b.refrescar.setProgressBackgroundColorSchemeResource(R.color.azul_tarjeta)
        b.refrescar.setOnRefreshListener { sincronizar() }
        b.btnAgregarClip.setOnClickListener { elegirClip.launch(arrayOf("video/mp4")) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                consola.dao.observarGaleria().collect { lista ->
                    adapter.submitList(lista)
                    b.vacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun sincronizar() {
        viewLifecycleOwner.lifecycleScope.launch {
            val msg = try {
                consola.sincronizar()
            } catch (e: Exception) {
                "No se pudo sincronizar: ${e.message}. Seguís con lo que ya bajaste."
            }
            _b?.let {
                it.refrescar.isRefreshing = false
                Snackbar.make(it.root, msg, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun usar(item: Item) {
        viewLifecycleOwner.lifecycleScope.launch { consola.usar(item) }
    }

    private fun rehacer(item: Item) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Rehacer este flyer?")
            .setMessage("Se manda el aviso para que se haga uno nuevo. Cuando llegue, reemplaza a este.")
            .setPositiveButton("Rehacer") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val msg = consola.rehacer(item)
                    _b?.let { Snackbar.make(it.root, msg, Snackbar.LENGTH_LONG).show() }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun descartar(item: Item) {
        viewLifecycleOwner.lifecycleScope.launch {
            consola.descartar(item)
            _b?.let {
                Snackbar.make(it.root, "Descartado.", Snackbar.LENGTH_LONG)
                    .setAction(R.string.deshacer) { lifecycleScope.launch { consola.restaurar(item) } }
                    .show()
            }
        }
    }
}
