package com.wallpaper.nativeapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WallpaperService : Service() {

    private val TAG = "WallpaperService"
    private val CHANNEL_ID = "wallpaper_service_channel"
    private val NOTIFICATION_ID = 202

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var isReceiverRegistered = false

    // Receptor dinámico para detectar cambios de pantalla
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "Broadcast de pantalla recibido: $action")

            serviceScope.launch {
                val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

                if (action == Intent.ACTION_SCREEN_OFF) {
                    // La pantalla se apagó. Momento ideal para cambiar el fondo de bloqueo secuencial
                    // para que ya esté listo cuando el usuario vuelva a encender el celular.
                    val lockEnabled = prefs.getBoolean("lock_enabled", false)
                    val lockTrigger = prefs.getString("lock_trigger", "time")
                    val lockPaused = prefs.getBoolean("lock_paused", false)
                    if (lockEnabled && lockTrigger == "unlock" && !lockPaused) {
                        Log.d(TAG, "Pantalla apagada: Cambiando fondo de BLOQUEO en segundo plano.")
                        WallpaperHelper.changeWallpaper(this@WallpaperService, isLockScreen = true)
                    }
                } else if (action == Intent.ACTION_USER_PRESENT) {
                    // El usuario desbloqueó el celular. Cambiamos el fondo de inicio.
                    val homeEnabled = prefs.getBoolean("home_enabled", false)
                    val homeTrigger = prefs.getString("home_trigger", "time")
                    val homePaused = prefs.getBoolean("home_paused", false)
                    if (homeEnabled && homeTrigger == "unlock" && !homePaused) {
                        Log.d(TAG, "Celular desbloqueado: Cambiando fondo de INICIO.")
                        WallpaperHelper.changeWallpaper(this@WallpaperService, isLockScreen = false)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creando servicio de fondos de pantalla...")
        createNotificationChannel()
        startForegroundService()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio iniciado o reiniciado.")
        // El servicio continuará ejecutándose hasta que se detenga explícitamente
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destruyendo servicio de fondos de pantalla...")
        unregisterScreenReceiver()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WallFlow Activo")
            .setContentText("Escuchando eventos de bloqueo para cambiar fondos...")
            .setSmallIcon(android.R.drawable.ic_menu_gallery) // Ícono nativo ligero
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Canal de baja prioridad para no molestar
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Servicio puesto en primer plano correctamente.")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando startForeground: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Fondos de Pantalla",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activa la detección de bloqueo/desbloqueo de pantalla"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun registerScreenReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, filter)
            isReceiverRegistered = true
            Log.d(TAG, "Receptor de pantalla registrado dinámicamente.")
        }
    }

    private fun unregisterScreenReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "Receptor de pantalla desregistrado.")
            } catch (e: Exception) {
                Log.e(TAG, "Error al desregistrar receptor de pantalla: ${e.message}")
            }
        }
    }
}
