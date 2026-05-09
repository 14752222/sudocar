package com.sudocar.launcher.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sudocar.launcher.R
import com.sudocar.launcher.utils.LyricLine

/**
 * 歌词列表适配器
 */
class LyricAdapter : RecyclerView.Adapter<LyricAdapter.LyricViewHolder>() {

    private val lyrics = mutableListOf<LyricLine>()
    private var currentLineIndex = -1
    private var normalColor: Int = 0
    private var highlightColor: Int = 0

    fun setColors(normal: Int, highlight: Int) {
        normalColor = normal
        highlightColor = highlight
    }

    fun setData(newLyrics: List<LyricLine>) {
        lyrics.clear()
        lyrics.addAll(newLyrics)
        currentLineIndex = -1
        notifyDataSetChanged()
    }

    fun setCurrentLine(index: Int) {
        if (index == currentLineIndex || index < 0 || index >= lyrics.size) return

        val oldIndex = currentLineIndex
        currentLineIndex = index

        // 局部刷新
        if (oldIndex >= 0 && oldIndex < lyrics.size) {
            notifyItemChanged(oldIndex)
        }
        notifyItemChanged(currentLineIndex)
    }

    fun getCurrentLineIndex(): Int = currentLineIndex

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lyric_line, parent, false)
        return LyricViewHolder(view)
    }

    override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
        holder.bind(lyrics[position], position == currentLineIndex)
    }

    override fun getItemCount(): Int = lyrics.size

    inner class LyricViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLyric: TextView = itemView.findViewById(R.id.tv_lyric_line)

        fun bind(lyricLine: LyricLine, isCurrent: Boolean) {
            tvLyric.text = lyricLine.text
            tvLyric.setTextColor(if (isCurrent) highlightColor else normalColor)
            tvLyric.alpha = if (isCurrent) 1.0f else 0.6f
            tvLyric.textSize = if (isCurrent) 15f else 13f
        }
    }
}
