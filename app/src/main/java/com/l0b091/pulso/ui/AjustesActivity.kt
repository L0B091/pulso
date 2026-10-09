package com.l0b091.pulso.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.l0b091.pulso.alarm.Alarmas
import com.l0b091.pulso.alarm.Avisos
import com.l0b091.pulso.data.Ajustes
import com.l0b091.pulso.data.Consola
import com.l0b091.pulso.databinding.ActivityAjustesBinding
import kotlinx.coroutines.launch

class AjustesActivity : AppCompatActivity() {
    private lateinit var b: ActivityAjustesBinding
    private lateinit var ajustes: Ajustes
    private lateinit var consola: Consola

    private val pedirNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { pintarPermisos() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAjustesBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        b.toolbar.setNavigationOnClickListener { finish() }
        ajustes = Ajustes.get(this)
        consola = Consola.get(this)

        b.enlaceWhatsapp.setText(ajustes.enlaceWhatsapp)
        b.enlaceInstagram.setText(ajustes.enlaceInstagram)
        b.enlaceFacebook.setText(ajustes.enlaceFacebook)
        b.token.setText(ajustes.token)
        b.repo.setText(ajustes.repo)
        b.rama.setText(ajustes.rama)
        b.avisoUrl.setText(ajustes.avisoUrl)
        b.avisoClave.setText(ajustes.avisoClave)
        b.avisoHeader.setText(ajustes.avisoHeader)
        b.ultimoSync.text = ajustes.ultimoSync.ifBlank { "Todavía no se sincronizó." }

        b.btnGuardar.setOnClickListener { guardar(true) }
        b.btnSincronizar.setOnClickListener { sincronizar() }
        b.btnPrueba.setOnClickListener { cargarPrueba() }
        b.btnPermisos.setOnClickListener { revisarPermisos() }
    }

    override fun onResume() {
        super.onResume()
        pintarPermisos()
    }

    private fun txt(e: android.widget.EditText) = e.text?.toString()?.trim().orEmpty()

    private fun guardar(avisar: Boolean) {
        ajustes.enlaceWhatsapp = txt(b.enlaceWhatsapp)
        ajustes.enlaceInstagram = txt(b.enlaceInstagram)
        ajustes.enlaceFacebook = txt(b.enlaceFacebook)
        ajustes.token = txt(b.token)
        ajustes.repo = txt(b.repo)
        ajustes.rama = txt(b.rama)
        ajustes.avisoUrl = txt(b.avisoUrl)
        ajustes.avisoClave = txt(b.avisoClave)
        ajustes.avisoHeader = txt(b.avisoHeader)
        if (!avisar) return
        val faltan = consola.enlacesFaltantes()
        val msg = if (faltan.isEmpty()) "Guardado." else "Guardado. Falta: ${faltan.joinToString(", ")}. Sin eso no se arma ningún envío."
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun sincronizar() {
        guardar(false)
        b.ultimoSync.text = "Sincronizando…"
        lifecycleScope.launch {
            val msg = try {
                consola.sincronizar()
            } catch (e: Exception) {
                "No se pudo sincronizar: ${e.message}"
            }
            b.ultimoSync.text = msg
        }
    }

    private fun cargarPrueba() {
        lifecycleScope.launch {
            val item = consola.cargarPaquetePrueba()
            Toast.makeText(this@AjustesActivity, "Cargado ${item.id} (listo). Miralo en Galería.", Toast.LENGTH_LONG).show()
        }
    }

    private fun pintarPermisos() {
        val notif = if (Avisos.puedeNotificar(this)) "activadas" else "desactivadas"
        val exactas = if (Alarmas.puedeExactas(this)) "permitidas" else "no permitidas (el aviso puede atrasarse)"
        b.estadoPermisos.text = "Notificaciones: $notif\nAlarmas exactas: $exactas"
    }

    private fun revisarPermisos() {
        when {
            !Avisos.puedeNotificar(this) -> {
                if (Build.VERSION.SDK_INT >= 33) pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
                else startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
            !Alarmas.puedeExactas(this) -> MainActivity.abrirPermisoAlarmas(this)
            else -> Toast.makeText(this, "Todo en orden.", Toast.LENGTH_SHORT).show()
        }
    }
}
