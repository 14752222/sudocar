package com.sudocar.launcher.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sudocar.launcher.R
import com.sudocar.launcher.model.AppInfo

// 定义点击回调接口
typealias OnAppClickListener = (AppInfo) -> Unit

class AppAdapter(
    private val onItemClick: OnAppClickListener
) : ListAdapter<AppInfo, AppAdapter.AppViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_app_icon)
        private val tvName: TextView = itemView.findViewById(R.id.tv_app_name)

        fun bind(app: AppInfo, clickListener: OnAppClickListener) {
            tvName.text = app.appName
            ivIcon.setImageDrawable(app.icon)

            // 设置点击事件
            itemView.setOnClickListener {
                clickListener(app)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.appName == newItem.appName && oldItem.icon == newItem.icon
        }
    }
}