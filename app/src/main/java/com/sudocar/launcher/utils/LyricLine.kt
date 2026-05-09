package com.sudocar.launcher.utils

/**
 * 歌词行数据类
 */
data class LyricLine(
    val timeMs: Long,      // 歌词时间戳（毫秒）
    val text: String       // 歌词文本
)

/**
 * 歌词解析器
 */
object LyricParser {

    /**
     * 解析 LRC 格式歌词
     * @param lrcContent LRC 格式歌词内容
     * @return 按时间排序的歌词行列表
     */
    fun parse(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()

        lrcContent.split("\n").forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            // 匹配时间戳 [mm:ss.xx] 或 [mm:ss.xxx]
            val timeRegex = """\[(\d{2}):(\d{2})\.(\d{2,3})]""".toRegex()
            val matches = timeRegex.findAll(trimmed).toList()

            if (matches.isNotEmpty()) {
                // 提取歌词文本（去掉所有时间戳）
                val text = trimmed.replace(timeRegex, "").trim()

                // 每个时间戳对应一行歌词
                matches.forEach { match ->
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toLong()
                    val millis = match.groupValues[3].let {
                        if (it.length == 2) it.toLong() * 10 else it.toLong()
                    }

                    val timeMs = minutes * 60 * 1000 + seconds * 1000 + millis
                    lines.add(LyricLine(timeMs, text))
                }
            }
        }

        // 按时间排序
        return lines.sortedBy { it.timeMs }
    }

    /**
     * 获取当前时间对应的歌词行索引
     * @param lines 歌词行列表
     * @param currentTimeMs 当前播放时间（毫秒）
     * @return 当前歌词行索引，如果没有匹配返回 -1
     */
    fun getCurrentLineIndex(lines: List<LyricLine>, currentTimeMs: Long): Int {
        if (lines.isEmpty()) return -1

        var index = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= currentTimeMs) {
                index = i
            } else {
                break
            }
        }
        return index
    }
}
