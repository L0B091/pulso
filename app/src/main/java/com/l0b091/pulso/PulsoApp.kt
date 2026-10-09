package com.l0b091.pulso

import android.app.Application
import com.l0b091.pulso.alarm.Avisos
import com.l0b091.pulso.data.Consola
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PulsoApp : Application() {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Avisos.crearCanal(this)
        alcance.launch { Consola.get(this@PulsoApp).limpiar() }
    }
}
