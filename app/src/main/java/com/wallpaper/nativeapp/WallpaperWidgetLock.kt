package com.wallpaper.nativeapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WallpaperWidgetLock : AppWidgetProvider() {

    companion object {
        private const val TAG = "WallpaperWidgetLock"
        const val ACTION_WIDGET_LOCK_TOGGLE_PAUSE = "com.wallpaper.nativeapp.ACTION_WIDGET_LOCK_TOGGLE_PAUSE"
        const val ACTION_WIDGET_LOCK_NEXT = "com.wallpaper.nativeapp.ACTION_WIDGET_LOCK_NEXT"
        const val ACTION_WIDGET_LOCK_BLACKLIST = "com.wallpaper.nativeapp.ACTION_WIDGET_LOCK_BLACKLIST"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate para Bloqueo: ${appWidgetIds.size} widgets activos.")
        val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
        val lockPaused = prefs.getBoolean("lock_paused", false)

        for (appWidgetId in appWidgetIds) {
            updateWidgetUi(context, appWidgetManager, appWidgetId, lockPaused)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive de Bloqueo recibió acción: $action")

        val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

        if (action == ACTION_WIDGET_LOCK_TOGGLE_PAUSE) {
            val currentPaused = prefs.getBoolean("lock_paused", false)
            val newPaused = !currentPaused

            prefs.edit().putBoolean("lock_paused", newPaused).apply()
            Log.d(TAG, "Bloqueo cambió estado de pausa a: $newPaused")

            val message = if (newPaused) "Rotación de bloqueo pausada" else "Rotación de bloqueo reanudada"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, WallpaperWidgetLock::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (id in allWidgetIds) {
                updateWidgetUi(context, appWidgetManager, id, newPaused)
            }
        } 
        else if (action == ACTION_WIDGET_LOCK_BLACKLIST) {
            Log.d(TAG, "Widget de Bloqueo solicitó añadir el fondo actual a la lista negra.")
            val currentUri = prefs.getString("lock_current_uri", null)
            if (!currentUri.isNullOrEmpty()) {
                val blacklist = prefs.getStringSet("lock_blacklist", emptySet())?.toMutableSet() ?: mutableSetOf()
                blacklist.add(currentUri)
                prefs.edit().putStringSet("lock_blacklist", blacklist).apply()
                Log.d(TAG, "Fondo de bloqueo añadido a la lista negra: $currentUri")
                Toast.makeText(context, "Imagen eliminada y añadida a la lista negra", Toast.LENGTH_SHORT).show()
            }
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val success = WallpaperHelper.changeWallpaper(context, isLockScreen = true)
                    if (!success) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "No hay más imágenes disponibles.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al cambiar tras lista negra: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
        else if (action == ACTION_WIDGET_LOCK_NEXT) {
            Log.d(TAG, "Widget de Bloqueo solicitó cambiar a la siguiente imagen.")
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val success = WallpaperHelper.changeWallpaper(context, isLockScreen = true)
                    if (!success) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "No se pudo cambiar el fondo de bloqueo. Configura una carpeta.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error cambiando fondo de bloqueo desde widget: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        } 
        else {
            super.onReceive(context, intent)
        }
    }

    private fun updateWidgetUi(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        lockPaused: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_home)

        // Alternar icono de play/pause
        val playPauseIcon = if (lockPaused) R.drawable.ic_widget_play else R.drawable.ic_widget_pause
        views.setImageViewResource(R.id.btn_widget_play_pause, playPauseIcon)

        // PendingIntent para Play/Pause
        val pauseIntent = Intent(context, WallpaperWidgetLock::class.java).apply {
            action = ACTION_WIDGET_LOCK_TOGGLE_PAUSE
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_play_pause, pausePendingIntent)

        // PendingIntent para Lista Negra (Dedo Abajo)
        val blacklistIntent = Intent(context, WallpaperWidgetLock::class.java).apply {
            action = ACTION_WIDGET_LOCK_BLACKLIST
        }
        val blacklistPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 50000, // Request code 50000 to keep it unique
            blacklistIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_blacklist, blacklistPendingIntent)

        // PendingIntent para Siguiente
        val nextIntent = Intent(context, WallpaperWidgetLock::class.java).apply {
            action = ACTION_WIDGET_LOCK_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 30000,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_next, nextPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
