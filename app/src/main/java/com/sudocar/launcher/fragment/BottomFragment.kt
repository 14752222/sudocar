package com.sudocar.launcher.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sudocar.launcher.MainActivity
import com.sudocar.launcher.R
import com.sudocar.launcher.databinding.FragmentBottomBinding

class BottomFragment : Fragment() {

    val binding: FragmentBottomBinding by lazy {
        FragmentBottomBinding.inflate(layoutInflater)
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?

    ): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 在这里处理 WindowInsets，此时 view 已经创建好了
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 给整个底部 Fragment 的根视图设置 padding
            // 这样内部的 Button 就不会被导航栏遮挡
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)

            insets
        }


    }

}