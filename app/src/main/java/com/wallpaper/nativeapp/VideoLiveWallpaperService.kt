package com.wallpaper.nativeapp

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
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
        private var activePfd: ParcelFileDescriptor? = null
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
                    if (mediaPlayer == null && currentUri != null) {
                        playVideo(currentUri!!)
                    } else {
                        mediaPlayer?.start()
                    }
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
            if (isVideo && currentUri != null) {
                playVideo(currentUri!!)
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
            if (key == "home_active_uri" || key == "home_active_is_video" || key == "home_brightness" ||
                key == "lock_active_uri" || key == "lock_active_is_video" || key == "lock_brightness") {
                Log.d(TAG, "Preferencia cambiada ($key). Actualizando fondo de video/imagen...")
                Handler(Looper.getMainLooper()).post {
                    loadAndPlayActiveWallpaper()
                }
            }
        }

        private fun loadAndPlayActiveWallpaper() {
            var uriStr = prefs.getString("home_active_uri", null)
            var videoFlag = prefs.getBoolean("home_active_is_video", false)

            if (uriStr.isNullOrEmpty()) {
                uriStr = prefs.getString("lock_active_uri", null)
                videoFlag = prefs.getBoolean("lock_active_is_video", false)
            }

            if (uriStr.isNullOrEmpty()) {
                drawDefaultBackground()
                return
            }

            val uri = Uri.parse(uriStr)
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
                activePfd = applicationContext.contentResolver.openFileDescriptor(uri, "r")
                if (activePfd == null) {
                    Log.e(TAG, "No se pudo abrir ParcelFileDescriptor para URI: $uri")
                    drawStaticImage()
                    return
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(activePfd!!.fileDescriptor)
                    setDisplay(surfaceHolder)
                    isLooping = true
                    setVolume(0f, 0f) // Silencioso para fondos de pantalla
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    }

                    prepareAsync()
                    setOnPreparedListener { mp ->
                        Log.d(TAG, "Video MP4 preparado y listo para reproducir vía FileDescriptor")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        }
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
                Log.e(TAG, "Error iniciando reproducción de video MP4: ${e.message}", e)
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
                    val rawBitmap: Bitmap? = if (isVideo) {
                        val retriever = MediaMetadataRetriever()
                        var pfd: ParcelFileDescriptor? = null
                        try {
                            pfd = contentResolver.openFileDescriptor(uri, "r")
                            if (pfd != null) {
                                retriever.setDataSource(pfd.fileDescriptor)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, canvas.width, canvas.height)
                                } else {
                                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                }
                            } else null
                        } catch (e: Exception) {
                            null
                        } finally {
                            try { pfd?.close() } catch (ignored: Exception) {}
                            try { retriever.release() } catch (ignored: Exception) {}
                        }
                    } else {
                        val inputStream = contentResolver.openInputStream(uri)
                        val b = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        b
                    }

                    if (rawBitmap != null) {
                        val screenWidth = canvas.width
                        val screenHeight = canvas.height
                        
                        val fitMode = prefs.getString("home_fit_mode", "fill") ?: "fill"
                        val brightness = prefs.getInt("home_brightness", 100)
                        val crop = prefs.getBoolean("home_crop", true)
                        val adaptiveDim = prefs.getBoolean("home_adaptive_dim", false)
                        
                        val processed = WallpaperHelper.processBitmap(rawBitmap, screenWidth, screenHeight, fitMode, brightness, crop, adaptiveDim)
                        rawBitmap.recycle()

                        if (processed != null) {
                            val paint = Paint().apply {
                                isFilterBitmap = true
                                isAntiAlias = true
                            }
                            canvas.drawBitmap(processed, 0f, 0f, paint)
                            processed.recycle()
                        } else {
                            canvas.drawColor(Color.BLACK)
                        }
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
            try {
                activePfd?.close()
                activePfd = null
            } catch (e: Exception) {
                Log.e(TAG, "Error al cerrar ParcelFileDescriptor: ${e.message}")
            }
        }
    }
}
