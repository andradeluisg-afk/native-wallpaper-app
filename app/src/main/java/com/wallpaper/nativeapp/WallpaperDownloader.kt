package com.wallpaper.nativeapp

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object WallpaperDownloader {
    private const val TAG = "WallpaperDownloader"
    const val PREFS_NAME = "wallpaper_prefs"

    /**
     * Consulta la API de Wallhaven, obtiene las URL de las imágenes y las descarga
     * directamente en la carpeta seleccionada por el usuario vía SAF.
     */
    suspend fun doDownloadBatch(context: Context, force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("downloader_enabled", false)
        if (!enabled && !force) {
            Log.d(TAG, "Descargador inactivo. Saltando descarga periódica.")
            return@withContext 0
        }

        val folderUriStr = prefs.getString("downloader_folder_uri", null)
        if (folderUriStr.isNullOrEmpty()) {
            Log.w(TAG, "No se ha configurado una carpeta de descargas.")
            return@withContext 0
        }
        val folderUri = Uri.parse(folderUriStr)

        val keywordsStr = prefs.getString("downloader_keywords", "") ?: ""
        val keywords = keywordsStr.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        if (keywords.isEmpty()) {
            Log.w(TAG, "No hay palabras clave configuradas.")
            return@withContext 0
        }

        val batchSize = prefs.getInt("downloader_batch_size", 1).coerceIn(1, 4)
        var currentIndex = prefs.getInt("downloader_keyword_index", 0)

        // Si el índice de guardado quedó obsoleto por cambiar la lista, lo reseteamos
        if (currentIndex >= keywords.size) {
            currentIndex = 0
        }

        var successCount = 0
        val nextIndex = (currentIndex + batchSize) % keywords.size

        Log.d(TAG, "Iniciando descarga por lote. Palabras clave: $keywords | Tamaño lote: $batchSize | Índice actual: $currentIndex")

        for (i in 0 until batchSize) {
            val idx = (currentIndex + i) % keywords.size
            val query = keywords[idx]
            Log.d(TAG, "Descargando imagen para palabra clave: $query")
            val downloaded = downloadOneImage(context, query, folderUri)
            if (downloaded) {
                successCount++
            }
        }

        // Guardar el siguiente índice para la rotación de palabras clave
        prefs.edit().putInt("downloader_keyword_index", nextIndex).apply()
        Log.d(TAG, "Lote completado. Éxitos: $successCount/$batchSize. Siguiente índice: $nextIndex")
        successCount
    }

    private fun downloadOneImage(context: Context, query: String, folderUri: Uri): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            // Consulta Wallhaven con sorting=random para tener variedad en cada llamada
            val urlString = "https://wallhaven.cc/api/v1/search?q=${Uri.encode(query)}&categories=111&purity=100&sorting=random"
            val response = fetchUrl(urlString) ?: return false
            
            val json = JSONObject(response)
            val dataArray = json.optJSONArray("data") ?: return false
            if (dataArray.length() == 0) {
                Log.w(TAG, "No se encontraron imágenes en Wallhaven para la búsqueda: $query")
                return false
            }
            
            // Cargar historial de IDs descargados para evitar repetidos
            val downloadedIds = prefs.getStringSet("downloader_downloaded_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            
            // Buscar la primera imagen del listado que no haya sido descargada antes
            var selectedItem: JSONObject? = null
            for (j in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(j)
                val id = item.getString("id")
                if (!downloadedIds.contains(id)) {
                    selectedItem = item
                    break
                }
            }
            
            if (selectedItem == null) {
                Log.d(TAG, "Todas las imágenes de esta página de resultados para '$query' ya fueron descargadas.")
                return false
            }
            
            val id = selectedItem.getString("id")
            val imageUrl = selectedItem.getString("path")
            
            // Generar nombre de archivo amigable usando el ID de Wallhaven para evitar colisiones
            val safeQueryName = query.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val fileName = "wallhaven_${safeQueryName}_${id}"
            
            Log.d(TAG, "Encontrada imagen no repetida para '$query' (ID=$id): $imageUrl. Descargando...")
            val success = downloadImageToDocument(context, imageUrl, folderUri, fileName)
            if (success) {
                // Registrar el ID en el historial para evitar repetidos en el futuro
                downloadedIds.add(id)
                prefs.edit().putStringSet("downloader_downloaded_ids", downloadedIds).apply()
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando imagen para query '$query': ${e.message}", e)
            return false
        }
    }

    private fun fetchUrl(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "WallFlow/1.0 (Android Wallpaper Downloader)")
            conn.inputStream.use { stream ->
                stream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error de red consultando Wallhaven API: ${e.message}")
            null
        }
    }

    private fun downloadImageToDocument(context: Context, imageUrl: String, targetFolderUri: Uri, fileName: String): Boolean {
        return try {
            val documentFolder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, targetFolderUri) 
                ?: throw Exception("No se pudo resolver el directorio SAF")
            
            // Identificar el tipo mime y la extensión correcta
            val isPng = imageUrl.endsWith(".png", true)
            val mimeType = if (isPng) "image/png" else "image/jpeg"
            val ext = if (isPng) ".png" else ".jpg"
            
            val newFile = documentFolder.createFile(mimeType, "$fileName$ext")
                ?: throw Exception("No se pudo crear el archivo vacío en el directorio SAF")
            
            val url = URL(imageUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "WallFlow/1.0 (Android Wallpaper Downloader)")
            
            conn.inputStream.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    input.copyTo(output)
                } ?: throw Exception("No se pudo abrir el stream de escritura SAF")
            }
            Log.d(TAG, "Guardada imagen con éxito: $fileName$ext")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo la imagen descargada a SAF: ${e.message}", e)
            false
        }
    }

    /**
     * Programa o reprograma la descarga periódica según las horas configuradas.
     */
    fun rescheduleDownloader(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("downloader_enabled", false)
        val intervalHours = prefs.getInt("downloader_interval_hours", 6).coerceAtLeast(1)

        val workManager = WorkManager.getInstance(context.applicationContext)

        if (enabled) {
            Log.d(TAG, "Programando tarea de descarga automática cada $intervalHours horas.")
            val periodicRequest = PeriodicWorkRequestBuilder<DownloadWorker>(
                intervalHours.toLong(), TimeUnit.HOURS
            ).build()

            workManager.enqueueUniquePeriodicWork(
                "wallpaper_downloader_periodic_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        } else {
            Log.d(TAG, "Descarga periódica desactivada. Cancelando tarea.")
            workManager.cancelUniqueWork("wallpaper_downloader_periodic_work")
        }
    }
}

/**
 * Worker en segundo plano para realizar las descargas de Wallhaven sin bloquear el sistema.
 */
class DownloadWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        Log.d("DownloadWorker", "Ejecutando tarea programada de descarga...")
        val downloaded = WallpaperDownloader.doDownloadBatch(applicationContext)
        return if (downloaded > 0) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
