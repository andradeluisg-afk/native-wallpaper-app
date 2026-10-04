package com.wallpaper.nativeapp

import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder

class VideoLiveWallpaperService : WallpaperService() {

    private val TAG = "VideoLiveWallpaper"

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    inner class VideoEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private var mediaPlayer: MediaPlayer? = null
        private var currentUri: Uri? = null
        private var isVideo = false
        private var isVisibleState = false
        private lateinit var prefs: SharedPreferences

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(this)
            loadAndPlayActiveWallpaper()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisibleState = visible
            Log.d(TAG, "Visibilidad cambiada: $visible | isVideo=$isVideo")
            if (visible) {
                if (isVideo) {
                    mediaPlayer?.start()
                } else {
                    drawStaticImage()
                }
            } else {
                if (isVideo) {
                    mediaPlayer?.pause()
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            Log.d(TAG, "Superficie creada")
            if (isVideo) {
                mediaPlayer?.setDisplay(holder)
            } else {
                drawStaticImage()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            Log.d(TAG, "Superficie destruida")
            releaseMediaPlayer()
        }

        override fun onDestroy() {
            super.onDestroy()
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            releaseMediaPlayer()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == "home_active_uri" || key == "home_active_is_video" || key == "home_brightness") {
                Log.d(TAG, "Preferencia cambiada ($key). Actualizando fondo de video/imagen...")
                Handler(Looper.getMainLooper()).post {
                    loadAndPlayActiveWallpaper()
                }
            }
        }

        private fun loadAndPlayActiveWallpaper() {
            val uriStr = prefs.getString("home_active_uri", null)
            if (uriStr.isNullOrEmpty()) {
                drawDefaultBackground()
                return
            }

            val uri = Uri.parse(uriStr)
            val videoFlag = prefs.getBoolean("home_active_is_video", false)

            currentUri = uri
            isVideo = videoFlag

            if (isVideo) {
                playVideo(uri)
            } else {
                releaseMediaPlayer()
                drawStaticImage()
            }
        }

        private fun playVideo(uri: Uri) {
            releaseMediaPlayer()
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, uri)
                    setDisplay(surfaceHolder)
                    isLooping = true
                    setVolume(0f, 0f) // Silencioso para fondos de pantalla
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        Log.d(TAG, "Video preparado y listo para reproducir")
                        if (isVisibleState) {
                            mp.start()
                        }
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "Error en MediaPlayer: what=$what extra=$extra")
                        releaseMediaPlayer()
                        drawStaticImage()
                        true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando reproducción de video: ${e.message}", e)
                releaseMediaPlayer()
                drawStaticImage()
            }
        }

        private fun drawStaticImage() {
            val uri = currentUri ?: return
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return

            try {
                val canvas = holder.lockCanvas() ?: return
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        val screenWidth = canvas.width
                        val screenHeight = canvas.height
                        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                        val dstRect = Rect(0, 0, screenWidth, screenHeight)

                        val paint = Paint().apply {
                            isFilterBitmap = true
                            isAntiAlias = true
                        }
                        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
                    } else {
                        canvas.drawColor(Color.BLACK)
                    }
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error dibujando imagen estática en lienzo: ${e.message}")
            }
        }

        private fun drawDefaultBackground() {
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return
            try {
                val canvas = holder.lockCanvas() ?: return
                canvas.drawColor(Color.BLACK)
                holder.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                Log.e(TAG, "Error dibujando fondo por defecto: ${e.message}")
            }
        }

        private fun releaseMediaPlayer() {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.reset()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error al liberar MediaPlayer: ${e.message}")
            }
        }
    }
}
