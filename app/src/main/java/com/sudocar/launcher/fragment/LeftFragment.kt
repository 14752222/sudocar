package com.sudocar.launcher.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sudocar.launcher.R
import com.sudocar.launcher.databinding.FragmentLeftBinding

class LeftFragment : Fragment() {
    val binding: FragmentLeftBinding by lazy {
        FragmentLeftBinding.inflate(layoutInflater)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return binding.root
    }

}