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

class WallpaperWidgetHome : AppWidgetProvider() {

    companion object {
        private const val TAG = "WallpaperWidgetHome"
        const val ACTION_WIDGET_HOME_TOGGLE_PAUSE = "com.wallpaper.nativeapp.ACTION_WIDGET_HOME_TOGGLE_PAUSE"
        const val ACTION_WIDGET_HOME_NEXT = "com.wallpaper.nativeapp.ACTION_WIDGET_HOME_NEXT"
        const val ACTION_WIDGET_HOME_BLACKLIST = "com.wallpaper.nativeapp.ACTION_WIDGET_HOME_BLACKLIST"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate para Inicio: ${appWidgetIds.size} widgets activos.")
        val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
        val homePaused = prefs.getBoolean("home_paused", false)

        for (appWidgetId in appWidgetIds) {
            updateWidgetUi(context, appWidgetManager, appWidgetId, homePaused)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive de Inicio recibió acción: $action")

        val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

        if (action == ACTION_WIDGET_HOME_TOGGLE_PAUSE) {
            val currentPaused = prefs.getBoolean("home_paused", false)
            val newPaused = !currentPaused

            prefs.edit().putBoolean("home_paused", newPaused).apply()
            Log.d(TAG, "Inicio cambió estado de pausa a: $newPaused")

            val message = if (newPaused) "Rotación de inicio pausada" else "Rotación de inicio reanudada"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, WallpaperWidgetHome::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (id in allWidgetIds) {
                updateWidgetUi(context, appWidgetManager, id, newPaused)
            }
        } 
        else if (action == ACTION_WIDGET_HOME_BLACKLIST) {
            Log.d(TAG, "Widget de Inicio solicitó añadir el fondo actual a la lista negra.")
            val currentUri = prefs.getString("home_current_uri", null)
            if (!currentUri.isNullOrEmpty()) {
                val blacklist = prefs.getStringSet("home_blacklist", emptySet())?.toMutableSet() ?: mutableSetOf()
                blacklist.add(currentUri)
                prefs.edit().putStringSet("home_blacklist", blacklist).apply()
                Log.d(TAG, "Fondo de inicio añadido a la lista negra: $currentUri")
                Toast.makeText(context, "Imagen eliminada y añadida a la lista negra", Toast.LENGTH_SHORT).show()
            }
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val success = WallpaperHelper.changeWallpaper(context, isLockScreen = false)
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
        else if (action == ACTION_WIDGET_HOME_NEXT) {
            Log.d(TAG, "Widget de Inicio solicitó cambiar a la siguiente imagen.")
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val success = WallpaperHelper.changeWallpaper(context, isLockScreen = false)
                    if (!success) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "No se pudo cambiar el fondo de inicio. Configura una carpeta.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error cambiando fondo de inicio desde widget: ${e.message}")
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
        homePaused: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_home)

        // Alternar icono de play/pause
        val playPauseIcon = if (homePaused) R.drawable.ic_widget_play else R.drawable.ic_widget_pause
        views.setImageViewResource(R.id.btn_widget_play_pause, playPauseIcon)

        // PendindIntent para Play/Pause
        val pauseIntent = Intent(context, WallpaperWidgetHome::class.java).apply {
            action = ACTION_WIDGET_HOME_TOGGLE_PAUSE
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_play_pause, pausePendingIntent)

        // PendingIntent para Lista Negra (Dedo Abajo)
        val blacklistIntent = Intent(context, WallpaperWidgetHome::class.java).apply {
            action = ACTION_WIDGET_HOME_BLACKLIST
        }
        val blacklistPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 40000,
            blacklistIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_blacklist, blacklistPendingIntent)

        // PendingIntent para Siguiente
        val nextIntent = Intent(context, WallpaperWidgetHome::class.java).apply {
            action = ACTION_WIDGET_HOME_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 20000,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_next, nextPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
