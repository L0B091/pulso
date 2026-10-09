package com.l0b091.pulso.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.l0b091.pulso.R
import com.l0b091.pulso.ui.MainActivity

/** Notificaciones nativas de "es hora de publicar". */
object Avisos {
    const val CANAL = "envios"

    fun crearCanal(context: Context) {
        val canal = NotificationChannel(CANAL, "Hora de publicar", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisa cuando un posteo agendado está listo para enviar."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    fun puedeNotificar(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun mostrar(context: Context, envioId: Long, titulo: String, texto: String) {
        if (!puedeNotificar(context)) return
        val abrir = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_ENVIO, envioId)
        }
        val pi = PendingIntent.getActivity(
            context, envioId.toInt(), abrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFFFF7A1A.toInt())
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(envioId.toInt(), n)
        } catch (_: SecurityException) {
        }
    }

    fun cancelar(context: Context, envioId: Long) {
        NotificationManagerCompat.from(context).cancel(envioId.toInt())
    }
}
