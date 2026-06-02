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

class WallpaperWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "WallpaperWidget"
        const val ACTION_WIDGET_TOGGLE_PAUSE = "com.wallpaper.nativeapp.ACTION_WIDGET_TOGGLE_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.wallpaper.nativeapp.ACTION_WIDGET_NEXT"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate llamado para actualizar widgets: ${appWidgetIds.size} activos.")
        val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
        val homePaused = prefs.getBoolean("home_paused", false)

        for (appWidgetId in appWidgetIds) {
            updateWidgetUi(context, appWidgetManager, appWidgetId, homePaused)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive del widget recibió acción: $action")

        val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

        if (action == ACTION_WIDGET_TOGGLE_PAUSE) {
            val currentPaused = prefs.getBoolean("home_paused", false)
            val newPaused = !currentPaused

            // Guardar nuevo estado
            prefs.edit().putBoolean("home_paused", newPaused).apply()
            Log.d(TAG, "Widget cambió estado de pausa a: $newPaused")

            // Mostrar retroalimentación rápida
            val message = if (newPaused) "Rotación de inicio pausada" else "Rotación de inicio reanudada"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

            // Forzar actualización inmediata en todos los widgets
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, WallpaperWidget::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (id in allWidgetIds) {
                updateWidgetUi(context, appWidgetManager, id, newPaused)
            }
        } 
        else if (action == ACTION_WIDGET_NEXT) {
            Log.d(TAG, "Widget solicitó cambiar a la siguiente imagen.")
            
            // Usar goAsync() para realizar la operación pesada en un hilo secundario sin colgar el receiver
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val success = WallpaperHelper.changeWallpaper(context, isLockScreen = false)
                    // Mostrar toast en el hilo principal si falla
                    if (!success) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "No se pudo cambiar el fondo. Configura una carpeta.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en hilo de cambio rápido desde widget: ${e.message}")
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

        // 1. Alternar ícono de play/pause dinámicamente
        val playPauseIcon = if (homePaused) {
            R.drawable.ic_widget_play  // Si está en pausa, mostramos el botón de Play para "reanudar"
        } else {
            R.drawable.ic_widget_pause // Si está activo, mostramos el botón de Pause para "pausar"
        }
        views.setImageViewResource(R.id.btn_widget_play_pause, playPauseIcon)

        // 2. Vincular PendingIntent para Play/Pause
        val pauseIntent = Intent(context, WallpaperWidget::class.java).apply {
            action = ACTION_WIDGET_TOGGLE_PAUSE
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId, // request code único por widget para evitar colisiones
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_play_pause, pausePendingIntent)

        // 3. Vincular PendingIntent para Next
        val nextIntent = Intent(context, WallpaperWidget::class.java).apply {
            action = ACTION_WIDGET_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 10000, // offset
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_next, nextPendingIntent)

        // 4. Aplicar cambios en el widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
