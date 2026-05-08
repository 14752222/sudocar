package com.sudocar.launcher.dialog

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.widget.SwitchCompat
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.sudocar.launcher.R

class SettingsDialog(context: Context) : Dialog(context, android.R.style.Theme_Material_Dialog_NoActionBar) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_settings)

        window?.apply {
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setDimAmount(0.7f)
        }

        setupWallpaper()
        setupQuickApps()
        setupAutoStart()
        setupDebugMode()
        setupKeyTest()
        setupTestButtons()
        setupAbout()
        
        applyThemeColors()
    }

    private fun applyThemeColors() {
        val isNightMode = isNightModeEnabled(context)
        val bgColor = if (isNightMode) Color.parseColor("#1A1A2E") else Color.parseColor("#FFFFFF")
        findViewById<LinearLayout>(R.id.settings_container)?.setBackgroundColor(bgColor)
    }

    // ==================== 壁纸设置 ====================
    private fun setupWallpaper() {
        findViewById<LinearLayout>(R.id.item_wallpaper)?.setOnClickListener {
            showWallpaperPicker()
        }
    }

    private fun showWallpaperPicker() {
        val items = arrayOf(
            "🌙 深色 #000000",
            "🌃 深蓝 #1A1A2E",
            "🪨 深灰 #333333",
            "🖼️ 系统壁纸"
        )
        
        AlertDialog.Builder(context)
            .setTitle("选择背景色")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> setBackgroundColor("#000000")
                    1 -> setBackgroundColor("#1A1A2E")
                    2 -> setBackgroundColor("#333333")
                    3 -> openSystemWallpaper()
                }
            }
            .show()
    }

    private fun setBackgroundColor(color: String) {
        try {
            prefs.edit()
                .putString(KEY_WALLPAPER_COLOR, color)
                .putBoolean(KEY_USE_CUSTOM_BG, true)
                .apply()
            Toast.makeText(context, "已设置背景色", Toast.LENGTH_SHORT).show()
            // ✅ 不要直接调用 onBackgroundChanged()，改为关闭 Dialog
            dismiss()
        } catch (e: Exception) {
            Toast.makeText(context, "设置失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSystemWallpaper() {
        try {
            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开系统壁纸设置", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 快捷应用 ====================
    private fun setupQuickApps() {
        updateQuickAppsDisplay()

        findViewById<LinearLayout>(R.id.item_quick_app)?.setOnClickListener {
            showQuickAppsManager()
        }
    }

    private fun showQuickAppsManager() {
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }

        val currentApps = getQuickApps()
        val tvTitle = TextView(context).apply {
            text = "当前快捷应用 (${currentApps.size}/5)"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        dialogView.addView(tvTitle)

        if (currentApps.isEmpty()) {
            dialogView.addView(TextView(context).apply {
                text = "暂无快捷应用\n点击下方按钮添加"
                setTextColor(Color.parseColor("#888888"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 32)
            })
        } else {
            currentApps.forEachIndexed { index, app ->
                val itemView = createQuickAppItem(app, index)
                dialogView.addView(itemView)
            }
        }

        val addBtn = TextView(context).apply {
            text = "+ 添加快捷应用"
            setTextColor(Color.parseColor("#4FC3F7"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 16)
            setOnClickListener { showAppPicker() }
        }
        dialogView.addView(addBtn)

        AlertDialog.Builder(context)
            .setTitle("快捷应用管理")
            .setView(dialogView)
            .setPositiveButton("完成", null)
            .show()
    }

    private fun createQuickAppItem(app: QuickAppInfo, index: Int): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#2A2A3E"))

            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48)
                setImageDrawable(getAppIcon(app.packageName))
            }

            val nameView = TextView(context).apply {
                text = app.name
                setTextColor(Color.WHITE)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 16
                }
            }

            val deleteBtn = TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor("#FF5252"))
                textSize = 18f
                setPadding(16, 8, 16, 8)
                setOnClickListener {
                    removeQuickApp(index)
                    showQuickAppsManager()
                }
            }

            addView(iconView)
            addView(nameView)
            addView(deleteBtn)
        }
    }

    private fun showAppPicker() {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { app ->
                (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 &&
                app.packageName != context.packageName
            }
            .sortedBy { it.loadLabel(pm).toString() }

        if (apps.isEmpty()) {
            Toast.makeText(context, "没有可用的第三方应用", Toast.LENGTH_SHORT).show()
            return
        }

        val names = apps.map { it.loadLabel(pm).toString() }.toTypedArray()
        
        AlertDialog.Builder(context)
            .setTitle("选择快捷应用")
            .setItems(names) { _, which ->
                val app = apps[which]
                addQuickApp(app.packageName, app.loadLabel(pm).toString())
                try { showQuickAppsManager() } catch (_: Exception) {}
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addQuickApp(packageName: String, appName: String) {
        val currentApps = getQuickApps().toMutableList()

        if (currentApps.any { it.packageName == packageName }) {
            Toast.makeText(context, "该应用已在快捷列表中", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentApps.size >= 5) {
            Toast.makeText(context, "最多添加5个快捷应用", Toast.LENGTH_SHORT).show()
            return
        }

        currentApps.add(QuickAppInfo(packageName, appName))
        saveQuickApps(currentApps)
        updateQuickAppsDisplay()
        Toast.makeText(context, "已添加: $appName", Toast.LENGTH_SHORT).show()
    }

    private fun removeQuickApp(index: Int) {
        val currentApps = getQuickApps().toMutableList()
        if (index in currentApps.indices) {
            currentApps.removeAt(index)
            saveQuickApps(currentApps)
            updateQuickAppsDisplay()
        }
    }

    private fun getQuickApps(): List<QuickAppInfo> {
        val count = prefs.getInt(KEY_QUICK_APP_COUNT, 0)
        val list = mutableListOf<QuickAppInfo>()
        for (i in 0 until count) {
            val pkg = prefs.getString("${KEY_QUICK_APP_PREFIX}${i}_pkg", null)
            val name = prefs.getString("${KEY_QUICK_APP_PREFIX}${i}_name", null)
            if (pkg != null && name != null) {
                list.add(QuickAppInfo(pkg, name))
            }
        }
        return list
    }

    private fun saveQuickApps(apps: List<QuickAppInfo>) {
        prefs.edit().putInt(KEY_QUICK_APP_COUNT, apps.size).apply()
        apps.forEachIndexed { index, app ->
            prefs.edit()
                .putString("${KEY_QUICK_APP_PREFIX}${index}_pkg", app.packageName)
                .putString("${KEY_QUICK_APP_PREFIX}${index}_name", app.name)
                .apply()
        }
    }

    private fun updateQuickAppsDisplay() {
        val tvQuickApp = findViewById<TextView>(R.id.tv_quick_app_name)
        val apps = getQuickApps()
        tvQuickApp?.text = if (apps.isEmpty()) "未设置" else "${apps.size}个应用"
        
        val icon = findViewById<ImageView>(R.id.iv_quick_app_arrow)
        icon?.setColorFilter(
            if (apps.isEmpty()) Color.parseColor("#666666") 
            else Color.parseColor("#4FC3F7")
        )
    }

    private fun getAppIcon(packageName: String) = try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (e: Exception) {
        ContextCompat.getDrawable(context, android.R.drawable.ic_menu_help)
    }

    // ==================== 开机自启动 ====================
    private fun setupAutoStart() {
        val switch = findViewById<SwitchCompat>(R.id.switch_auto_start)
        switch?.isChecked = prefs.getBoolean(KEY_AUTO_START, false)

        switch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_START, isChecked).apply()
            if (isChecked) {
                requestAutoStartPermission()
            }
            Toast.makeText(context, if (isChecked) "已开启开机自启动" else "已关闭开机自启动", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestAutoStartPermission() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
            Toast.makeText(context, "请在设置中开启自启动权限", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "请手动到系统设置中开启自启动", Toast.LENGTH_LONG).show()
        }
    }

    // ==================== 调试模式 ====================
    private fun setupDebugMode() {
        val switch = findViewById<SwitchCompat>(R.id.switch_debug_mode)
        switch?.isChecked = prefs.getBoolean(KEY_DEBUG_MODE, false)

        switch?.setOnCheckedChangeListener { _, isChecked ->
            try {
                prefs.edit().putBoolean(KEY_DEBUG_MODE, isChecked).apply()
                Toast.makeText(context, if (isChecked) "已开启调试模式" else "已关闭调试模式", Toast.LENGTH_SHORT).show()
                // 通知 MainActivity 调试模式变化
                (context as? OnDebugModeChangeListener)?.onDebugModeChanged(isChecked)
            } catch (e: Exception) {
                Toast.makeText(context, "切换失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== 按键测试 ====================
    private fun setupKeyTest() {
        findViewById<LinearLayout>(R.id.item_key_test)?.setOnClickListener {
            (context as? OnKeyTestToggleListener)?.onKeyTestToggle()
            dismiss()
        }
    }

    // ==================== 测试功能 ====================
    private fun setupTestButtons() {
        findViewById<LinearLayout>(R.id.item_test)?.setOnClickListener {
            showTestMenu()
        }
    }

    private fun showTestMenu() {
        val items = arrayOf(
            "🐛 开启调试模式",
            "⬛ 测试背景 - 黑色",
            "🌃 测试背景 - 深蓝",
            "⬜ 测试背景 - 白色",
            "📱 测试快捷启动弹窗",
            "ℹ️ 查看当前设置"
        )

        AlertDialog.Builder(context)
            .setTitle("🧪 测试功能")
            .setItems(items) { _, which ->
                try {
                    when (which) {
                        0 -> {
                            prefs.edit().putBoolean(KEY_DEBUG_MODE, true).apply()
                            (context as? OnDebugModeChangeListener)?.onDebugModeChanged(true)
                            Toast.makeText(context, "已开启调试模式", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                        1 -> {
                            prefs.edit().putString(KEY_WALLPAPER_COLOR, "#000000").apply()
                            dismiss()
                        }
                        2 -> {
                            prefs.edit().putString(KEY_WALLPAPER_COLOR, "#1A1A2E").apply()
                            dismiss()
                        }
                        3 -> {
                            prefs.edit().putString(KEY_WALLPAPER_COLOR, "#FFFFFF").apply()
                            dismiss()
                        }
                        4 -> {
                            dismiss()
                            (context as? OnTestQuickSwitchListener)?.onTestQuickSwitch()
                        }
                        5 -> showCurrentSettings()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showCurrentSettings() {
        val nightMode = prefs.getBoolean(KEY_NIGHT_MODE, true)
        val debugMode = prefs.getBoolean(KEY_DEBUG_MODE, false)
        val bgColor = prefs.getString(KEY_WALLPAPER_COLOR, "未设置")
        val appCount = prefs.getInt(KEY_QUICK_APP_COUNT, 0)
        val autoStart = prefs.getBoolean(KEY_AUTO_START, false)

        val message = """
            🌙 夜间模式: ${if (nightMode) "开" else "关"}
            🐛 调试模式: ${if (debugMode) "开" else "关"}
            🎨 背景颜色: $bgColor
            📱 快捷应用: $appCount 个
            ⚡ 开机自启: ${if (autoStart) "开" else "关"}
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle("📋 当前设置")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    // ==================== 关于 ====================
    private fun setupAbout() {
        findViewById<LinearLayout>(R.id.item_about)?.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("🚗 关于 SudoCar")
                .setMessage("""
                    SudoCar Launcher
                    版本: 1.0
                    
                    专为车机优化的桌面启动器
                    支持地图+音乐双屏布局
                    方向盘按键控制
                    
                    适配车型: 长安 CS75 Plus
                """.trimIndent())
                .setPositiveButton("确定", null)
                .show()
        }
    }

    // ==================== 数据类 ====================
    data class QuickAppInfo(val packageName: String, val name: String)

    // ==================== 静态方法 ====================
    companion object {
        const val PREFS_NAME = "sudocar_settings"
        const val KEY_WALLPAPER_COLOR = "wallpaper_color"
        const val KEY_USE_CUSTOM_BG = "use_custom_bg"
        const val KEY_QUICK_APP_COUNT = "quick_app_count"
        const val KEY_QUICK_APP_PREFIX = "quick_app_"
        const val KEY_AUTO_START = "auto_start"
        const val KEY_NIGHT_MODE = "night_mode"
        const val KEY_DEBUG_MODE = "debug_mode"

        fun getAllQuickApps(context: Context): List<QuickAppInfo> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_QUICK_APP_COUNT, 0)
            val list = mutableListOf<QuickAppInfo>()

            list.add(QuickAppInfo(context.packageName, "桌面"))

            for (i in 0 until count) {
                val pkg = prefs.getString("${KEY_QUICK_APP_PREFIX}${i}_pkg", null)
                val name = prefs.getString("${KEY_QUICK_APP_PREFIX}${i}_name", null)
                if (pkg != null && name != null) {
                    list.add(QuickAppInfo(pkg, name))
                }
            }
            return list
        }

        fun isNightModeEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_NIGHT_MODE, true)
        }

        fun isDebugModeEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DEBUG_MODE, false)
        }

        fun isAutoStartEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_START, false)
        }

        fun getWallpaperColor(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_WALLPAPER_COLOR, null)
        }
    }

    // ==================== 回调接口 ====================
    interface OnDebugModeChangeListener {
        fun onDebugModeChanged(enabled: Boolean)
    }

    interface OnBackgroundChangeListener {
        fun onBackgroundChanged()
    }

    interface OnTestQuickSwitchListener {
        fun onTestQuickSwitch()
    }

    interface OnKeyTestToggleListener {
        fun onKeyTestToggle()
    }
}
