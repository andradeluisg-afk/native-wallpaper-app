package com.wallpaper.nativeapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Reinicio o actualización detectada ($action). Restaurando tareas de WallFlow...")

            val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            val homeEnabled = prefs.getBoolean("home_enabled", false)
            val lockEnabled = prefs.getBoolean("lock_enabled", false)

            if (!homeEnabled && !lockEnabled) {
                Log.d(TAG, "Ambas pantallas desactivadas. No se reprograma nada.")
                return
            }

            val homeTrigger = prefs.getString("home_trigger", "time")
            val lockTrigger = prefs.getString("lock_trigger", "time")

            // 1. Reprogramar WorkManager si alguna pantalla usa actualización por tiempo
            if ((homeEnabled && homeTrigger == "time") || (lockEnabled && lockTrigger == "time")) {
                val periodicWorkRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(15, TimeUnit.MINUTES).build()
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    "wallpaper_periodic_work",
                    ExistingPeriodicWorkPolicy.UPDATE, // Actualizar con la nueva configuración
                    periodicWorkRequest
                )
                Log.d(TAG, "Tarea de WorkManager (latido de 15 min) reprogramada.")
            }

            // 2. Reiniciar el servicio en primer plano si alguna pantalla usa el disparador de desbloqueo
            if ((homeEnabled && homeTrigger == "unlock") || (lockEnabled && lockTrigger == "unlock")) {
                val serviceIntent = Intent(context, WallpaperService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d(TAG, "Servicio en primer plano WallpaperService iniciado tras reinicio.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error al iniciar WallpaperService: ${e.message}", e)
                }
            }
        }
    }
}
