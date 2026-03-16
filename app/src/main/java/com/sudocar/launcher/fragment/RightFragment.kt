package com.sudocar.launcher.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sudocar.launcher.R
import com.sudocar.launcher.databinding.FragmentRightBinding

class RightFragment : Fragment() {
    val binding: FragmentRightBinding by lazy {
        FragmentRightBinding.inflate(layoutInflater)
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        return binding.root
    }
}