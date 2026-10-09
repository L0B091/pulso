package com.l0b091.pulso.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.l0b091.pulso.data.Consola
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Al reiniciar el teléfono, Android borra las alarmas: se vuelven a programar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val resultado = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Consola.get(context).reprogramarTodo()
            } finally {
                resultado.finish()
            }
        }
    }
}
