package com.sudocar.launcher.fragment

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.sudocar.launcher.R
import com.sudocar.launcher.databinding.FragmentMiddleBinding
import com.sudocar.launcher.utils.QQMusicLyricController

class MiddleFragment : Fragment() {
    private val binding: FragmentMiddleBinding by lazy {
        FragmentMiddleBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e("TAG ==>", "onCreate: ", )
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}