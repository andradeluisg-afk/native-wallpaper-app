package com.wallpaper.nativeapp

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WizardDialog(private val activity: MainActivity) : Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private lateinit var prefs: SharedPreferences
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var tvStepIndicator: TextView
    private lateinit var progressBar: ProgressBar

    // Paso 2: Chips
    private val selectedChips = mutableSetOf("anime", "dark")
    private lateinit var etCustomKeywords: EditText

    // Paso 3: Carpeta Wallhaven
    private lateinit var tvFolderName: TextView
    private lateinit var layoutDownloadBox: LinearLayout
    private lateinit var layoutDownloadProgress: LinearLayout
    private lateinit var tvDownloadDone: TextView
    private lateinit var btnStep3Next: Button

    // Paso 4: Carpeta Personal
    private lateinit var tvPersonalName: TextView
    private lateinit var rgPersonalTarget: RadioGroup
    private var pendingPersonalUri: Uri? = null

    // Paso 5: Frecuencias
    private lateinit var rgLockTrigger: RadioGroup
    private lateinit var rbLockUnlock: RadioButton
    private lateinit var rgHomeTrigger: RadioGroup
    private lateinit var rbHome1h: RadioButton
    private lateinit var cbDimHome: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_wizard)

        prefs = activity.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

        initViews()
        setupStep1()
        setupStep2()
        setupStep3()
        setupStep4()
        setupStep5()
        setupStep6()
    }

    private fun initViews() {
        viewFlipper = findViewById(R.id.wizard_view_flipper)
        tvStepIndicator = findViewById(R.id.tv_wizard_step_indicator)
        progressBar = findViewById(R.id.wizard_progress_bar)

        findViewById<View>(R.id.btn_wizard_close).setOnClickListener {
            dismiss()
        }
    }

    private fun updateStepHeader(step: Int) {
        tvStepIndicator.text = "Paso $step de 6"
        progressBar.progress = step
    }

    // ==========================================
    // PASO 1: Bienvenida
    // ==========================================
    private fun setupStep1() {
        val cardExisting = findViewById<LinearLayout>(R.id.card_wizard_existing_user)
        val btnKeepExisting = findViewById<Button>(R.id.btn_wizard_keep_existing)
        val btnNext = findViewById<Button>(R.id.btn_step1_next)

        // Comprobar si es un usuario que ya tenía configuración previa
        val existingHome = prefs.getString("home_folder_uris_delimited", null)
        val existingDl = prefs.getString("downloader_folder_uri", null)
        if (!existingHome.isNullOrEmpty() || !existingDl.isNullOrEmpty()) {
            cardExisting.visibility = View.VISIBLE
            btnKeepExisting.setOnClickListener {
                prefs.edit().putBoolean("wizard_completed_v1", true).apply()
                dismiss()
            }
        } else {
            cardExisting.visibility = View.GONE
        }

        btnNext.setOnClickListener {
            // Activar el servicio por defecto
            prefs.edit().putBoolean("service_active", true).apply()
            viewFlipper.displayedChild = 1
            updateStepHeader(2)
        }
    }

    // ==========================================
    // PASO 2: Gustos en Wallhaven
    // ==========================================
    private fun setupStep2() {
        etCustomKeywords = findViewById(R.id.et_wizard_custom_keywords)

        // Pre-cargar si ya tenía palabras clave
        val savedKeywords = prefs.getString("downloader_keywords", "") ?: ""
        if (savedKeywords.isNotEmpty()) {
            etCustomKeywords.setText(savedKeywords)
        }

        val chipMap = mapOf(
            R.id.chip_anime to "anime",
            R.id.chip_fantasy to "fantasy",
            R.id.chip_gaming to "gaming",
            R.id.chip_nature to "nature",
            R.id.chip_dark to "dark",
            R.id.chip_cyberpunk to "cyberpunk",
            R.id.chip_minimalism to "minimalism",
            R.id.chip_digital_art to "digital art",
            R.id.chip_cars to "cars"
        )

        for ((chipId, keyword) in chipMap) {
            val tvChip = findViewById<TextView>(chipId) ?: continue
            updateChipUi(tvChip, selectedChips.contains(keyword))

            tvChip.setOnClickListener {
                if (selectedChips.contains(keyword)) {
                    selectedChips.remove(keyword)
                    updateChipUi(tvChip, false)
                } else {
                    selectedChips.add(keyword)
                    updateChipUi(tvChip, true)
                }
            }
        }

        findViewById<Button>(R.id.btn_step2_back).setOnClickListener {
            viewFlipper.displayedChild = 0
            updateStepHeader(1)
        }

        findViewById<Button>(R.id.btn_step2_next).setOnClickListener {
            val customText = etCustomKeywords.text.toString().trim()
            val list = selectedChips.toMutableList()
            if (customText.isNotEmpty()) {
                val extras = customText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                list.addAll(extras)
            }

            if (list.isEmpty()) {
                Toast.makeText(context, "Por favor selecciona al menos una categoría", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val finalKeywords = list.distinct().joinToString(", ")
            prefs.edit().putString("downloader_keywords", finalKeywords).apply()

            viewFlipper.displayedChild = 2
            updateStepHeader(3)
        }
    }

    private fun updateChipUi(tvChip: TextView, isSelected: Boolean) {
        if (isSelected) {
            tvChip.setBackgroundResource(R.drawable.bg_chip_selected)
            tvChip.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            tvChip.setBackgroundResource(R.drawable.bg_chip_unselected)
            tvChip.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
    }

    // ==========================================
    // PASO 3: Carpeta Wallhaven y Descarga
    // ==========================================
    private fun setupStep3() {
        tvFolderName = findViewById(R.id.tv_wizard_folder_name)
        layoutDownloadBox = findViewById(R.id.layout_wizard_download_box)
        layoutDownloadProgress = findViewById(R.id.layout_wizard_download_progress)
        tvDownloadDone = findViewById(R.id.tv_wizard_download_done)
        btnStep3Next = findViewById(R.id.btn_step3_next)

        val currentDlFolder = prefs.getString("downloader_folder_uri", null)
        if (!currentDlFolder.isNullOrEmpty()) {
            val uri = Uri.parse(currentDlFolder)
            val name = getFolderName(activity, uri)
            tvFolderName.text = "Carpeta actual: $name"
            btnStep3Next.isEnabled = true
        }

        findViewById<Button>(R.id.btn_wizard_pick_wallhaven_folder).setOnClickListener {
            activity.selectFolderForWizard(isWallhaven = true)
        }

        findViewById<Button>(R.id.btn_step3_back).setOnClickListener {
            viewFlipper.displayedChild = 1
            updateStepHeader(2)
        }

        btnStep3Next.setOnClickListener {
            viewFlipper.displayedChild = 3
            updateStepHeader(4)
        }
    }

    fun onWallhavenFolderPicked(uri: Uri) {
        val folderName = getFolderName(activity, uri)
        tvFolderName.text = "Carpeta: $folderName"

        // Guardar carpeta y auto-vincular a Inicio y Bloqueo
        val uriStr = uri.toString()
        prefs.edit().putString("downloader_folder_uri", uriStr).apply()
        addFolderToScreen("home_", uriStr)
        addFolderToScreen("lock_", uriStr)

        // Mostrar sección de descarga inmediata
        layoutDownloadBox.visibility = View.VISIBLE
        layoutDownloadProgress.visibility = View.VISIBLE
        tvDownloadDone.visibility = View.GONE
        btnStep3Next.isEnabled = false

        // Disparar descarga inmediata de 6 fondos
        CoroutineScope(Dispatchers.IO).launch {
            val count = WallpaperDownloader.doDownloadBatch(activity, force = true, customBatchSize = 6)
            withContext(Dispatchers.Main) {
                layoutDownloadProgress.visibility = View.GONE
                tvDownloadDone.visibility = View.VISIBLE
                if (count > 0) {
                    tvDownloadDone.text = "✓ ¡Listo! $count fondos descargados y vinculados a Inicio y Bloqueo."
                } else {
                    tvDownloadDone.text = "✓ Carpeta vinculada con éxito a Inicio y Bloqueo."
                }
                btnStep3Next.isEnabled = true
            }
        }
    }

    private fun addFolderToScreen(prefix: String, folderUri: String) {
        val currentStr = prefs.getString("${prefix}folder_uris_delimited", null)
        val list = currentStr?.split("|")?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
        if (!list.contains(folderUri)) {
            list.add(folderUri)
            prefs.edit().putString("${prefix}folder_uris_delimited", list.joinToString("|")).apply()
        }
        prefs.edit().putBoolean("${prefix}enabled", true).apply()
    }

    // ==========================================
    // PASO 4: Fotos Personales (Opcional)
    // ==========================================
    private fun setupStep4() {
        tvPersonalName = findViewById(R.id.tv_wizard_personal_name)
        rgPersonalTarget = findViewById(R.id.rg_wizard_personal_target)

        findViewById<Button>(R.id.btn_wizard_pick_personal_folder).setOnClickListener {
            activity.selectFolderForWizard(isWallhaven = false)
        }

        findViewById<Button>(R.id.btn_step4_back).setOnClickListener {
            viewFlipper.displayedChild = 2
            updateStepHeader(3)
        }

        findViewById<Button>(R.id.btn_step4_next).setOnClickListener {
            // Si eligió una carpeta personal, vincularla según la opción elegida
            pendingPersonalUri?.let { uri ->
                val uriStr = uri.toString()
                val targetId = rgPersonalTarget.checkedRadioButtonId
                when (targetId) {
                    R.id.rb_personal_home -> addFolderToScreen("home_", uriStr)
                    R.id.rb_personal_lock -> addFolderToScreen("lock_", uriStr)
                    else -> {
                        addFolderToScreen("home_", uriStr)
                        addFolderToScreen("lock_", uriStr)
                    }
                }
            }

            viewFlipper.displayedChild = 4
            updateStepHeader(5)
        }
    }

    fun onPersonalFolderPicked(uri: Uri) {
        pendingPersonalUri = uri
        val folderName = getFolderName(activity, uri)
        tvPersonalName.text = "Carpeta elegida: $folderName"
        rgPersonalTarget.visibility = View.VISIBLE
    }

    // ==========================================
    // PASO 5: Frecuencia y Estilo
    // ==========================================
    private fun setupStep5() {
        rgLockTrigger = findViewById(R.id.rg_wizard_lock_trigger)
        rbLockUnlock = findViewById(R.id.rb_wizard_lock_unlock)
        rgHomeTrigger = findViewById(R.id.rg_wizard_home_trigger)
        rbHome1h = findViewById(R.id.rb_wizard_home_1h)
        cbDimHome = findViewById(R.id.cb_wizard_dim_home)

        findViewById<Button>(R.id.btn_step5_back).setOnClickListener {
            viewFlipper.displayedChild = 3
            updateStepHeader(4)
        }

        findViewById<Button>(R.id.btn_step5_next).setOnClickListener {
            // Guardar configuración de Bloqueo
            val lockTrigger = if (rbLockUnlock.isChecked) "unlock" else "time"
            prefs.edit().putString("lock_trigger", lockTrigger).apply()
            if (lockTrigger == "time") {
                prefs.edit().putInt("lock_interval", 60).apply()
            }

            // Guardar configuración de Inicio
            val homeTrigger = if (rbHome1h.isChecked) "time" else "unlock"
            prefs.edit().putString("home_trigger", homeTrigger).apply()
            if (homeTrigger == "time") {
                prefs.edit().putInt("home_interval", 60).apply()
            }

            // Guardar atenuación inteligente en Inicio
            if (cbDimHome.isChecked) {
                prefs.edit()
                    .putInt("home_brightness", 80)
                    .putBoolean("home_adaptive_dim", true)
                    .apply()
            } else {
                prefs.edit()
                    .putInt("home_brightness", 100)
                    .putBoolean("home_adaptive_dim", false)
                    .apply()
            }

            viewFlipper.displayedChild = 5
            updateStepHeader(6)
        }
    }

    // ==========================================
    // PASO 6: Aplicar Fondos y Empezar
    // ==========================================
    private fun setupStep6() {
        val btnFinish = findViewById<Button>(R.id.btn_wizard_finish)

        btnFinish.setOnClickListener {
            btnFinish.isEnabled = false
            btnFinish.text = "Aplicando fondos..."

            // Activar todos los servicios
            prefs.edit().apply {
                putBoolean("service_active", true)
                putBoolean("home_enabled", true)
                putBoolean("lock_enabled", true)
                putBoolean("home_paused", false)
                putBoolean("lock_paused", false)
                putBoolean("downloader_enabled", true)
                putInt("downloader_interval_hours", 6)
                putInt("downloader_batch_size", 4)
                putBoolean("wizard_completed_v1", true)
                apply()
            }

            // Programar descargas periódicas de Wallhaven
            WallpaperDownloader.rescheduleDownloader(activity)

            // Aplicar primer fondo inmediatamente
            CoroutineScope(Dispatchers.IO).launch {
                WallpaperHelper.changeWallpaper(activity, isLockScreen = false)
                WallpaperHelper.changeWallpaper(activity, isLockScreen = true)

                withContext(Dispatchers.Main) {
                    activity.loadSettings()
                    activity.rescheduleJobs()
                    dismiss()
                    Toast.makeText(activity, "¡WallFlow activo! Disfruta de tus nuevos fondos.", Toast.LENGTH_LONG).show()
                }
            }
        }
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
        } catch (ignored: Exception) {}
        val lastSegment = uri.lastPathSegment ?: return "Carpeta"
        val parts = lastSegment.split(":")
        return if (parts.size > 1) Uri.decode(parts[1]) else Uri.decode(parts[0])
    }
}
