package com.wallpaper.nativeapp

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

object WallpaperHelper {

    private const val TAG = "WallpaperHelper"

    /**
     * Obtiene la lista de URIs de imágenes dentro de una carpeta seleccionada por SAF
     */
    fun getImagesFromFolder(context: Context, folderUriString: String?): List<Uri> {
        if (folderUriString.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<Uri>()
        try {
            val treeUri = Uri.parse(folderUriString)
            val documentId = if (DocumentsContract.isDocumentUri(context, treeUri)) {
                DocumentsContract.getDocumentId(treeUri)
            } else {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    if (idIndex >= 0 && mimeIndex >= 0) {
                        val docId = cursor.getString(idIndex)
                        val mimeType = cursor.getString(mimeIndex)
                        // Filtrar solo imágenes (png, jpg, webp, etc.)
                        if (mimeType != null && mimeType.startsWith("image/")) {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            list.add(fileUri)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando carpeta: ${e.message}", e)
        }
        return list
    }

    /**
     * Cambia el fondo de pantalla (inicio o bloqueo) usando las preferencias del usuario
     */
    fun changeWallpaper(context: Context, isLockScreen: Boolean): Boolean {
        val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
        val prefix = if (isLockScreen) "lock_" else "home_"

        val enabled = prefs.getBoolean("${prefix}enabled", false)
        if (!enabled) {
            Log.d(TAG, "Cambiador desactivado para pantalla de " + if (isLockScreen) "bloqueo" else "inicio")
            return false
        }

        val folderUriString = prefs.getString("${prefix}folder_uri", null)
        if (folderUriString.isNullOrEmpty()) {
            Log.w(TAG, "No hay carpeta configurada para pantalla de " + if (isLockScreen) "bloqueo" else "inicio")
            return false
        }

        // Obtener todas las imágenes en esa carpeta
        val imageUris = getImagesFromFolder(context, folderUriString)
        if (imageUris.isEmpty()) {
            Log.w(TAG, "No se encontraron imágenes en la carpeta para " + if (isLockScreen) "bloqueo" else "inicio")
            return false
        }

        val order = prefs.getString("${prefix}order", "random") ?: "random"
        var selectedUri: Uri? = null
        val totalImages = imageUris.size

        if (order == "random") {
            val randomIndex = (0 until totalImages).random()
            selectedUri = imageUris[randomIndex]
            Log.d(TAG, "Modo aleatorio: seleccionada imagen index $randomIndex/$totalImages")
        } else {
            // Modo secuencial
            var currentIndex = prefs.getInt("${prefix}current_index", 0)
            if (currentIndex >= totalImages) {
                currentIndex = 0
            }
            selectedUri = imageUris[currentIndex]
            
            // Avanzar al siguiente y guardar en cache
            val nextIndex = (currentIndex + 1) % totalImages
            prefs.edit().putInt("${prefix}current_index", nextIndex).apply()
            Log.d(TAG, "Modo secuencial: index $currentIndex/$totalImages. Siguiente: $nextIndex")
        }

        if (selectedUri == null) return false

        // Configuración de estilo y atenuación
        val fitMode = prefs.getString("${prefix}fit_mode", "fill") ?: "fill"
        val brightness = prefs.getInt("${prefix}brightness", 100)

        // Obtener tamaño de la pantalla
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        Log.d(TAG, "Procesando imagen: URI=$selectedUri | Pantalla=${screenWidth}x${screenHeight} | Fit=$fitMode | Brillo=$brightness%")

        // Decodificar la imagen optimizada para el tamaño de la pantalla
        val originalBitmap = decodeSampledBitmap(context, selectedUri, screenWidth, screenHeight)
        if (originalBitmap == null) {
            Log.e(TAG, "No se pudo decodificar la imagen: $selectedUri")
            return false
        }

        // Procesar la imagen (escalado y brillo)
        val processedBitmap = processBitmap(originalBitmap, screenWidth, screenHeight, fitMode, brightness)
        originalBitmap.recycle() // Liberar memoria de la imagen decodificada original

        if (processedBitmap == null) {
            Log.e(TAG, "Error procesando el bitmap")
            return false
        }

        // Aplicar fondo
        val wallpaperManager = WallpaperManager.getInstance(context)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val flag = if (isLockScreen) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
                wallpaperManager.setBitmap(processedBitmap, null, true, flag)
                Log.d(TAG, "Fondo de " + (if (isLockScreen) "bloqueo" else "inicio") + " actualizado exitosamente.")
            } else {
                wallpaperManager.setBitmap(processedBitmap)
                Log.d(TAG, "Fondo de pantalla actualizado para ambas pantallas (API < 24).")
            }
            processedBitmap.recycle() // Liberar memoria del bitmap final
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al configurar fondo de pantalla en el sistema: ${e.message}", e)
            if (!processedBitmap.isRecycled) {
                processedBitmap.recycle()
            }
            false
        }
    }

    /**
     * Decodifica un bitmap reduciendo su tamaño según sea necesario para evitar OOM (Out Of Memory)
     */
    private fun decodeSampledBitmap(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            // Primera pasada para medir dimensiones originales
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Calcular inSampleSize (factor de compresión)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            // Segunda pasada para decodificar
            inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error al decodificar bitmap optimizado: ${e.message}")
            try { inputStream?.close() } catch (ignored: Exception) {}
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Mantener reduciendo mientras el alto y el ancho sean mayores al requerido
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Escala y aplica el brillo al bitmap en un canvas para maximizar el rendimiento de memoria
     */
    fun processBitmap(original: Bitmap, screenWidth: Int, screenHeight: Int, fitMode: String, brightness: Int): Bitmap? {
        return try {
            val srcWidth = original.width
            val srcHeight = original.height

            // Creamos un bitmap limpio con el tamaño exacto de la pantalla
            val processed = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(processed)
            canvas.drawColor(Color.BLACK) // Relleno por defecto (útil en modo Fit)

            when (fitMode) {
                "stretch" -> {
                    // Estirar la imagen a pantalla completa sin importar la proporción
                    val srcRect = Rect(0, 0, srcWidth, srcHeight)
                    val destRect = Rect(0, 0, screenWidth, screenHeight)
                    canvas.drawBitmap(original, srcRect, destRect, null)
                }
                "fit" -> {
                    // Zoom Out: Ajustar la imagen completa dentro de la pantalla, manteniendo la proporción
                    val scaleX = screenWidth.toFloat() / srcWidth
                    val scaleY = screenHeight.toFloat() / srcHeight
                    val scale = min(scaleX, scaleY)

                    val newWidth = (srcWidth * scale).toInt()
                    val newHeight = (srcHeight * scale).toInt()
                    val left = (screenWidth - newWidth) / 2
                    val top = (screenHeight - newHeight) / 2

                    val srcRect = Rect(0, 0, srcWidth, srcHeight)
                    val destRect = Rect(left, top, left + newWidth, top + newHeight)
                    canvas.drawBitmap(original, srcRect, destRect, null)
                }
                else -> { // "fill" (Rellena - Default)
                    // Zoom In: Cubrir la pantalla completa, manteniendo la proporción (recortando los sobrantes)
                    val scaleX = screenWidth.toFloat() / srcWidth
                    val scaleY = screenHeight.toFloat() / srcHeight
                    val scale = max(scaleX, scaleY)

                    val newWidth = (srcWidth * scale).toInt()
                    val newHeight = (srcHeight * scale).toInt()
                    val left = (screenWidth - newWidth) / 2
                    val top = (screenHeight - newHeight) / 2

                    val srcRect = Rect(0, 0, srcWidth, srcHeight)
                    val destRect = Rect(left, top, left + newWidth, top + newHeight)
                    canvas.drawBitmap(original, srcRect, destRect, null)
                }
            }

            // Aplicar oscurecimiento de brillo si es menor a 100%
            val dimAlpha = ((100 - brightness) * 255 / 100).coerceIn(0, 255)
            if (dimAlpha > 0) {
                val paint = Paint().apply {
                    color = Color.BLACK
                    alpha = dimAlpha
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), paint)
            }

            processed
        } catch (e: Exception) {
            Log.e(TAG, "Error en procesamiento de bitmap: ${e.message}", e)
            null
        }
    }
}
