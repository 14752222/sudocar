package com.sudocar.launcher.utils

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.sudocar.launcher.R

/**
 * 按键测试覆盖层 - 车机调试工具
 * 全局显示所有接收到的按键事件，用于排查方向盘按键映射
 */
class KeyTestOverlay(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isShown = false

    private val handler = Handler(Looper.getMainLooper())
    private val keyLog = mutableListOf<KeyLogEntry>()
    private val maxLogSize = 50

    private var logContainer: LinearLayout? = null

    data class KeyLogEntry(
        val keyCode: Int,
        val keyName: String,
        val action: String,
        val deviceId: Int,
        val timestamp: Long,
        val extra: String?
    )

    fun show() {
        if (isShown) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 100
        }

        overlayView = createOverlayView()
        windowManager?.addView(overlayView, params)
        isShown = true
    }

    fun hide() {
        if (!isShown) return
        try {
            windowManager?.removeView(overlayView)
        } catch (_: Exception) {}
        overlayView = null
        isShown = false
    }

    fun toggle() {
        if (isShown) hide() else show()
    }

    fun isVisible() = isShown

    fun logKeyEvent(keyCode: Int, action: Int, deviceId: Int, repeatCount: Int = 0, extra: String? = null) {
        val keyName = getKeyName(keyCode)
        val actionName = when (action) {
            android.view.KeyEvent.ACTION_DOWN -> "按下"
            android.view.KeyEvent.ACTION_UP -> "松开"
            else -> "未知"
        }

        val entry = KeyLogEntry(
            keyCode = keyCode,
            keyName = keyName,
            action = actionName,
            deviceId = deviceId,
            timestamp = System.currentTimeMillis(),
            extra = extra ?: (if (repeatCount > 0) "重复${repeatCount}" else null)
        )

        keyLog.add(0, entry)
        if (keyLog.size > maxLogSize) {
            keyLog.removeAt(keyLog.size - 1)
        }

        refreshDisplay()
    }

    private fun createOverlayView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6000000"))
            setPadding(16, 16, 16, 16)
        }

        // 标题栏
        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(12, 8, 12, 8)
        }

        val title = TextView(context).apply {
            text = "🔑 按键监听 (长按关闭)"
            setTextColor(Color.YELLOW)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(16, 4, 16, 4)
            setOnClickListener { hide() }
        }

        titleBar.addView(title)
        titleBar.addView(closeBtn)
        container.addView(titleBar)

        // 按键说明
        val hint = TextView(context).apply {
            text = "KEYCODE: $keyCodeHint"
            setTextColor(Color.parseColor("#888888"))
            textSize = 11f
            setPadding(0, 4, 0, 8)
        }
        container.addView(hint)

        // 日志区域
        logContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(300)
            )
        }

        val scrollView = ScrollView(context).apply {
            addView(logContainer)
        }
        container.addView(scrollView)

        // 长按关闭
        container.setOnLongClickListener {
            hide()
            true
        }

        return container
    }

    private fun refreshDisplay() {
        if (!isShown || logContainer == null) return

        handler.post {
            logContainer?.removeAllViews()

            keyLog.take(20).forEachIndexed { index, entry ->
                val itemView = createLogItem(entry, index == 0)
                logContainer?.addView(itemView)
            }
        }
    }

    private fun createLogItem(entry: KeyLogEntry, isLatest: Boolean): View {
        val bgColor = when (entry.action) {
            "按下" -> if (isLatest) Color.parseColor("#1A4D1A") else Color.parseColor("#0D260D")
            "松开" -> if (isLatest) Color.parseColor("#4D1A1A") else Color.parseColor("#260D0D")
            else -> Color.TRANSPARENT
        }

        return TextView(context).apply {
            text = buildString {
                append("[${entry.action}] ")
                append(entry.keyName)
                append(" (")
                append(entry.keyCode)
                append(")")
                entry.extra?.let { append(" ×$it") }
                append(" | 设备:$entry.deviceId")
            }
            setTextColor(if (isLatest) Color.WHITE else Color.parseColor("#AAAAAA"))
            textSize = if (isLatest) 15f else 13f
            setBackgroundColor(bgColor)
            setPadding(12, 8, 12, 8)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (isLatest) 0 else 1
            }
            layoutParams = params
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val keyCodeHint = """
常用按键码参考:
MEDIA_PREV=87 | MEDIA_NEXT=88 | MEDIA_PLAY_PAUSE=85
TV_INPUT=170 | BUTTON_MODE=82 | APP_SWITCH=187
DPAD_LEFT=21 | DPAD_RIGHT=22 | DPAD_CENTER=23
VOL_UP=24 | VOL_DOWN=25 | BACK=4 | HOME=3
"""

        /**
         * 获取按键的友好名称
         */
        fun getKeyName(keyCode: Int): String {
            return when (keyCode) {
                // 多媒体按键
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> "MEDIA_PLAY"
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> "MEDIA_PAUSE"
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "MEDIA_PLAY_PAUSE"
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> "MEDIA_STOP"
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> "MEDIA_NEXT"
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "MEDIA_PREVIOUS"
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> "MEDIA_REWIND"
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "MEDIA_FAST_FORWARD"
                android.view.KeyEvent.KEYCODE_HEADSETHOOK -> "HEADSETHOOK"
                android.view.KeyEvent.KEYCODE_MUTE -> "MUTE"

                // 导航/方向键
                android.view.KeyEvent.KEYCODE_DPAD_UP -> "DPAD_UP"
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> "DPAD_DOWN"
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> "DPAD_LEFT"
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> "DPAD_RIGHT"
                android.view.KeyEvent.KEYCODE_DPAD_CENTER -> "DPAD_CENTER"

                // 系统按键
                android.view.KeyEvent.KEYCODE_BACK -> "BACK"
                android.view.KeyEvent.KEYCODE_HOME -> "HOME"
                android.view.KeyEvent.KEYCODE_MENU -> "MENU"
                android.view.KeyEvent.KEYCODE_POWER -> "POWER"
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> "VOL_UP"
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> "VOL_DOWN"

                // 车机专用
                android.view.KeyEvent.KEYCODE_TV_INPUT -> "TV_INPUT"
                android.view.KeyEvent.KEYCODE_BUTTON_MODE -> "BUTTON_MODE"
                android.view.KeyEvent.KEYCODE_APP_SWITCH -> "APP_SWITCH"

                // 方向盘按键
                android.view.KeyEvent.KEYCODE_CHANNEL_UP -> "CHANNEL_UP"
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> "CHANNEL_DOWN"

                // 通用按钮
                android.view.KeyEvent.KEYCODE_BUTTON_1 -> "BTN_1"
                android.view.KeyEvent.KEYCODE_BUTTON_2 -> "BTN_2"
                android.view.KeyEvent.KEYCODE_BUTTON_3 -> "BTN_3"
                android.view.KeyEvent.KEYCODE_BUTTON_4 -> "BTN_4"
                android.view.KeyEvent.KEYCODE_BUTTON_5 -> "BTN_5"
                android.view.KeyEvent.KEYCODE_BUTTON_6 -> "BTN_6"
                android.view.KeyEvent.KEYCODE_BUTTON_7 -> "BTN_7"
                android.view.KeyEvent.KEYCODE_BUTTON_8 -> "BTN_8"
                android.view.KeyEvent.KEYCODE_BUTTON_9 -> "BTN_9"
                android.view.KeyEvent.KEYCODE_BUTTON_10 -> "BTN_10"
                android.view.KeyEvent.KEYCODE_BUTTON_11 -> "BTN_11"
                android.view.KeyEvent.KEYCODE_BUTTON_12 -> "BTN_12"
                android.view.KeyEvent.KEYCODE_BUTTON_A -> "BTN_A"
                android.view.KeyEvent.KEYCODE_BUTTON_B -> "BTN_B"
                android.view.KeyEvent.KEYCODE_BUTTON_C -> "BTN_C"
                android.view.KeyEvent.KEYCODE_BUTTON_X -> "BTN_X"
                android.view.KeyEvent.KEYCODE_BUTTON_Y -> "BTN_Y"
                android.view.KeyEvent.KEYCODE_BUTTON_Z -> "BTN_Z"
                android.view.KeyEvent.KEYCODE_BUTTON_L1 -> "BTN_L1"
                android.view.KeyEvent.KEYCODE_BUTTON_R1 -> "BTN_R1"
                android.view.KeyEvent.KEYCODE_BUTTON_L2 -> "BTN_L2"
                android.view.KeyEvent.KEYCODE_BUTTON_R2 -> "BTN_R2"
                android.view.KeyEvent.KEYCODE_BUTTON_THUMBL -> "BTN_THUMBL"
                android.view.KeyEvent.KEYCODE_BUTTON_THUMBR -> "BTN_THUMBR"
                android.view.KeyEvent.KEYCODE_BUTTON_START -> "BTN_START"
                android.view.KeyEvent.KEYCODE_BUTTON_SELECT -> "BTN_SELECT"

                // Tab/CarThing
                android.view.KeyEvent.KEYCODE_TAB -> "TAB"
                android.view.KeyEvent.KEYCODE_PROG_GREEN -> "PROG_GREEN"
                android.view.KeyEvent.KEYCODE_PROG_RED -> "PROG_RED"
                android.view.KeyEvent.KEYCODE_PROG_YELLOW -> "PROG_YELLOW"
                android.view.KeyEvent.KEYCODE_PROG_BLUE -> "PROG_BLUE"

                // 其他
                android.view.KeyEvent.KEYCODE_ENTER -> "ENTER"
                android.view.KeyEvent.KEYCODE_SPACE -> "SPACE"
                android.view.KeyEvent.KEYCODE_ESCAPE -> "ESCAPE"
                android.view.KeyEvent.KEYCODE_PAGE_UP -> "PAGE_UP"
                android.view.KeyEvent.KEYCODE_PAGE_DOWN -> "PAGE_DOWN"

                else -> "KEYCODE_$keyCode"
            }
        }
    }
}
