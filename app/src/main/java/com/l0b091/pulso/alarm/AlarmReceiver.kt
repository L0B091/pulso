package com.l0b091.pulso.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.l0b091.pulso.data.Consola
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val envioId = intent.getLongExtra(Alarmas.EXTRA_ENVIO, -1L)
        val intento = intent.getIntExtra(Alarmas.EXTRA_INTENTO, 0)
        if (envioId <= 0) return
        val resultado = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Consola.get(context).alDispararse(envioId, intento)
            } finally {
                resultado.finish()
            }
        }
    }
}
