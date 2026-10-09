package com.l0b091.pulso.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.l0b091.pulso.work.SyncWorker
import java.util.concurrent.TimeUnit

/** Alarmas exactas para cada posteo agendado, más un sync antes de la hora. */
object Alarmas {
    const val ACCION = "com.l0b091.pulso.ALARMA"
    const val EXTRA_ENVIO = "envio"
    const val EXTRA_INTENTO = "intento"
    const val REINTENTO_MS = 10 * 60 * 1000L
    private const val SYNC_ANTES_MS = 15 * 60 * 1000L

    fun puedeExactas(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun pendiente(context: Context, envioId: Long, intento: Int, flags: Int): PendingIntent? {
        val i = Intent(context, AlarmReceiver::class.java).apply {
            action = ACCION
            putExtra(EXTRA_ENVIO, envioId)
            putExtra(EXTRA_INTENTO, intento)
        }
        return PendingIntent.getBroadcast(context, (envioId * 4 + intento).toInt(), i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun programar(context: Context, envioId: Long, intento: Int, cuandoMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendiente(context, envioId, intento, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (puedeExactas(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuandoMs, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuandoMs, pi)
        }
    }

    fun cancelar(context: Context, envioId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        for (intento in 0..2) {
            pendiente(context, envioId, intento, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        }
        WorkManager.getInstance(context).cancelUniqueWork("sync-$envioId")
    }

    fun programarSync(context: Context, envioId: Long, cuandoMs: Long) {
        val demora = (cuandoMs - SYNC_ANTES_MS - System.currentTimeMillis()).coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(demora, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync-$envioId", ExistingWorkPolicy.REPLACE, req)
    }

    fun syncAhora(context: Context) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync-manual", ExistingWorkPolicy.KEEP, req)
    }
}
