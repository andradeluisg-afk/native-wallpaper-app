package com.wallpaper.nativeapp

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
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
    private lateinit var layoutHomeFoldersList: LinearLayout
    private lateinit var layoutLockFoldersList: LinearLayout

    // v0.3 Pause/Active state
    private lateinit var switchHomeActive: SwitchCompat
    private lateinit var tvHomeStatusTitle: TextView
    private lateinit var tvHomeStatusDesc: TextView

    private lateinit var switchLockActive: SwitchCompat
    private lateinit var tvLockStatusTitle: TextView
    private lateinit var tvLockStatusDesc: TextView

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

    // v0.3 Crop checkboxes
    private lateinit var cbHomeCrop: android.widget.CheckBox
    private lateinit var cbLockCrop: android.widget.CheckBox

    // v0.4 Blacklist and Adaptive brightness views
    private lateinit var btnHomeClearBlacklist: Button
    private lateinit var switchHomeAdaptiveBrightness: SwitchCompat
    private lateinit var btnLockClearBlacklist: Button
    private lateinit var switchLockAdaptiveBrightness: SwitchCompat

    private var homePreviewLuminance = 1.0f
    private var lockPreviewLuminance = 1.0f

    // v0.4 Downloader views
    private lateinit var tabDownloader: TextView
    private lateinit var layoutDownloaderSettings: LinearLayout
    private lateinit var switchDownloaderActive: SwitchCompat
    private lateinit var tvDownloaderFolderStatus: TextView
    private lateinit var btnDownloaderSelectFolder: Button
    private lateinit var etDownloaderKeywords: EditText
    private lateinit var etDownloaderInterval: EditText
    private lateinit var rgDownloaderBatch: RadioGroup
    private lateinit var rbDownloaderBatch1: RadioButton
    private lateinit var rbDownloaderBatch2: RadioButton
    private lateinit var rbDownloaderBatch3: RadioButton
    private lateinit var rbDownloaderBatch4: RadioButton
    private lateinit var btnDownloaderNow: Button

    private val REQ_DOWNLOADER_FOLDER = 1003

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

        switchHomeActive = findViewById(R.id.switch_home_active)
        tvHomeStatusTitle = findViewById(R.id.tv_home_status_title)
        tvHomeStatusDesc = findViewById(R.id.tv_home_status_desc)

        switchLockActive = findViewById(R.id.switch_lock_active)
        tvLockStatusTitle = findViewById(R.id.tv_lock_status_title)
        tvLockStatusDesc = findViewById(R.id.tv_lock_status_desc)

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

        // Crop checkboxes (v0.3)
        cbHomeCrop = findViewById(R.id.cb_home_crop)
        cbLockCrop = findViewById(R.id.cb_lock_crop)

        // v0.4 controls
        btnHomeClearBlacklist = findViewById(R.id.btn_home_clear_blacklist)
        switchHomeAdaptiveBrightness = findViewById(R.id.switch_home_adaptive_brightness)
        btnLockClearBlacklist = findViewById(R.id.btn_lock_clear_blacklist)
        switchLockAdaptiveBrightness = findViewById(R.id.switch_lock_adaptive_brightness)
        layoutHomeFoldersList = findViewById(R.id.layout_home_folders_list)
        layoutLockFoldersList = findViewById(R.id.layout_lock_folders_list)

        // v0.4 Downloader bindings
        tabDownloader = findViewById(R.id.tab_downloader)
        layoutDownloaderSettings = findViewById(R.id.layout_downloader_settings)
        switchDownloaderActive = findViewById(R.id.switch_downloader_active)
        tvDownloaderFolderStatus = findViewById(R.id.tv_downloader_folder_status)
        btnDownloaderSelectFolder = findViewById(R.id.btn_downloader_select_folder)
        etDownloaderKeywords = findViewById(R.id.et_downloader_keywords)
        etDownloaderInterval = findViewById(R.id.et_downloader_interval)
        rgDownloaderBatch = findViewById(R.id.rg_downloader_batch)
        rbDownloaderBatch1 = findViewById(R.id.rb_downloader_batch_1)
        rbDownloaderBatch2 = findViewById(R.id.rb_downloader_batch_2)
        rbDownloaderBatch3 = findViewById(R.id.rb_downloader_batch_3)
        rbDownloaderBatch4 = findViewById(R.id.rb_downloader_batch_4)
        btnDownloaderNow = findViewById(R.id.btn_downloader_now)

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
            tabDownloader.setBackgroundColor(resources.getColor(R.color.transparent, theme))
            tabDownloader.setTextColor(resources.getColor(R.color.text_secondary, theme))

            layoutHomeSettings.visibility = View.VISIBLE
            layoutLockSettings.visibility = View.GONE
            layoutDownloaderSettings.visibility = View.GONE
        }

        tabLock.setOnClickListener {
            tabLock.setBackgroundResource(R.drawable.bg_tab_active)
            tabLock.setTextColor(resources.getColor(R.color.white, theme))
            tabHome.setBackgroundColor(resources.getColor(R.color.transparent, theme))
            tabHome.setTextColor(resources.getColor(R.color.text_secondary, theme))
            tabDownloader.setBackgroundColor(resources.getColor(R.color.transparent, theme))
            tabDownloader.setTextColor(resources.getColor(R.color.text_secondary, theme))

            layoutHomeSettings.visibility = View.GONE
            layoutLockSettings.visibility = View.VISIBLE
            layoutDownloaderSettings.visibility = View.GONE
        }

        tabDownloader.setOnClickListener {
            tabDownloader.setBackgroundResource(R.drawable.bg_tab_active)
            tabDownloader.setTextColor(resources.getColor(R.color.white, theme))
            tabHome.setBackgroundColor(resources.getColor(R.color.transparent, theme))
            tabHome.setTextColor(resources.getColor(R.color.text_secondary, theme))
            tabLock.setBackgroundColor(resources.getColor(R.color.transparent, theme))
            tabLock.setTextColor(resources.getColor(R.color.text_secondary, theme))

            layoutHomeSettings.visibility = View.GONE
            layoutLockSettings.visibility = View.GONE
            layoutDownloaderSettings.visibility = View.VISIBLE
        }
    }

    private fun loadSettings() {
        // Cargar servicio global
        switchService.isChecked = prefs.getBoolean("service_active", false)

        // Migración de carpeta única a múltiple (v0.5)
        for (prefix in listOf("home_", "lock_")) {
            val classic = prefs.getString("${prefix}folder_uri", null)
            val delimited = prefs.getString("${prefix}folder_uris_delimited", null)
            if (delimited == null && !classic.isNullOrEmpty()) {
                saveFolderUris(prefix, listOf(classic))
            }
        }

        // CONFIGURACIÓN PANTALLA INICIO (v0.3 pausa/play)
        val homePaused = prefs.getBoolean("home_paused", false)
        switchHomeActive.isChecked = !homePaused
        updateHomeActiveTexts(!homePaused)

        updateFoldersListUI(isLock = false)
        val homeUris = getFolderUris("home_")
        val homeFolder = if (homeUris.isNotEmpty()) homeUris[0] else null
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
                cbHomeCrop.visibility = View.GONE
            }
            "fit" -> {
                rbHomeFitFit.isChecked = true
                ivHomePreview.scaleType = ImageView.ScaleType.FIT_CENTER
                cbHomeCrop.visibility = View.GONE
            }
            else -> {
                rbHomeFitFill.isChecked = true
                ivHomePreview.scaleType = ImageView.ScaleType.CENTER_CROP
                cbHomeCrop.visibility = View.VISIBLE
            }
        }
        cbHomeCrop.isChecked = prefs.getBoolean("home_crop", true)

        val homeBrightness = prefs.getInt("home_brightness", 100)
        sbHomeBrightness.progress = homeBrightness
        updateBrightnessText(homeBrightness, isLockScreen = false)

        val homeOrder = prefs.getString("home_order", "random")
        if (homeOrder == "random") rbHomeOrderRandom.isChecked = true else rbHomeOrderSequential.isChecked = true

        // CONFIGURACIÓN PANTALLA BLOQUEO (v0.3 pausa/play)
        val lockPaused = prefs.getBoolean("lock_paused", false)
        switchLockActive.isChecked = !lockPaused
        updateLockActiveTexts(!lockPaused)

        updateFoldersListUI(isLock = true)
        val lockUris = getFolderUris("lock_")
        val lockFolder = if (lockUris.isNotEmpty()) lockUris[0] else null
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
                cbLockCrop.visibility = View.GONE
            }
            "fit" -> {
                rbLockFitFit.isChecked = true
                ivLockPreview.scaleType = ImageView.ScaleType.FIT_CENTER
                cbLockCrop.visibility = View.GONE
            }
            else -> {
                rbLockFitFill.isChecked = true
                ivLockPreview.scaleType = ImageView.ScaleType.CENTER_CROP
                cbLockCrop.visibility = View.VISIBLE
            }
        }
        cbLockCrop.isChecked = prefs.getBoolean("lock_crop", true)

        val lockBrightness = prefs.getInt("lock_brightness", 100)
        sbLockBrightness.progress = lockBrightness
        updateBrightnessText(lockBrightness, isLockScreen = true)

        val lockOrder = prefs.getString("lock_order", "random")
        if (lockOrder == "random") rbLockOrderRandom.isChecked = true else rbLockOrderSequential.isChecked = true

        // v0.4 cargar estados
        switchHomeAdaptiveBrightness.isChecked = prefs.getBoolean("home_adaptive_dim", false)
        switchLockAdaptiveBrightness.isChecked = prefs.getBoolean("lock_adaptive_dim", false)
        updateBlacklistButtons()

        // CONFIGURACIÓN AUTO-DESCARGADOR (v0.4)
        val dlEnabled = prefs.getBoolean("downloader_enabled", false)
        switchDownloaderActive.isChecked = dlEnabled

        val dlFolder = prefs.getString("downloader_folder_uri", null)
        if (dlFolder.isNullOrEmpty()) {
            tvDownloaderFolderStatus.text = "Ninguna seleccionada (Se requiere)"
        } else {
            val uri = Uri.parse(dlFolder)
            val name = getFolderName(this, uri)
            tvDownloaderFolderStatus.text = "Carpeta: $name"
        }

        etDownloaderKeywords.setText(prefs.getString("downloader_keywords", ""))
        
        val dlInterval = prefs.getInt("downloader_interval_hours", 6)
        etDownloaderInterval.setText(dlInterval.toString())

        val dlBatch = prefs.getInt("downloader_batch_size", 1)
        val batchRadioId = when (dlBatch) {
            2 -> R.id.rb_downloader_batch_2
            3 -> R.id.rb_downloader_batch_3
            4 -> R.id.rb_downloader_batch_4
            else -> R.id.rb_downloader_batch_1
        }
        rgDownloaderBatch.check(batchRadioId)
    }

    private fun updateBlacklistButtons() {
        val homeBlacklist = prefs.getStringSet("home_blacklist", emptySet()) ?: emptySet()
        btnHomeClearBlacklist.text = "Restablecer Lista Negra (${homeBlacklist.size})"
        btnHomeClearBlacklist.isEnabled = homeBlacklist.isNotEmpty()

        val lockBlacklist = prefs.getStringSet("lock_blacklist", emptySet()) ?: emptySet()
        btnLockClearBlacklist.text = "Restablecer Lista Negra (${lockBlacklist.size})"
        btnLockClearBlacklist.isEnabled = lockBlacklist.isNotEmpty()
    }

    private fun getFolderUris(prefix: String): List<String> {
        val str = prefs.getString("${prefix}folder_uris_delimited", null) ?: return emptyList()
        return str.split("|").filter { it.isNotEmpty() }
    }

    private fun saveFolderUris(prefix: String, list: List<String>) {
        val str = list.joinToString("|")
        prefs.edit().putString("${prefix}folder_uris_delimited", str).apply()
    }

    private fun updateFoldersListUI(isLock: Boolean) {
        val prefix = if (isLock) "lock_" else "home_"
        val layoutList = if (isLock) layoutLockFoldersList else layoutHomeFoldersList
        val tvStatus = if (isLock) tvLockFolderStatus else tvHomeFolderStatus
        val btnSelect = if (isLock) btnLockSelectFolder else btnHomeSelectFolder

        layoutList.removeAllViews()

        val uris = getFolderUris(prefix).toMutableList()

        if (uris.isEmpty()) {
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = getString(R.string.no_folder_selected)
            layoutList.visibility = View.GONE
        } else {
            tvStatus.visibility = View.GONE
            layoutList.visibility = View.VISIBLE

            for (uriStr in uris) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, 8)
                    }
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val tvName = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = getFolderName(this@MainActivity, Uri.parse(uriStr))
                    setTextColor(resources.getColor(R.color.text_primary, theme))
                    textSize = 13sp
                }

                val btnDelete = TextView(this).apply {
                    text = "Quitar"
                    setTextColor(resources.getColor(R.color.secondary, theme))
                    textSize = 12sp
                    setPadding(12, 6, 12, 6)
                    isClickable = true
                    isFocusable = true
                    setBackgroundResource(R.drawable.bg_card)

                    setOnClickListener {
                        uris.remove(uriStr)
                        saveFolderUris(prefix, uris)
                        updateFoldersListUI(isLock)
                        updateFolderUI(null, isLock)
                        rescheduleJobs()
                    }
                }

                row.addView(tvName)
                row.addView(btnDelete)
                layoutList.addView(row)
            }
        }

        btnSelect.text = "Agregar Carpeta (${uris.size}/4)"
        btnSelect.isEnabled = uris.size < 4
    }

    private fun setupListeners() {
        // Toggle de Servicio en segundo plano
        switchService.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("service_active", isChecked).apply()
            rescheduleJobs()
        }

        // Toggle de pausa de rotación de inicio (v0.3)
        switchHomeActive.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("home_paused", !isChecked).apply()
            updateHomeActiveTexts(isChecked)
            
            // Avisar al widget para que se redibuje inmediatamente
            notifyWidgetsOfPauseChange(isLockScreen = false)
        }

        // Toggle de pausa de rotación de bloqueo (v0.3)
        switchLockActive.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("lock_paused", !isChecked).apply()
            updateLockActiveTexts(isChecked)
            
            // Avisar al widget para que se redibuje inmediatamente
            notifyWidgetsOfPauseChange(isLockScreen = true)
        }

        // v0.4 Listeners
        switchHomeAdaptiveBrightness.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("home_adaptive_dim", isChecked).apply()
            updateBrightnessText(sbHomeBrightness.progress, isLockScreen = false)
        }

        switchLockAdaptiveBrightness.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("lock_adaptive_dim", isChecked).apply()
            updateBrightnessText(sbLockBrightness.progress, isLockScreen = true)
        }

        btnHomeClearBlacklist.setOnClickListener {
            prefs.edit().remove("home_blacklist").apply()
            Toast.makeText(this, "Lista negra de inicio vaciada", Toast.LENGTH_SHORT).show()
            updateBlacklistButtons()
            val homeUris = getFolderUris("home_")
            val homeFolder = if (homeUris.isNotEmpty()) homeUris[0] else null
            updateFolderUI(homeFolder, isLockScreen = false)
        }

        btnLockClearBlacklist.setOnClickListener {
            prefs.edit().remove("lock_blacklist").apply()
            Toast.makeText(this, "Lista negra de bloqueo vaciada", Toast.LENGTH_SHORT).show()
            updateBlacklistButtons()
            val lockUris = getFolderUris("lock_")
            val lockFolder = if (lockUris.isNotEmpty()) lockUris[0] else null
            updateFolderUI(lockFolder, isLockScreen = true)
        }

        // Botón Cambiar fondo ahora
        btnChangeNow.setOnClickListener {
            btnChangeNow.isEnabled = false
            activityScope.launch {
                val successHome = withContext(Dispatchers.IO) {
                    WallpaperHelper.changeWallpaper(this@MainActivity, isLockScreen = false)
                }
                val successLock = withContext(Dispatchers.IO) {
                    WallpaperHelper.changeWallpaper(this@MainActivity, isLockScreen = true)
                }
                btnChangeNow.isEnabled = true
                if (successHome || successLock) {
                    Toast.makeText(this@MainActivity, "Fondos cambiados con éxito", Toast.LENGTH_SHORT).show()
                    loadPreviewForScreen(isLockScreen = false)
                    loadPreviewForScreen(isLockScreen = true)
                } else {
                    Toast.makeText(this@MainActivity, "Error al cambiar fondos. Verifica las carpetas.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // LISTENERS DEL AUTO-DESCARGADOR
        switchDownloaderActive.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("downloader_enabled", isChecked).apply()
            WallpaperDownloader.rescheduleDownloader(this)
        }

        btnDownloaderSelectFolder.setOnClickListener {
            selectFolder(REQ_DOWNLOADER_FOLDER)
        }

        etDownloaderKeywords.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                prefs.edit().putString("downloader_keywords", s?.toString() ?: "").apply()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        etDownloaderInterval.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hours = s?.toString()?.toIntOrNull() ?: 6
                prefs.edit().putInt("downloader_interval_hours", hours.coerceAtLeast(1)).apply()
                WallpaperDownloader.rescheduleDownloader(this@MainActivity)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        rgDownloaderBatch.setOnCheckedChangeListener { _, checkedId ->
            val size = when (checkedId) {
                R.id.rb_downloader_batch_2 -> 2
                R.id.rb_downloader_batch_3 -> 3
                R.id.rb_downloader_batch_4 -> 4
                else -> 1
            }
            prefs.edit().putInt("downloader_batch_size", size).apply()
        }

        btnDownloaderNow.setOnClickListener {
            val folder = prefs.getString("downloader_folder_uri", null)
            if (folder.isNullOrEmpty()) {
                Toast.makeText(this, "Selecciona una carpeta primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnDownloaderNow.isEnabled = false
            btnDownloaderNow.text = "Descargando..."

            activityScope.launch {
                val downloaded = withContext(Dispatchers.IO) {
                    WallpaperDownloader.doDownloadBatch(this@MainActivity, force = true)
                }
                btnDownloaderNow.isEnabled = true
                btnDownloaderNow.text = "Descargar Ahora"
                if (downloaded > 0) {
                    Toast.makeText(this@MainActivity, "¡Listo! Se descargaron $downloaded imágenes.", Toast.LENGTH_SHORT).show()
                    
                    // Actualizar previsualización y conteo
                    val homeFolder = prefs.getString("home_folder_uri", null)
                    updateFolderUI(homeFolder, isLockScreen = false)
                    val lockFolder = prefs.getString("lock_folder_uri", null)
                    updateFolderUI(lockFolder, isLockScreen = true)
                } else {
                    Toast.makeText(this@MainActivity, "No se pudo descargar ninguna imagen. Revisa tu conexión o palabras clave.", Toast.LENGTH_SHORT).show()
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
            // Show crop checkbox only in fill mode
            cbHomeCrop.visibility = if (fit == "fill") View.VISIBLE else View.GONE
        }
        cbHomeCrop.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("home_crop", isChecked).apply()
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
            // Show crop checkbox only in fill mode
            cbLockCrop.visibility = if (fit == "fill") View.VISIBLE else View.GONE
        }
        cbLockCrop.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("lock_crop", isChecked).apply()
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
            
            // Tomar permisos persistentes de lectura y escritura
            val takeFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try {
                contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (e: Exception) {
                Log.e(TAG, "Error solicitando permisos persistentes: ${e.message}")
            }

            val uriString = treeUri.toString()

            if (requestCode == REQ_DOWNLOADER_FOLDER) {
                prefs.edit().putString("downloader_folder_uri", uriString).apply()
                val folderName = getFolderName(this, treeUri)
                tvDownloaderFolderStatus.text = "Carpeta: $folderName"
                
                // Actualizar los textos de carpeta ya que las imágenes de descargas se combinan
                val homeUris = getFolderUris("home_")
                val homeFolder = if (homeUris.isNotEmpty()) homeUris[0] else null
                updateFolderUI(homeFolder, isLockScreen = false)
                
                val lockUris = getFolderUris("lock_")
                val lockFolder = if (lockUris.isNotEmpty()) lockUris[0] else null
                updateFolderUI(lockFolder, isLockScreen = true)
            } else {
                val isLock = (requestCode == REQ_LOCK_FOLDER)
                val prefix = if (isLock) "lock_" else "home_"

                val uris = getFolderUris(prefix).toMutableList()
                if (!uris.contains(uriString) && uris.size < 4) {
                    uris.add(uriString)
                    saveFolderUris(prefix, uris)
                    
                    // Asegurar de habilitar la rotación
                    prefs.edit().apply {
                        putBoolean("${prefix}enabled", true)
                        putInt("${prefix}current_index", 0) // reset index
                        apply()
                    }

                    updateFoldersListUI(isLock)
                    updateFolderUI(null, isLock)
                    rescheduleJobs()
                }
            }
        }
    }

    private fun updateFolderUI(uriString: String?, isLockScreen: Boolean) {
        val tvStatus = if (isLockScreen) tvLockFolderStatus else tvHomeFolderStatus
        val tvCount = if (isLockScreen) tvLockImageCount else tvHomeImageCount
        val prefix = if (isLockScreen) "lock_" else "home_"
        
        if (uriString.isNullOrEmpty()) {
            tvStatus.text = getString(R.string.no_folder_selected)
        } else {
            val uri = Uri.parse(uriString)
            val folderName = getFolderName(this, uri)
            tvStatus.text = "Carpeta: $folderName"
        }

        // Escanear imágenes en segundo plano para no colgar la UI
        activityScope.launch {
            val images = withContext(Dispatchers.IO) {
                WallpaperHelper.getCombinedImagesForScreen(this@MainActivity, isLockScreen)
            }
            
            // Filtrar lista negra en la preview y en el contador
            val blacklist = prefs.getStringSet("${prefix}blacklist", emptySet()) ?: emptySet()
            val filteredImages = images.filter { !blacklist.contains(it.toString()) }
            
            tvCount.text = "${filteredImages.size} imágenes encontradas"
            
            if (filteredImages.isNotEmpty()) {
                val order = prefs.getString("${prefix}order", "random") ?: "random"
                val index = if (order == "sequential") {
                    val currentIndex = prefs.getInt("${prefix}current_index", 0)
                    if (currentIndex < filteredImages.size) currentIndex else 0
                } else {
                    0
                }
                loadPreviewImage(filteredImages[index], isLockScreen)
            } else {
                loadPlaceholderPreview(isLockScreen)
            }
        }
    }

    private fun loadPreviewForScreen(isLockScreen: Boolean) {
        val prefix = if (isLockScreen) "lock_" else "home_"
        val folderUri = prefs.getString("${prefix}folder_uri", null)
        updateFolderUI(folderUri, isLockScreen)
    }

    private fun loadPreviewImage(uri: Uri, isLockScreen: Boolean) {
        val ivPreview = if (isLockScreen) ivLockPreview else ivHomePreview
        
        activityScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                // Decodificar el archivo real de forma segura y reducir su escala
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
                // v0.4 calcular luminancia de la miniatura para la vista previa
                val luminance = WallpaperHelper.getAverageLuminance(bitmap)
                if (isLockScreen) {
                    lockPreviewLuminance = luminance
                } else {
                    homePreviewLuminance = luminance
                }
                val progress = if (isLockScreen) sbLockBrightness.progress else sbHomeBrightness.progress
                updateBrightnessText(progress, isLockScreen)
            } else {
                loadPlaceholderPreview(isLockScreen)
            }
        }
    }

    private fun loadPlaceholderPreview(isLockScreen: Boolean) {
        val ivPreview = if (isLockScreen) ivLockPreview else ivHomePreview
        ivPreview.setImageResource(android.R.drawable.ic_menu_gallery)
        ivPreview.scaleType = ImageView.ScaleType.FIT_CENTER
        if (isLockScreen) {
            lockPreviewLuminance = 1.0f
        } else {
            homePreviewLuminance = 1.0f
        }
        val progress = if (isLockScreen) sbLockBrightness.progress else sbHomeBrightness.progress
        updateBrightnessText(progress, isLockScreen)
    }

    private fun updateBrightnessText(progress: Int, isLockScreen: Boolean) {
        val tvVal = if (isLockScreen) tvLockBrightnessVal else tvHomeBrightnessVal
        val viewDim = if (isLockScreen) viewLockPreviewDim else viewHomePreviewDim
        val prefix = if (isLockScreen) "lock_" else "home_"
        val adaptiveDim = prefs.getBoolean("${prefix}adaptive_dim", false)
        val luminance = if (isLockScreen) lockPreviewLuminance else homePreviewLuminance
        
        val baseDimAlphaPercent = 100 - progress
        val finalDimAlphaPercent = if (adaptiveDim) {
            val factor = ((luminance - 0.15f) / 0.65f).coerceIn(0f, 1f)
            (baseDimAlphaPercent * factor).toInt()
        } else {
            baseDimAlphaPercent
        }
        
        if (progress == 100) {
            tvVal.text = "Brillo: 100% (Original)"
        } else {
            if (adaptiveDim) {
                tvVal.text = "Brillo: $progress% (Atenuación inteligente: -$finalDimAlphaPercent%)"
            } else {
                tvVal.text = "Brillo: $progress% (Oscurecido -$baseDimAlphaPercent%)"
            }
        }
        
        val alpha = finalDimAlphaPercent / 100f
        viewDim.alpha = alpha.coerceIn(0f, 0.9f) // no dejar que se ponga 100% negro en la preview
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

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "home_paused") {
            val isPaused = sharedPreferences.getBoolean("home_paused", false)
            runOnUiThread {
                if (::switchHomeActive.isInitialized) {
                    if (switchHomeActive.isChecked == isPaused) {
                        switchHomeActive.isChecked = !isPaused
                        updateHomeActiveTexts(!isPaused)
                    }
                }
            }
        } else if (key == "lock_paused") {
            val isPaused = sharedPreferences.getBoolean("lock_paused", false)
            runOnUiThread {
                if (::switchLockActive.isInitialized) {
                    if (switchLockActive.isChecked == isPaused) {
                        switchLockActive.isChecked = !isPaused
                        updateLockActiveTexts(!isPaused)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        // Refrescar en caso de que haya cambiado desde el Widget mientras la app estaba oculta
        val homePaused = prefs.getBoolean("home_paused", false)
        if (::switchHomeActive.isInitialized) {
            switchHomeActive.isChecked = !homePaused
            updateHomeActiveTexts(!homePaused)
        }
        val lockPaused = prefs.getBoolean("lock_paused", false)
        if (::switchLockActive.isInitialized) {
            switchLockActive.isChecked = !lockPaused
            updateLockActiveTexts(!lockPaused)
        }
        // v0.4 Actualizar contadores de lista negra
        updateBlacklistButtons()
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun updateHomeActiveTexts(isActive: Boolean) {
        if (isActive) {
            tvHomeStatusTitle.text = "Rotación: Activa"
            tvHomeStatusDesc.text = "La imagen cambiará automáticamente según la configuración."
            tvHomeStatusTitle.setTextColor(resources.getColor(R.color.text_primary, theme))
        } else {
            tvHomeStatusTitle.text = "Rotación: Pausada"
            tvHomeStatusDesc.text = "La auto-rotación de inicio está detenida. Cambios manuales permitidos."
            tvHomeStatusTitle.setTextColor(resources.getColor(R.color.secondary, theme)) // color Cyan para resaltar pausa
        }
    }

    private fun updateLockActiveTexts(isActive: Boolean) {
        if (isActive) {
            tvLockStatusTitle.text = "Rotación: Activa"
            tvLockStatusDesc.text = "La imagen cambiará automáticamente según la configuración."
            tvLockStatusTitle.setTextColor(resources.getColor(R.color.text_primary, theme))
        } else {
            tvLockStatusTitle.text = "Rotación: Pausada"
            tvLockStatusDesc.text = "La auto-rotación de bloqueo está detenida. Cambios manuales permitidos."
            tvLockStatusTitle.setTextColor(resources.getColor(R.color.secondary, theme)) // color Cyan para resaltar pausa
        }
    }

    private fun notifyWidgetsOfPauseChange(isLockScreen: Boolean) {
        val providerClass = if (isLockScreen) WallpaperWidgetLock::class.java else WallpaperWidgetHome::class.java
        val widgetIntent = Intent(this, providerClass).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val appWidgetManager = AppWidgetManager.getInstance(application)
        val thisWidget = ComponentName(application, providerClass)
        val ids = appWidgetManager.getAppWidgetIds(thisWidget)
        widgetIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(widgetIntent)
    }
}
