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
    private const val HISTORY_MAX_SIZE = 15

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
            // ─── Opción B: Filtro de historial reciente ───────────────────────────
            // Calculamos cuántas imágenes podemos recordar sin que se atasque
            // (nunca más de la mitad del total para que siempre haya candidatos)
            val historyCapacity = min(HISTORY_MAX_SIZE, totalImages / 2)

            // Cargar historial guardado
            val historyString = prefs.getString("${prefix}recent_history", "") ?: ""
            val recentHistory = if (historyString.isBlank()) {
                mutableListOf()
            } else {
                historyString.split(",").toMutableList()
            }

            // Intentar elegir una imagen que no esté en el historial (máx 30 intentos)
            var attempts = 0
            var candidate: Uri? = null
            while (attempts < 30) {
                val randomIndex = (0 until totalImages).random()
                val candidateUri = imageUris[randomIndex]
                if (historyCapacity <= 0 || !recentHistory.contains(candidateUri.toString())) {
                    candidate = candidateUri
                    break
                }
                attempts++
            }
            // Si tras 30 intentos no encontramos candidato (carpeta muy pequeña), elegimos al azar
            if (candidate == null) {
                candidate = imageUris[(0 until totalImages).random()]
                Log.w(TAG, "Historial lleno y sin candidatos nuevos tras $attempts intentos. Eligiendo al azar.")
            }
            selectedUri = candidate

            // Actualizar historial: agregar el nuevo y recortar a la capacidad
            if (historyCapacity > 0) {
                recentHistory.add(selectedUri.toString())
                while (recentHistory.size > historyCapacity) {
                    recentHistory.removeAt(0)
                }
                prefs.edit().putString("${prefix}recent_history", recentHistory.joinToString(",")).apply()
            }

            Log.d(TAG, "Modo aleatorio: seleccionada (intentos=$attempts) | historial=${recentHistory.size}/$historyCapacity | total=$totalImages")
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
        val crop = prefs.getBoolean("${prefix}crop", true)

        // Obtener tamaño de la pantalla
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        // Para modo relleno sin recorte cargamos a mayor resolución (imagen más ancha)
        val reqWidth = if (fitMode == "fill" && !crop) screenWidth * 3 else screenWidth

        Log.d(TAG, "Procesando imagen: URI=$selectedUri | Pantalla=${screenWidth}x${screenHeight} | Fit=$fitMode | Crop=$crop | Brillo=$brightness%")

        // Decodificar la imagen optimizada para el tamaño de la pantalla
        val originalBitmap = decodeSampledBitmap(context, selectedUri, reqWidth, screenHeight)
        if (originalBitmap == null) {
            Log.e(TAG, "No se pudo decodificar la imagen: $selectedUri")
            return false
        }

        // Procesar la imagen (escalado y brillo)
        val processedBitmap = processBitmap(originalBitmap, screenWidth, screenHeight, fitMode, brightness, crop)
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
     * Escala y aplica el brillo al bitmap en un canvas para maximizar el rendimiento de memoria.
     *
     * @param crop  Si false y fitMode == "fill", NO se recorta el ancho de la imagen.
     *              El bitmap resultante será más ancho que la pantalla, lo que permite
     *              el efecto de desplazamiento paralaje en launchers como Lawnchair.
     */
    fun processBitmap(
        original: Bitmap,
        screenWidth: Int,
        screenHeight: Int,
        fitMode: String,
        brightness: Int,
        crop: Boolean = true
    ): Bitmap? {
        return try {
            val srcWidth = original.width
            val srcHeight = original.height

            when (fitMode) {
                "stretch" -> {
                    // Estirar la imagen a pantalla completa sin importar la proporción
                    val processed = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(processed)
                    canvas.drawColor(Color.BLACK)
                    canvas.drawBitmap(original, Rect(0, 0, srcWidth, srcHeight), Rect(0, 0, screenWidth, screenHeight), null)
                    applyDim(canvas, screenWidth, screenHeight, brightness)
                    processed
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

                    val processed = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(processed)
                    canvas.drawColor(Color.BLACK)
                    canvas.drawBitmap(original, Rect(0, 0, srcWidth, srcHeight), Rect(left, top, left + newWidth, top + newHeight), null)
                    applyDim(canvas, screenWidth, screenHeight, brightness)
                    processed
                }
                else -> { // "fill"
                    if (crop) {
                        // ── Modo Relleno CON recorte (comportamiento original) ──
                        // Escala la imagen para que cubra toda la pantalla, recortando sobrantes.
                        val scaleX = screenWidth.toFloat() / srcWidth
                        val scaleY = screenHeight.toFloat() / srcHeight
                        val scale = max(scaleX, scaleY)

                        val newWidth = (srcWidth * scale).toInt()
                        val newHeight = (srcHeight * scale).toInt()
                        val left = (screenWidth - newWidth) / 2
                        val top = (screenHeight - newHeight) / 2

                        val processed = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(processed)
                        canvas.drawColor(Color.BLACK)
                        canvas.drawBitmap(original, Rect(0, 0, srcWidth, srcHeight), Rect(left, top, left + newWidth, top + newHeight), null)
                        applyDim(canvas, screenWidth, screenHeight, brightness)
                        processed
                    } else {
                        // ── Modo Relleno SIN recorte (paralaje / desplazamiento lateral) ──
                        // La imagen se escala para que su ALTO coincida exactamente con la pantalla.
                        // El ANCHO resultante se respeta sin recortar, dando una imagen más ancha
                        // que el launcher puede desplazar lateralmente (efecto paralaje).
                        val scale = screenHeight.toFloat() / srcHeight
                        val newWidth = max(screenWidth, (srcWidth * scale).toInt())
                        val newHeight = screenHeight  // siempre igual al alto de pantalla

                        val processed = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(processed)
                        canvas.drawColor(Color.BLACK)
                        // Dibujar desde la esquina superior-izquierda (sin centrar horizontalmente)
                        canvas.drawBitmap(original, Rect(0, 0, srcWidth, srcHeight), Rect(0, 0, newWidth, newHeight), null)
                        applyDim(canvas, newWidth, newHeight, brightness)
                        processed
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en procesamiento de bitmap: ${e.message}", e)
            null
        }
    }

    /**
     * Aplica una capa de oscurecimiento proporcional al brillo configurado.
     */
    private fun applyDim(canvas: Canvas, width: Int, height: Int, brightness: Int) {
        val dimAlpha = ((100 - brightness) * 255 / 100).coerceIn(0, 255)
        if (dimAlpha > 0) {
            val paint = Paint().apply {
                color = Color.BLACK
                alpha = dimAlpha
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }
}
