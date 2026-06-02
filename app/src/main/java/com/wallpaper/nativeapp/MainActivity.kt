package com.wallpaper.nativeapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val REQ_HOME_FOLDER = 1001
    private val REQ_LOCK_FOLDER = 1002

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    // UI Elements
    private lateinit var switchService: SwitchCompat
    private lateinit var btnChangeNow: Button
    private lateinit var tabHome: TextView
    private lateinit var tabLock: TextView
    private lateinit var layoutHomeSettings: LinearLayout
    private lateinit var layoutLockSettings: LinearLayout

    // Home settings views
    private lateinit var tvHomeFolderStatus: TextView
    private lateinit var tvHomeImageCount: TextView
    private lateinit var btnHomeSelectFolder: Button
    private lateinit var rgHomeTrigger: RadioGroup
    private lateinit var rbHomeTriggerTime: RadioButton
    private lateinit var rbHomeTriggerUnlock: RadioButton
    private lateinit var layoutHomeInterval: LinearLayout
    private lateinit var spinnerHomeInterval: Spinner
    private lateinit var rgHomeFit: RadioGroup
    private lateinit var rbHomeFitFill: RadioButton
    private lateinit var rbHomeFitStretch: RadioButton
    private lateinit var rbHomeFitFit: RadioButton
    private lateinit var tvHomeBrightnessVal: TextView
    private lateinit var sbHomeBrightness: SeekBar
    private lateinit var rgHomeOrder: RadioGroup
    private lateinit var rbHomeOrderRandom: RadioButton
    private lateinit var rbHomeOrderSequential: RadioButton
    private lateinit var ivHomePreview: ImageView
    private lateinit var viewHomePreviewDim: View

    // Lock settings views
    private lateinit var tvLockFolderStatus: TextView
    private lateinit var tvLockImageCount: TextView
    private lateinit var btnLockSelectFolder: Button
    private lateinit var rgLockTrigger: RadioGroup
    private lateinit var rbLockTriggerTime: RadioButton
    private lateinit var rbLockTriggerUnlock: RadioButton
    private lateinit var layoutLockInterval: LinearLayout
    private lateinit var spinnerLockInterval: Spinner
    private lateinit var rgLockFit: RadioGroup
    private lateinit var rbLockFitFill: RadioButton
    private lateinit var rbLockFitStretch: RadioButton
    private lateinit var rbLockFitFit: RadioButton
    private lateinit var tvLockBrightnessVal: TextView
    private lateinit var sbLockBrightness: SeekBar
    private lateinit var rgLockOrder: RadioGroup
    private lateinit var rbLockOrderRandom: RadioButton
    private lateinit var rbLockOrderSequential: RadioButton
    private lateinit var ivLockPreview: ImageView
    private lateinit var viewLockPreviewDim: View

    private lateinit var prefs: SharedPreferences

    // Spinner values
    private val intervalLabels = arrayOf(
        "Cada 15 minutos",
        "Cada 30 minutos",
        "Cada 1 hora",
        "Cada 2 horas",
        "Cada 6 horas",
        "Cada 12 horas",
        "Cada 24 horas"
    )
    private val intervalValues = arrayOf(15, 30, 60, 120, 360, 720, 1440)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

        initViews()
        setupTabs()
        loadSettings()
        setupListeners()

        // Verificar y solicitar permisos de notificaciones si es necesario (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 200)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun initViews() {
        switchService = findViewById(R.id.switch_service)
        btnChangeNow = findViewById(R.id.btn_change_now)
        tabHome = findViewById(R.id.tab_home)
        tabLock = findViewById(R.id.tab_lock)
        layoutHomeSettings = findViewById(R.id.layout_home_settings)
        layoutLockSettings = findViewById(R.id.layout_lock_settings)

        // Home settings
        tvHomeFolderStatus = findViewById(R.id.tv_home_folder_status)
        tvHomeImageCount = findViewById(R.id.tv_home_image_count)
        btnHomeSelectFolder = findViewById(R.id.btn_home_select_folder)
        rgHomeTrigger = findViewById(R.id.rg_home_trigger)
        rbHomeTriggerTime = findViewById(R.id.rb_home_trigger_time)
        rbHomeTriggerUnlock = findViewById(R.id.rb_home_trigger_unlock)
        layoutHomeInterval = findViewById(R.id.layout_home_interval)
        spinnerHomeInterval = findViewById(R.id.spinner_home_interval)
        rgHomeFit = findViewById(R.id.rg_home_fit)
        rbHomeFitFill = findViewById(R.id.rb_home_fit_fill)
        rbHomeFitStretch = findViewById(R.id.rb_home_fit_stretch)
        rbHomeFitFit = findViewById(R.id.rb_home_fit_fit)
        tvHomeBrightnessVal = findViewById(R.id.tv_home_brightness_val)
        sbHomeBrightness = findViewById(R.id.sb_home_brightness)
        rgHomeOrder = findViewById(R.id.rg_home_order)
        rbHomeOrderRandom = findViewById(R.id.rb_home_order_random)
        rbHomeOrderSequential = findViewById(R.id.rb_home_order_sequential)
        ivHomePreview = findViewById(R.id.iv_home_preview)
        viewHomePreviewDim = findViewById(R.id.view_home_preview_dim)

        // Lock settings
        tvLockFolderStatus = findViewById(R.id.tv_lock_folder_status)
        tvLockImageCount = findViewById(R.id.tv_lock_image_count)
        btnLockSelectFolder = findViewById(R.id.btn_lock_select_folder)
        rgLockTrigger = findViewById(R.id.rg_lock_trigger)
        rbLockTriggerTime = findViewById(R.id.rb_lock_trigger_time)
        rbLockTriggerUnlock = findViewById(R.id.rb_lock_trigger_unlock)
        layoutLockInterval = findViewById(R.id.layout_lock_interval)
        spinnerLockInterval = findViewById(R.id.spinner_lock_interval)
        rgLockFit = findViewById(R.id.rg_lock_fit)
        rbLockFitFill = findViewById(R.id.rb_lock_fit_fill)
        rbLockFitStretch = findViewById(R.id.rb_lock_fit_stretch)
        rbLockFitFit = findViewById(R.id.rb_lock_fit_fit)
        tvLockBrightnessVal = findViewById(R.id.tv_lock_brightness_val)
        sbLockBrightness = findViewById(R.id.sb_lock_brightness)
        rgLockOrder = findViewById(R.id.rg_lock_order)
        rbLockOrderRandom = findViewById(R.id.rb_lock_order_random)
        rbLockOrderSequential = findViewById(R.id.rb_lock_order_sequential)
        ivLockPreview = findViewById(R.id.iv_lock_preview)
        viewLockPreviewDim = findViewById(R.id.view_lock_preview_dim)

        // Setup Spinners
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervalLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerHomeInterval.adapter = adapter
        spinnerLockInterval.adapter = adapter
    }

    private fun setupTabs() {
        tabHome.setOnClickListener {
            tabHome.setBackgroundResource(R.drawable.bg_tab_active)
            tabHome.setTextColor(resources.getColor(R.color.white, theme))
            tabLock.setBackgroundColor(resources.getColor(R.color.transparent, theme))
            tabLock.setTextColor(resources.getColor(R.color.text_secondary, theme))

            layoutHomeSettings.visibility = View.VISIBLE
            layoutLockSettings.visibility = View.GONE
        }

        tabLock.setOnClickListener {
            tabLock.setBackgroundResource(R.drawable.bg_tab_active)
            tabLock.setTextColor(resources.getColor(R.color.white, theme))
            tabHome.setBackgroundColor(resources.getColor(R.color.transparent, theme))
            tabHome.setTextColor(resources.getColor(R.color.text_secondary, theme))

            layoutHomeSettings.visibility = View.GONE
            layoutLockSettings.visibility = View.VISIBLE
        }
    }

    private fun loadSettings() {
        // Cargar servicio global
        switchService.isChecked = prefs.getBoolean("service_active", false)

        // CONFIGURACIÓN PANTALLA INICIO
        val homeFolder = prefs.getString("home_folder_uri", null)
        updateFolderUI(homeFolder, isLockScreen = false)

        val homeTrigger = prefs.getString("home_trigger", "time")
        if (homeTrigger == "time") {
            rbHomeTriggerTime.isChecked = true
            layoutHomeInterval.visibility = View.VISIBLE
        } else {
            rbHomeTriggerUnlock.isChecked = true
            layoutHomeInterval.visibility = View.GONE
        }

        val homeInterval = prefs.getInt("home_interval", 60)
        val homeIntervalIdx = intervalValues.indexOf(homeInterval).coerceAtLeast(0)
        spinnerHomeInterval.setSelection(homeIntervalIdx)

        val homeFit = prefs.getString("home_fit_mode", "fill")
        when (homeFit) {
            "stretch" -> {
                rbHomeFitStretch.isChecked = true
                ivHomePreview.scaleType = ImageView.ScaleType.FIT_XY
            }
            "fit" -> {
                rbHomeFitFit.isChecked = true
                ivHomePreview.scaleType = ImageView.ScaleType.FIT_CENTER
            }
            else -> {
                rbHomeFitFill.isChecked = true
                ivHomePreview.scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }

        val homeBrightness = prefs.getInt("home_brightness", 100)
        sbHomeBrightness.progress = homeBrightness
        updateBrightnessText(homeBrightness, isLockScreen = false)

        val homeOrder = prefs.getString("home_order", "random")
        if (homeOrder == "random") rbHomeOrderRandom.isChecked = true else rbHomeOrderSequential.isChecked = true

        // CONFIGURACIÓN PANTALLA BLOQUEO
        val lockFolder = prefs.getString("lock_folder_uri", null)
        updateFolderUI(lockFolder, isLockScreen = true)

        val lockTrigger = prefs.getString("lock_trigger", "time")
        if (lockTrigger == "time") {
            rbLockTriggerTime.isChecked = true
            layoutLockInterval.visibility = View.VISIBLE
        } else {
            rbLockTriggerUnlock.isChecked = true
            layoutLockInterval.visibility = View.GONE
        }

        val lockInterval = prefs.getInt("lock_interval", 60)
        val lockIntervalIdx = intervalValues.indexOf(lockInterval).coerceAtLeast(0)
        spinnerLockInterval.setSelection(lockIntervalIdx)

        val lockFit = prefs.getString("lock_fit_mode", "fill")
        when (lockFit) {
            "stretch" -> {
                rbLockFitStretch.isChecked = true
                ivLockPreview.scaleType = ImageView.ScaleType.FIT_XY
            }
            "fit" -> {
                rbLockFitFit.isChecked = true
                ivLockPreview.scaleType = ImageView.ScaleType.FIT_CENTER
            }
            else -> {
                rbLockFitFill.isChecked = true
                ivLockPreview.scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }

        val lockBrightness = prefs.getInt("lock_brightness", 100)
        sbLockBrightness.progress = lockBrightness
        updateBrightnessText(lockBrightness, isLockScreen = true)

        val lockOrder = prefs.getString("lock_order", "random")
        if (lockOrder == "random") rbLockOrderRandom.isChecked = true else rbLockOrderSequential.isChecked = true
    }

    private fun setupListeners() {
        // Toggle de Servicio en segundo plano
        switchService.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("service_active", isChecked).apply()
            rescheduleJobs()
        }

        // Botón Cambiar fondo ahora
        btnChangeNow.setOnClickListener {
            btnChangeNow.isEnabled = false
            btnChangeNow.text = "Cambiando..."
            
            activityScope.launch {
                val homeSuccess = withContext(Dispatchers.IO) {
                    WallpaperHelper.changeWallpaper(this@MainActivity, isLockScreen = false)
                }
                val lockSuccess = withContext(Dispatchers.IO) {
                    WallpaperHelper.changeWallpaper(this@MainActivity, isLockScreen = true)
                }

                btnChangeNow.isEnabled = true
                btnChangeNow.text = getString(R.string.btn_change_now)

                if (homeSuccess || lockSuccess) {
                    Toast.makeText(this@MainActivity, "Fondos actualizados correctamente", Toast.LENGTH_SHORT).show()
                    // Recargar previsualización para reflejar cambio si es secuencial
                    loadPreviewForScreen(isLockScreen = false)
                    loadPreviewForScreen(isLockScreen = true)
                } else {
                    Toast.makeText(this@MainActivity, "No se pudo cambiar el fondo. Asegúrate de configurar carpetas válidas.", Toast.LENGTH_LONG).show()
                }
            }
        }

        // LISTENERS PARA INICIO
        btnHomeSelectFolder.setOnClickListener { selectFolder(REQ_HOME_FOLDER) }
        rgHomeTrigger.setOnCheckedChangeListener { _, checkedId ->
            val trigger = if (checkedId == R.id.rb_home_trigger_time) "time" else "unlock"
            prefs.edit().putString("home_trigger", trigger).apply()
            layoutHomeInterval.visibility = if (trigger == "time") View.VISIBLE else View.GONE
            rescheduleJobs()
        }
        spinnerHomeInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val interval = intervalValues[position]
                prefs.edit().putInt("home_interval", interval).apply()
                rescheduleJobs()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        rgHomeFit.setOnCheckedChangeListener { _, checkedId ->
            val fit = when (checkedId) {
                R.id.rb_home_fit_stretch -> "stretch"
                R.id.rb_home_fit_fit -> "fit"
                else -> "fill"
            }
            prefs.edit().putString("home_fit_mode", fit).apply()
            ivHomePreview.scaleType = when (fit) {
                "stretch" -> ImageView.ScaleType.FIT_XY
                "fit" -> ImageView.ScaleType.FIT_CENTER
                else -> ImageView.ScaleType.CENTER_CROP
            }
        }
        sbHomeBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val p = progress.coerceIn(10, 100)
                updateBrightnessText(p, isLockScreen = false)
                prefs.edit().putInt("home_brightness", p).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        rgHomeOrder.setOnCheckedChangeListener { _, checkedId ->
            val order = if (checkedId == R.id.rb_home_order_random) "random" else "sequential"
            prefs.edit().putString("home_order", order).apply()
        }

        // LISTENERS PARA BLOQUEO
        btnLockSelectFolder.setOnClickListener { selectFolder(REQ_LOCK_FOLDER) }
        rgLockTrigger.setOnCheckedChangeListener { _, checkedId ->
            val trigger = if (checkedId == R.id.rb_lock_trigger_time) "time" else "unlock"
            prefs.edit().putString("lock_trigger", trigger).apply()
            layoutLockInterval.visibility = if (trigger == "time") View.VISIBLE else View.GONE
            rescheduleJobs()
        }
        spinnerLockInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val interval = intervalValues[position]
                prefs.edit().putInt("lock_interval", interval).apply()
                rescheduleJobs()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        rgLockFit.setOnCheckedChangeListener { _, checkedId ->
            val fit = when (checkedId) {
                R.id.rb_lock_fit_stretch -> "stretch"
                R.id.rb_lock_fit_fit -> "fit"
                else -> "fill"
            }
            prefs.edit().putString("lock_fit_mode", fit).apply()
            ivLockPreview.scaleType = when (fit) {
                "stretch" -> ImageView.ScaleType.FIT_XY
                "fit" -> ImageView.ScaleType.FIT_CENTER
                else -> ImageView.ScaleType.CENTER_CROP
            }
        }
        sbLockBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val p = progress.coerceIn(10, 100)
                updateBrightnessText(p, isLockScreen = true)
                prefs.edit().putInt("lock_brightness", p).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        rgLockOrder.setOnCheckedChangeListener { _, checkedId ->
            val order = if (checkedId == R.id.rb_lock_order_random) "random" else "sequential"
            prefs.edit().putString("lock_order", order).apply()
        }
    }

    private fun selectFolder(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            val treeUri = data.data ?: return
            
            // Tomar permisos persistentes de lectura
            val takeFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(treeUri, takeFlags)

            val uriString = treeUri.toString()
            val isLock = (requestCode == REQ_LOCK_FOLDER)
            val prefix = if (isLock) "lock_" else "home_"

            prefs.edit().apply {
                putString("${prefix}folder_uri", uriString)
                putBoolean("${prefix}enabled", true)
                putInt("${prefix}current_index", 0) // reset index
                apply()
            }

            updateFolderUI(uriString, isLock)
            rescheduleJobs()
        }
    }

    private fun updateFolderUI(uriString: String?, isLockScreen: Boolean) {
        val tvStatus = if (isLockScreen) tvLockFolderStatus else tvHomeFolderStatus
        val tvCount = if (isLockScreen) tvLockImageCount else tvHomeImageCount
        
        if (uriString.isNullOrEmpty()) {
            tvStatus.text = getString(R.string.no_folder_selected)
            tvCount.text = "0 imágenes encontradas"
            loadPlaceholderPreview(isLockScreen)
            return
        }

        val uri = Uri.parse(uriString)
        val folderName = getFolderName(this, uri)
        tvStatus.text = "Carpeta: $folderName"

        // Escanear imágenes en segundo plano para no colgar la UI
        activityScope.launch {
            val images = withContext(Dispatchers.IO) {
                WallpaperHelper.getImagesFromFolder(this@MainActivity, uriString)
            }
            tvCount.text = "${images.size} imágenes encontradas"
            
            if (images.isNotEmpty()) {
                // Cargar la primera imagen del folder en la vista previa
                loadPreviewImage(images[0], isLockScreen)
            } else {
                loadPlaceholderPreview(isLockScreen)
            }
        }
    }

    private fun loadPreviewForScreen(isLockScreen: Boolean) {
        val prefix = if (isLockScreen) "lock_" else "home_"
        val folderUri = prefs.getString("${prefix}folder_uri", null)
        
        if (folderUri.isNullOrEmpty()) {
            loadPlaceholderPreview(isLockScreen)
            return
        }

        activityScope.launch {
            val images = withContext(Dispatchers.IO) {
                WallpaperHelper.getImagesFromFolder(this@MainActivity, folderUri)
            }
            if (images.isNotEmpty()) {
                val order = prefs.getString("${prefix}order", "random") ?: "random"
                val index = if (order == "sequential") {
                    val currentIndex = prefs.getInt("${prefix}current_index", 0)
                    if (currentIndex < images.size) currentIndex else 0
                } else {
                    0
                }
                loadPreviewImage(images[index], isLockScreen)
            } else {
                loadPlaceholderPreview(isLockScreen)
            }
        }
    }

    private fun loadPreviewImage(uri: Uri, isLockScreen: Boolean) {
        val ivPreview = if (isLockScreen) ivLockPreview else ivHomePreview
        
        activityScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                // Reducimos la imagen a un tamaño pequeño óptimo para la previsualización en miniatura (300x600 px)
                WallpaperHelper.processBitmap(
                    WallpaperHelper.processBitmap(
                        // Primero decodificamos el bitmap con una muestra segura
                        WallpaperHelper.processBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), 1, 1, "fill", 100)!!, 1, 1, "fill", 100
                    )!!, // placeholder
                    300, 600, "fill", 100
                ) // Evita llamadas redundantes.
                // Decodificar el archivo real de forma segura
                try {
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    contentResolver.openInputStream(uri)?.use { 
                        android.graphics.BitmapFactory.decodeStream(it, null, options)
                    }
                    
                    // Calculamos inSampleSize para 300x600 px
                    var inSampleSize = 1
                    val reqW = 300
                    val reqH = 600
                    if (options.outHeight > reqH || options.outWidth > reqW) {
                        val halfH = options.outHeight / 2
                        val halfW = options.outWidth / 2
                        while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                            inSampleSize *= 2
                        }
                    }
                    options.inJustDecodeBounds = false
                    options.inSampleSize = inSampleSize
                    
                    contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, options)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error cargando miniatura de previsualización: ${e.message}")
                    null
                }
            }

            if (bitmap != null) {
                ivPreview.setImageBitmap(bitmap)
            } else {
                loadPlaceholderPreview(isLockScreen)
            }
        }
    }

    private fun loadPlaceholderPreview(isLockScreen: Boolean) {
        val ivPreview = if (isLockScreen) ivLockPreview else ivHomePreview
        ivPreview.setImageResource(android.R.drawable.ic_menu_gallery)
        ivPreview.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun updateBrightnessText(progress: Int, isLockScreen: Boolean) {
        val tvVal = if (isLockScreen) tvLockBrightnessVal else tvHomeBrightnessVal
        val viewDim = if (isLockScreen) viewLockPreviewDim else viewHomePreviewDim
        
        if (progress == 100) {
            tvVal.text = "Brillo: 100% (Original)"
        } else {
            tvVal.text = "Brillo: $progress% (Oscurecido ${100 - progress}%)"
        }
        
        // El view de atenuación sobre la imagen de preview ajusta su alfa inversamente al progreso
        val alpha = (100 - progress) / 100f
        viewDim.alpha = alpha.coerceIn(0f, 0.9f) // no dejar que se ponga 100% negro en la preview para seguir apreciando la imagen
    }

    private fun getFolderName(context: Context, uri: Uri): String {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        return it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando nombre de carpeta: ${e.message}")
        }
        
        // Fallback
        val lastSegment = uri.lastPathSegment ?: return "Carpeta"
        val parts = lastSegment.split(":")
        return if (parts.size > 1) Uri.decode(parts[1]) else Uri.decode(parts[0])
    }

    private fun rescheduleJobs() {
        val isServiceChecked = switchService.isChecked
        
        val homeEnabled = prefs.getBoolean("home_enabled", false)
        val homeTrigger = prefs.getString("home_trigger", "time")
        val lockEnabled = prefs.getBoolean("lock_enabled", false)
        val lockTrigger = prefs.getString("lock_trigger", "time")

        val needsService = isServiceChecked && ((homeEnabled && homeTrigger == "unlock") || (lockEnabled && lockTrigger == "unlock"))
        val needsWorker = (homeEnabled && homeTrigger == "time") || (lockEnabled && lockTrigger == "time")

        // 1. Administrar Servicio en Primer Plano
        val serviceIntent = Intent(this, WallpaperService::class.java)
        if (needsService) {
            Log.d(TAG, "Iniciando WallpaperService para disparadores de desbloqueo...")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando servicio: ${e.message}")
            }
        } else {
            Log.d(TAG, "Deteniendo WallpaperService (no se requiere)...")
            stopService(serviceIntent)
        }

        // 2. Administrar WorkManager
        val workManager = WorkManager.getInstance(applicationContext)
        if (needsWorker) {
            Log.d(TAG, "Programando tarea de WorkManager para disparadores de tiempo...")
            val periodicWorkRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(15, TimeUnit.MINUTES).build()
            workManager.enqueueUniquePeriodicWork(
                "wallpaper_periodic_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWorkRequest
            )
        } else {
            Log.d(TAG, "Cancelando tarea de WorkManager...")
            workManager.cancelUniqueWork("wallpaper_periodic_work")
        }
    }
}
