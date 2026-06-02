package com.wallpaper.nativeapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WallpaperWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "WallpaperWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Ejecutando latido de WallpaperWorker en segundo plano...")

        val prefs: SharedPreferences = applicationContext.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        var hasUpdated = false

        // 1. Verificar Pantalla de Inicio
        val homeEnabled = prefs.getBoolean("home_enabled", false)
        val homeTrigger = prefs.getString("home_trigger", "time")
        val homePaused = prefs.getBoolean("home_paused", false)
        if (homeEnabled && homeTrigger == "time" && !homePaused) {
            val homeIntervalMin = prefs.getInt("home_interval", 60) // por defecto 60 min
            val intervalMs = homeIntervalMin * 60 * 1000L
            val lastChange = prefs.getLong("home_last_change", 0L)
            
            // Si es la primera ejecución (lastChange == 0) o si ya pasó el intervalo (con 30s de margen por tolerancia de WorkManager)
            if (lastChange == 0L || (currentTime - lastChange >= intervalMs - 30000L)) {
                Log.d(TAG, "Tiempo cumplido para fondo de INICIO ($homeIntervalMin min). Cambiando...")
                val success = WallpaperHelper.changeWallpaper(applicationContext, isLockScreen = false)
                if (success || lastChange == 0L) {
                    prefs.edit().putLong("home_last_change", currentTime).apply()
                    hasUpdated = true
                }
            }
        }

        // 2. Verificar Pantalla de Bloqueo
        val lockEnabled = prefs.getBoolean("lock_enabled", false)
        val lockTrigger = prefs.getString("lock_trigger", "time")
        val lockPaused = prefs.getBoolean("lock_paused", false)
        if (lockEnabled && lockTrigger == "time" && !lockPaused) {
            val lockIntervalMin = prefs.getInt("lock_interval", 60) // por defecto 60 min
            val intervalMs = lockIntervalMin * 60 * 1000L
            val lastChange = prefs.getLong("lock_last_change", 0L)

            // Si es la primera ejecución o ya pasó el intervalo
            if (lastChange == 0L || (currentTime - lastChange >= intervalMs - 30000L)) {
                Log.d(TAG, "Tiempo cumplido para fondo de BLOQUEO ($lockIntervalMin min). Cambiando...")
                val success = WallpaperHelper.changeWallpaper(applicationContext, isLockScreen = true)
                if (success || lastChange == 0L) {
                    prefs.edit().putLong("lock_last_change", currentTime).apply()
                    hasUpdated = true
                }
            }
        }

        Log.d(TAG, "WallpaperWorker completado. Cambios realizados: $hasUpdated")
        Result.success()
    }
}
