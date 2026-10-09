package com.l0b091.pulso.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.l0b091.pulso.data.Estados
import com.l0b091.pulso.data.Item
import com.l0b091.pulso.data.Tipos
import com.l0b091.pulso.databinding.ItemGaleriaBinding
import com.l0b091.pulso.util.Medios

class GaleriaAdapter(
    private val alcance: LifecycleCoroutineScope,
    private val alUsar: (Item) -> Unit,
    private val alRehacer: (Item) -> Unit,
    private val alDescartar: (Item) -> Unit,
) : ListAdapter<Item, GaleriaAdapter.VH>(DIF) {

    class VH(val b: ItemGaleriaBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemGaleriaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = getItem(position)
        val esClip = item.tipo == Tipos.CLIP
        Medios.cargar(alcance, h.b.miniatura, item.thumbPath, 480)
        h.b.iconoClip.visibility = if (esClip) View.VISIBLE else View.GONE
        h.b.estado.text = if (item.rehaciendo) "rehaciendo…" else item.estado
        h.b.detalle.text = if (esClip) "Clip" else "Flyer · ${item.angulo ?: "sin ángulo"}"
        h.b.btnRehacer.visibility = if (esClip) View.GONE else View.VISIBLE
        h.b.btnUsar.isEnabled = item.estado != Estados.LISTO
        h.b.btnUsar.setOnClickListener { alUsar(item) }
        h.b.btnRehacer.setOnClickListener { alRehacer(item) }
        h.b.btnDescartar.setOnClickListener { alDescartar(item) }
    }

    companion object {
        val DIF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(a: Item, b: Item) = a.id == b.id
            override fun areContentsTheSame(a: Item, b: Item) = a == b
        }
    }
}
