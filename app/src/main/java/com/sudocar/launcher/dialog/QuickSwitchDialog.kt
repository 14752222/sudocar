package com.sudocar.launcher.dialog

import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.sudocar.launcher.R

class QuickSwitchDialog(context: Context) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    private var quickApps: List<SettingsDialog.QuickAppInfo> = emptyList()
    private var currentIndex = 0
    private var onLaunchListener: ((String) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null
    private var isViewInitialized = false

    fun setOnLaunchListener(listener: (String) -> Unit) {
        onLaunchListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_quick_switch)

        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            setDimAmount(0.6f)
        }

        setCanceledOnTouchOutside(false)
        isViewInitialized = true
        loadApps()
    }

    private fun loadApps() {
        quickApps = SettingsDialog.getAllQuickApps(context)
        if (quickApps.isEmpty()) {
            quickApps = listOf(SettingsDialog.QuickAppInfo(context.packageName, "桌面"))
        }
        currentIndex = 0
        updateDisplay()
    }

    override fun show() {
        loadApps()
        applyTheme()
        super.show()
        scheduleDismiss()
    }

    private fun applyTheme() {
        val isNightMode = SettingsDialog.isNightModeEnabled(context)
        window?.setBackgroundDrawableResource(
            if (isNightMode) android.R.color.transparent else android.R.color.background_light
        )
        
        findViewById<TextView>(R.id.tv_hint)?.setTextColor(
            context.getColor(if (isNightMode) R.color.text_secondary else R.color.text_primary)
        )
    }

    private fun updateDisplay() {
        if (!isViewInitialized) return
        
        val layoutApps = findViewById<LinearLayout>(R.id.root) ?: return
        layoutApps.removeAllViews()

        if (quickApps.isEmpty()) return

        val app = quickApps[currentIndex]
        val appView = createAppView(app.packageName, app.name)
        layoutApps.addView(appView)

        appView.alpha = 1.0f
        appView.scaleX = 1.2f
        appView.scaleY = 1.2f
    }

    private fun createAppView(packageName: String, name: String): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(160, 180).apply {
                marginStart = 20
                marginEnd = 20
            }
        }

        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(100, 100)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(getAppIcon(packageName))
        }

        val nameView = TextView(context).apply {
            text = name
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 16f
            gravity = Gravity.CENTER
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12
            }
        }

        container.addView(iconView)
        container.addView(nameView)

        if (packageName == quickApps.getOrNull(currentIndex)?.packageName) {
            container.setBackgroundResource(R.drawable.bg_popup_item)
        }

        return container
    }

    private fun getAppIcon(packageName: String): Drawable? {
        return try {
            val pm = context.packageManager
            if (packageName == context.packageName) {
                pm.getApplicationIcon(pm.getApplicationInfo(packageName, 0))
            } else {
                pm.getApplicationIcon(packageName)
            }
        } catch (e: Exception) {
            context.getDrawable(android.R.drawable.ic_menu_help)
        }
    }

    fun switchLeft(): Boolean {
        if (quickApps.isEmpty()) return false
        currentIndex = if (currentIndex > 0) currentIndex - 1 else quickApps.size - 1
        updateDisplay()
        scheduleDismiss()
        return true
    }

    fun switchRight(): Boolean {
        if (quickApps.isEmpty()) return false
        currentIndex = (currentIndex + 1) % quickApps.size
        updateDisplay()
        scheduleDismiss()
        return true
    }

    fun confirmLaunch(): Boolean {
        if (!isShowing || quickApps.isEmpty()) return false

        val app = quickApps.getOrNull(currentIndex) ?: return false
        onLaunchListener?.invoke(app.packageName)
        dismiss()
        return true
    }

    fun getCurrentPackage(): String? {
        return quickApps.getOrNull(currentIndex)?.packageName
    }

    fun getCurrentIndex(): Int = currentIndex

    fun setCurrentIndex(index: Int) {
        if (index in quickApps.indices) {
            currentIndex = index
            updateDisplay()
        }
    }

    private fun scheduleDismiss() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = Runnable {
            if (isShowing) {
                dismiss()
            }
        }
        handler.postDelayed(dismissRunnable!!, 3000)
    }

    override fun dismiss() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        super.dismiss()
    }
}
