package com.l0b091.pulso.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.l0b091.pulso.R
import com.l0b091.pulso.alarm.Alarmas
import com.l0b091.pulso.data.Ajustes
import com.l0b091.pulso.data.Consola
import com.l0b091.pulso.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var ajustes: Ajustes
    private var cambiandoSeleccion = false

    private val pedirNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { pedirAlarmasExactas() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        ajustes = Ajustes.get(this)

        b.navInferior.setOnItemSelectedListener { item ->
            if (!cambiandoSeleccion) navegar(item.itemId)
            true
        }
        b.navInferior.setOnItemReselectedListener { item -> navegar(item.itemId) }

        if (savedInstanceState == null) {
            val envio = envioDelIntent(intent)
            if (envio != null) {
                ajustes.ultimoDiaAbierto = Consola.get(this).hoy()
                abrirEnviar(envio)
            } else {
                abrirEnviar(null)
            }
        }
        pedirPermisosPrimeraVez()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        envioDelIntent(intent)?.let { abrirEnviar(it) }
    }

    override fun onResume() {
        super.onResume()
        val hoy = Consola.get(this).hoy()
        if (ajustes.ultimoDiaAbierto != hoy) {
            ajustes.ultimoDiaAbierto = hoy
            abrirDia()
        }
    }

    private fun envioDelIntent(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_ENVIO, -1L)?.takeIf { it > 0 }

    private fun navegar(id: Int) {
        when (id) {
            R.id.nav_enviar -> mostrar(EnviarFragment.nuevo(null), getString(R.string.nav_enviar))
            R.id.nav_galeria -> mostrar(GaleriaFragment(), getString(R.string.nav_galeria))
            R.id.nav_cuentas -> mostrar(CuentasFragment(), getString(R.string.nav_cuentas))
        }
    }

    private fun seleccionar(id: Int) {
        cambiandoSeleccion = true
        b.navInferior.selectedItemId = id
        cambiandoSeleccion = false
    }

    private fun mostrar(f: Fragment, titulo: String) {
        supportActionBar?.title = titulo
        supportFragmentManager.beginTransaction().replace(R.id.contenedor, f).commit()
    }

    fun abrirEnviar(envioId: Long?) {
        seleccionar(R.id.nav_enviar)
        mostrar(EnviarFragment.nuevo(envioId), getString(R.string.nav_enviar))
    }

    fun abrirDia() {
        seleccionar(R.id.nav_enviar)
        mostrar(DiaFragment(), getString(R.string.menu_dia))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_principal, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_dia -> { abrirDia(); true }
        R.id.menu_ajustes -> { startActivity(Intent(this, AjustesActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }

    // ---------- Permisos: se piden la primera vez que se abre la app ----------

    private fun pedirPermisosPrimeraVez() {
        if (ajustes.permisosPedidos) return
        ajustes.permisosPedidos = true
        if (Build.VERSION.SDK_INT >= 33) {
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            pedirAlarmasExactas()
        }
    }

    private fun pedirAlarmasExactas() {
        if (Build.VERSION.SDK_INT < 31 || Alarmas.puedeExactas(this)) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Avisos a la hora justa")
            .setMessage("Para avisarte a la hora exacta de cada posteo, Android necesita que permitas alarmas exactas. Sin eso, el aviso puede llegar unos minutos tarde.")
            .setPositiveButton("Permitir") { _, _ -> abrirPermisoAlarmas(this) }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    companion object {
        const val EXTRA_ENVIO = "envio_id"

        fun abrirPermisoAlarmas(activity: AppCompatActivity) {
            if (Build.VERSION.SDK_INT < 31) return
            try {
                activity.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + activity.packageName))
                )
            } catch (_: Exception) {
            }
        }
    }
}
