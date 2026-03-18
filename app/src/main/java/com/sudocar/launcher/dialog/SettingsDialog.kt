package com.sudocar.launcher.dialog

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.sudocar.launcher.R

class SettingsDialog(context: Context) : Dialog(context, android.R.style.Theme_Material_Dialog_NoActionBar) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_settings)

        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        setupWallpaper()
        setupQuickApps()
        setupAutoStart()
        setupNightMode()
        setupTestButtons() // 测试按钮
        setupAbout()
    }

    private fun setupWallpaper() {
        findViewById<LinearLayout>(R.id.item_wallpaper)?.setOnClickListener {
            showWallpaperPicker()
        }
    }

    private fun showWallpaperPicker() {
        val items = arrayOf("系统壁纸", "相册选择", "黑色 #000000", "深蓝 #1A1A2E", "深灰 #333333")
        AlertDialog.Builder(context)
            .setTitle("选择壁纸/背景")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openSystemWallpaper()
                    1 -> openGallery()
                    2 -> setBackgroundColor("#000000")
                    3 -> setBackgroundColor("#1A1A2E")
                    4 -> setBackgroundColor("#333333")
                }
            }
            .show()
    }

    private fun setBackgroundColor(color: String) {
        prefs.edit()
            .putString(KEY_WALLPAPER_COLOR, color)
            .putBoolean(KEY_USE_CUSTOM_BG, true)
            .apply()
        Toast.makeText(context, "已设置背景色: $color", Toast.LENGTH_SHORT).show()
        // 立即应用
        (context as? OnBackgroundChangeListener)?.onBackgroundChanged()
    }

    private fun openSystemWallpaper() {
        try {
            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开系统壁纸", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开相册", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupQuickApps() {
        updateQuickAppsDisplay()

        findViewById<LinearLayout>(R.id.item_quick_app)?.setOnClickListener {
            showAppPicker()
        }
    }

    private fun showAppPicker() {
        val pm = context.packageManager
        // 只获取非系统应用
        val apps = pm.getInstalledApplications(0)
            .filter { app ->
                // 排除系统应用
                (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 &&
                (app.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
                app.packageName != context.packageName
            }
            .sortedBy { it.loadLabel(pm).toString() }

        if (apps.isEmpty()) {
            Toast.makeText(context, "没有可用的第三方应用", Toast.LENGTH_SHORT).show()
            return
        }

        // 创建带图标的列表
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        val scrollView = android.widget.ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        apps.forEach { app ->
            val appView = createAppItem(app.loadLabel(pm).toString(), app.loadIcon(pm), app.packageName)
            container.addView(appView)
        }

        scrollView.addView(container)
        dialogView.addView(scrollView)

        val dialog = AlertDialog.Builder(context)
            .setTitle("选择快捷应用 (最多5个)")
            .setView(dialogView)
            .setNegativeButton("取消", null)
            .setNeutralButton("管理") { _, _ ->
                showManageQuickApps()
            }
            .create()

        dialog.show()
    }

    private fun createAppItem(name: String, icon: android.graphics.drawable.Drawable, packageName: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundResource(android.R.drawable.list_selector_background)

            val iconView = ImageView(context).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(48, 48)
            }

            val nameView = TextView(context).apply {
                text = name
                textSize = 16f
                setTextColor(context.getColor(R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 16
                }
            }

            addView(iconView)
            addView(nameView)

            setOnClickListener {
                addQuickApp(packageName, name)
            }
        }
    }

    private fun showManageQuickApps() {
        val apps = getQuickApps()
        if (apps.isEmpty()) {
            Toast.makeText(context, "暂无快捷应用", Toast.LENGTH_SHORT).show()
            return
        }

        val names = apps.map { it.name }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("管理快捷应用")
            .setItems(names) { _, which ->
                AlertDialog.Builder(context)
                    .setTitle("移除 ${names[which]}?")
                    .setPositiveButton("移除") { _, _ ->
                        removeQuickApp(which)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setPositiveButton("完成", null)
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
            val removed = currentApps.removeAt(index)
            saveQuickApps(currentApps)
            updateQuickAppsDisplay()
            Toast.makeText(context, "已移除: ${removed.name}", Toast.LENGTH_SHORT).show()
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
    }

    private fun setupAutoStart() {
        val switch = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_auto_start)
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
            val intent = Intent()
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.data = Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "请手动到设置中开启自启动权限", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupNightMode() {
        val switch = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_night_mode)
        switch?.isChecked = prefs.getBoolean(KEY_NIGHT_MODE, true)

        switch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NIGHT_MODE, isChecked).apply()
            (context as? OnNightModeChangeListener)?.onNightModeChanged()
            Toast.makeText(context, if (isChecked) "已开启夜景模式" else "已关闭夜景模式", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTestButtons() {
        findViewById<LinearLayout>(R.id.item_test)?.setOnClickListener {
            showTestMenu()
        }
    }

    private fun showTestMenu() {
        val items = arrayOf(
            "测试夜间模式 - 开",
            "测试夜间模式 - 关",
            "测试背景色 - 黑色",
            "测试背景色 - 深蓝",
            "测试背景色 - 白色",
            "测试快捷启动弹窗",
            "查看当前设置"
        )

        AlertDialog.Builder(context)
            .setTitle("测试功能")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        prefs.edit().putBoolean(KEY_NIGHT_MODE, true).apply()
                        (context as? OnNightModeChangeListener)?.onNightModeChanged()
                        Toast.makeText(context, "夜间模式: 开", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        prefs.edit().putBoolean(KEY_NIGHT_MODE, false).apply()
                        (context as? OnNightModeChangeListener)?.onNightModeChanged()
                        Toast.makeText(context, "夜间模式: 关", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        prefs.edit().putString(KEY_WALLPAPER_COLOR, "#000000").apply()
                        (context as? OnBackgroundChangeListener)?.onBackgroundChanged()
                        Toast.makeText(context, "背景: 黑色", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        prefs.edit().putString(KEY_WALLPAPER_COLOR, "#1A1A2E").apply()
                        (context as? OnBackgroundChangeListener)?.onBackgroundChanged()
                        Toast.makeText(context, "背景: 深蓝", Toast.LENGTH_SHORT).show()
                    }
                    4 -> {
                        prefs.edit().putString(KEY_WALLPAPER_COLOR, "#FFFFFF").apply()
                        (context as? OnBackgroundChangeListener)?.onBackgroundChanged()
                        Toast.makeText(context, "背景: 白色", Toast.LENGTH_SHORT).show()
                    }
                    5 -> {
                        (context as? OnTestQuickSwitchListener)?.onTestQuickSwitch()
                    }
                    6 -> showCurrentSettings()
                }
            }
            .show()
    }

    private fun showCurrentSettings() {
        val nightMode = prefs.getBoolean(KEY_NIGHT_MODE, true)
        val bgColor = prefs.getString(KEY_WALLPAPER_COLOR, "未设置")
        val appCount = prefs.getInt(KEY_QUICK_APP_COUNT, 0)

        val message = """
            夜间模式: ${if (nightMode) "开" else "关"}
            背景颜色: $bgColor
            快捷应用: $appCount 个
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle("当前设置")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun setupAbout() {
        findViewById<LinearLayout>(R.id.item_about)?.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("关于 SudoCar")
                .setMessage("SudoCar Launcher\n版本: 1.0\n专为长安 CS75 Plus 优化")
                .setPositiveButton("确定", null)
                .show()
        }
    }

    data class QuickAppInfo(val packageName: String, val name: String)

    companion object {
        const val PREFS_NAME = "sudocar_settings"
        const val KEY_WALLPAPER_COLOR = "wallpaper_color"
        const val KEY_USE_CUSTOM_BG = "use_custom_bg"
        const val KEY_QUICK_APP_COUNT = "quick_app_count"
        const val KEY_QUICK_APP_PREFIX = "quick_app_"
        const val KEY_AUTO_START = "auto_start"
        const val KEY_NIGHT_MODE = "night_mode"

        fun getAllQuickApps(context: Context): List<QuickAppInfo> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_QUICK_APP_COUNT, 0)
            val list = mutableListOf<QuickAppInfo>()

            // 第一个是桌面启动器
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
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_NIGHT_MODE, true)
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

    // 回调接口
    interface OnNightModeChangeListener {
        fun onNightModeChanged()
    }

    interface OnBackgroundChangeListener {
        fun onBackgroundChanged()
    }

    interface OnTestQuickSwitchListener {
        fun onTestQuickSwitch()
    }
}
