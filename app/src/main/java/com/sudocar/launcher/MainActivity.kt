package com.sudocar.launcher

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sudocar.launcher.databinding.ActivityMainBinding
import com.sudocar.launcher.fragment.BottomFragment
import com.sudocar.launcher.fragment.LeftFragment
import com.sudocar.launcher.fragment.MiddleFragment
import com.sudocar.launcher.fragment.RightFragment

class MainActivity : AppCompatActivity() {

    // Fragment 实例
    private val leftFragment = LeftFragment()
    private val middleFragment = MiddleFragment() // 假设这是地图
    private val rightFragment = RightFragment()   // 假设这是音乐
    private val bottomFragment = BottomFragment()

    // 状态标记
    private var isSwapped = false
    private var isSwapping = false

    // ViewBinding
    val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        // 初始化 Fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().apply {
                add(R.id.container_left, leftFragment, "LEFT")
                add(R.id.container_middle, middleFragment, "MIDDLE")
                add(R.id.container_right, rightFragment, "RIGHT")
                add(R.id.container_bottom, bottomFragment, "BOTTOM")
            }.commit()
        } else {
            // 恢复状态
            isSwapped = savedInstanceState.getBoolean("KEY_IS_SWAPPED", false)
            // 如果恢复时是交换状态，需要立即应用一次布局（因为 XML 是默认状态）
            if (isSwapped) {
                binding.root.post { applySwapLayout(true) }
            }
        }

        // 处理系统栏内边距
        setupWindowInsets()

        // 设置按钮监听 (延迟到视图绘制完成)
        binding.root.post {
            setupSwapListener()
            // 如果初始就是交换状态，确保按钮文字正确
            updateSwapButtonState()
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
        if (bottomView != null) {
            val swapBtn = bottomView.findViewById<Button>(R.id.btn_swap)
            swapBtn?.setOnClickListener {
                toggleLayout()
            }
        }
    }

    /**
     * 点击切换入口
     */
    private fun toggleLayout() {
        if (isSwapping) return
        if (supportFragmentManager.isStateSaved) return

        isSwapping = true

        // 切换状态标志
        isSwapped = !isSwapped

        // 应用新的布局配置
        applySwapLayout(isSwapped)

        isSwapping = false
        updateSwapButtonState()

        Log.d("app", "Layout Toggled. Is Swapped: $isSwapped")
    }

    /**
     * 核心逻辑：根据目标状态，创建全新的 LayoutParams 并应用
     * @param targetSwapped true = [左]-[右(小)]
    -[中(大)], false = [左]-[中(大)]-[右(小)]
     */
    private fun applySwapLayout(targetSwapped: Boolean) {
        val constraintLayout = binding.main
        val containerMiddleId = R.id.container_middle
        val containerRightId = R.id.container_right
        val containerLeftId = R.id.container_left

        // 定义固定尺寸比例
        val WIDTH_LARGE = 0.68f
        val WIDTH_SMALL = 0.22f

        // 创建 ConstraintSet 并克隆当前布局状态
        val constraintSet = androidx.constraintlayout.widget.ConstraintSet()
        constraintSet.clone(constraintLayout)

        if (targetSwapped) {
            // === 目标状态：[Left] - [Right(小)] - [Middle(大)] ===

            // 1. 配置 Right (去中间，靠 Left 和 Middle，宽度小)
            constraintSet.connect(containerRightId, ConstraintSet.START, containerLeftId, ConstraintSet.END, 0)
            constraintSet.connect(containerRightId, ConstraintSet.END, containerMiddleId, ConstraintSet.START, 0)
            constraintSet.constrainPercentWidth(containerRightId, WIDTH_SMALL)

            // 2. 配置 Middle (去最右，靠 Right 和 Parent，宽度大)
            constraintSet.connect(containerMiddleId, ConstraintSet.START, containerRightId, ConstraintSet.END, 0)
            constraintSet.connect(containerMiddleId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
            constraintSet.constrainPercentWidth(containerMiddleId, WIDTH_LARGE)

        } else {
            // === 目标状态：[Left] - [Middle(大)] - [Right(小)] (默认) ===

            // 1. 配置 Middle (在中间，靠 Left 和 Right，宽度大)
            constraintSet.connect(containerMiddleId, ConstraintSet.START, containerLeftId, ConstraintSet.END, 0)
            constraintSet.connect(containerMiddleId, ConstraintSet.END, containerRightId, ConstraintSet.START, 0)
            constraintSet.constrainPercentWidth(containerMiddleId, WIDTH_LARGE)

            // 2. 配置 Right (在最右，靠 Middle 和 Parent，宽度小)
            constraintSet.connect(containerRightId, ConstraintSet.START, containerMiddleId, ConstraintSet.END, 0)
            constraintSet.connect(containerRightId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
            constraintSet.constrainPercentWidth(containerRightId, WIDTH_SMALL)
        }

        // 【关键】：应用 ConstraintSet 到布局
        // 第二个参数 true 表示开启动画过渡（可选，改为 false 则瞬间切换）
        constraintSet.applyTo(constraintLayout)

        Log.d("app", "ConstraintSet Applied. Swapped: $targetSwapped")
    }

    private fun updateSwapButtonState() {
        val bottomView = bottomFragment.view ?: return
        val swapBtn = bottomView.findViewById<Button>(R.id.btn_swap)
        swapBtn?.text = if (isSwapped) "恢复大屏地图" else "切换大屏地图"
    }
}