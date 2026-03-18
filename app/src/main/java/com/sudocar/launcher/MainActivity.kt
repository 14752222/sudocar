package com.sudocar.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sudocar.launcher.databinding.ActivityMainBinding
import com.sudocar.launcher.dialog.QuickSwitchDialog
import com.sudocar.launcher.dialog.SettingsDialog
import com.sudocar.launcher.fragment.BottomFragment
import com.sudocar.launcher.fragment.LeftFragment
import com.sudocar.launcher.fragment.MiddleFragment
import com.sudocar.launcher.fragment.RightFragment
import com.sudocar.launcher.utils.KeyEventManager

class MainActivity : AppCompatActivity(), 
    BottomFragment.OnSwapClickListener, 
    BottomFragment.OnNightModeChangeListener,
    MiddleFragment.OnSwapClickListener,
    SettingsDialog.OnNightModeChangeListener,
    SettingsDialog.OnBackgroundChangeListener,
    SettingsDialog.OnTestQuickSwitchListener {

    private val leftFragment = LeftFragment()
    private val middleFragment = MiddleFragment()
    private val rightFragment = RightFragment()
    private val bottomFragment = BottomFragment()

    private var isSwapped = false
    private var isSwapping = false

    private lateinit var keyEventManager: KeyEventManager
    private var quickSwitchDialog: QuickSwitchDialog? = null

    val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    // 在 onCreate 之前设置主题
    private fun applyThemeBeforeCreate() {
        val isNightMode = SettingsDialog.isNightModeEnabled(this)
        Log.d(TAG, "applyThemeBeforeCreate: nightMode=$isNightMode")
        
        if (isNightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 先设置主题，再调用 super.onCreate
        applyThemeBeforeCreate()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        initWindowManager()

        keyEventManager = KeyEventManager(object : KeyEventManager.KeyCallback {
            override fun onMediaKey(keyCode: Int): Boolean {
                return handleMediaKey(keyCode)
            }
        })
        keyEventManager.start(this)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().apply {
                add(R.id.container_left, leftFragment, "LEFT")
                add(R.id.container_middle, middleFragment, "MIDDLE")
                add(R.id.container_right, rightFragment, "RIGHT")
                add(R.id.container_bottom, bottomFragment, "BOTTOM")
            }.commit()
        } else {
            isSwapped = savedInstanceState.getBoolean("KEY_IS_SWAPPED", false)
            if (isSwapped) {
                binding.root.post { applySwapLayout(true) }
            }
        }

        setupWindowInsets()
        binding.root.post {
            setupSwapListener()
            setupQuickSwitchListener()
            updateSwapButtonState()
        }

        checkAutoStart()
    }

    private fun initWindowManager() {
        if (SettingsDialog.isAutoStartEnabled(this)) {
            Log.i(TAG, "开机自启动已开启")
        }
    }

    private fun checkAutoStart() {
        if (SettingsDialog.isAutoStartEnabled(this)) {
            Log.d(TAG, "自启动权限已开启")
        }
    }

    /** 应用背景色和字体颜色 */
    fun applyBackground() {
        val isNightMode = SettingsDialog.isNightModeEnabled(this)
        val customColor = SettingsDialog.getWallpaperColor(this)

        Log.d(TAG, "applyBackground: nightMode=$isNightMode, color=$customColor")

        // 从颜色资源获取
        val bgColor = if (isNightMode) {
            if (customColor != null) {
                try {
                    Color.parseColor(customColor)
                } catch (e: Exception) {
                    getColor(R.color.bg_dark)
                }
            } else {
                getColor(R.color.bg_dark)
            }
        } else {
            getColor(R.color.bg_dark)  // 日间模式白色
        }
        
        val textColor = getColor(R.color.text_primary)
        val bottomBgColor = getColor(R.color.bottom_bg)

        // 设置主背景
        binding.main.setBackgroundColor(bgColor)
        
        // 设置所有 Fragment 容器的背景
        findViewById<View>(R.id.container_left)?.setBackgroundColor(bgColor)
        findViewById<View>(R.id.container_middle)?.setBackgroundColor(bgColor)
        findViewById<View>(R.id.container_right)?.setBackgroundColor(bgColor)
        findViewById<View>(R.id.container_bottom)?.setBackgroundColor(bottomBgColor)

        // 保存当前颜色模式到全局，供 Fragment 读取
        getSharedPreferences(SettingsDialog.PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(SettingsDialog.KEY_NIGHT_MODE, isNightMode)
            .putInt("textColor", textColor)
            .apply()

        Log.d(TAG, "背景已设置: bg=${Integer.toHexString(bgColor)}, text=${Integer.toHexString(textColor)}")
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        keyEventManager.stop()
    }

    // SettingsDialog 回调 + BottomFragment 回调
    override fun onNightModeChanged() {
        Log.d(TAG, "onNightModeChanged")
        
        // 先设置主题模式
        val isNightMode = SettingsDialog.isNightModeEnabled(this)
        if (isNightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        
        // 重新创建 Activity，让系统自动切换主题颜色
        recreate()
    }

    override fun onBackgroundChanged() {
        Log.d(TAG, "onBackgroundChanged")
        // 重新创建 Activity 刷新背景
        recreate()
    }

    override fun onTestQuickSwitch() {
        Log.d(TAG, "onTestQuickSwitch")
        showQuickSwitch()
    }

    private fun handleMediaKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                rightFragment.handleKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                rightFragment.handleKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                rightFragment.handleKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                true
            }
            KeyEvent.KEYCODE_TV_INPUT,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                showQuickSwitch()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (quickSwitchDialog?.isShowing == true) {
                    quickSwitchDialog?.switchLeft()
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (quickSwitchDialog?.isShowing == true) {
                    quickSwitchDialog?.switchRight()
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (quickSwitchDialog?.isShowing == true) {
                    quickSwitchDialog?.confirmLaunch()
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_BACK -> {
                if (quickSwitchDialog?.isShowing == true) {
                    quickSwitchDialog?.dismiss()
                    true
                } else {
                    false
                }
            }
            else -> {
                rightFragment.handleKeyEvent(keyCode)
            }
        }
    }

    private fun showQuickSwitch() {
        if (quickSwitchDialog == null) {
            quickSwitchDialog = QuickSwitchDialog(this)
            quickSwitchDialog?.setOnLaunchListener { packageName ->
                launchApp(packageName)
            }
        }
        quickSwitchDialog?.show()
    }

    private fun launchApp(packageName: String) {
        if (packageName.isEmpty()) return

        if (packageName == this.packageName) {
            Log.d(TAG, "当前已在桌面")
            return
        }

        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.i(TAG, "启动应用: $packageName")
            } else {
                Log.w(TAG, "应用未安装: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动应用失败: ${e.message}")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (event != null && keyEventManager.dispatchKeyEvent(event)) {
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return if (event != null && keyEventManager.dispatchKeyEvent(event)) {
            true
        } else {
            super.onKeyUp(keyCode, event)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("KEY_IS_SWAPPED", isSwapped)
    }

    private fun setupWindowInsets() {
        val bottomContainerView = findViewById<View>(R.id.container_bottom) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(bottomContainerView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }
    }

    private fun setupSwapListener() {
        val bottomView = bottomFragment.view
        Log.e("app", "setupSwapListener: ")
        if (bottomView != null) {
            val swapBtn = bottomView.findViewById<Button>(R.id.btn_swap)
            swapBtn?.setOnClickListener {
                toggleLayout()
            }
        }
    }

    private fun setupQuickSwitchListener() {
        val bottomView = bottomFragment.view
        if (bottomView != null) {
            bottomFragment.setOnQuickSwitchListener {
                showQuickSwitch()
            }
        }
    }

    private fun toggleLayout() {
        if (isSwapping) return
        if (supportFragmentManager.isStateSaved) return

        isSwapping = true
        isSwapped = !isSwapped
        applySwapLayout(isSwapped)
        isSwapping = false
        updateSwapButtonState()
        Log.d("app", "Layout Toggled. Is Swapped: $isSwapped")
    }

    private fun applySwapLayout(targetSwapped: Boolean) {
        val constraintLayout = binding.main
        val containerMiddleId = R.id.container_middle
        val containerRightId = R.id.container_right
        val containerLeftId = R.id.container_left

        val WIDTH_LARGE = 0.68f
        val WIDTH_SMALL = 0.22f

        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        if (targetSwapped) {
            constraintSet.connect(containerRightId, ConstraintSet.START, containerLeftId, ConstraintSet.END, 0)
            constraintSet.connect(containerRightId, ConstraintSet.END, containerMiddleId, ConstraintSet.START, 0)
            constraintSet.constrainPercentWidth(containerRightId, WIDTH_SMALL)

            constraintSet.connect(containerMiddleId, ConstraintSet.START, containerRightId, ConstraintSet.END, 0)
            constraintSet.connect(containerMiddleId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
            constraintSet.constrainPercentWidth(containerMiddleId, WIDTH_LARGE)
        } else {
            constraintSet.connect(containerMiddleId, ConstraintSet.START, containerLeftId, ConstraintSet.END, 0)
            constraintSet.connect(containerMiddleId, ConstraintSet.END, containerRightId, ConstraintSet.START, 0)
            constraintSet.constrainPercentWidth(containerMiddleId, WIDTH_LARGE)

            constraintSet.connect(containerRightId, ConstraintSet.START, containerMiddleId, ConstraintSet.END, 0)
            constraintSet.connect(containerRightId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
            constraintSet.constrainPercentWidth(containerRightId, WIDTH_SMALL)
        }

        constraintSet.applyTo(constraintLayout)
        Log.d("app", "ConstraintSet Applied. Swapped: $targetSwapped")
    }

    private fun updateSwapButtonState() {
        val bottomView = bottomFragment.view ?: return
        val swapBtn = bottomView.findViewById<Button>(R.id.btn_swap)
        swapBtn?.text = if (isSwapped) "恢复大屏地图" else "切换大屏地图"
    }

    override fun onSwapClick() {
        toggleLayout()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
