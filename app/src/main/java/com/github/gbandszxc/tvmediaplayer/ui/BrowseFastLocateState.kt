package com.github.gbandszxc.tvmediaplayer.ui

data class BrowseFastLocateState(
    val totalCount: Int,
    val currentIndex: Int,
    val visibleWindowSize: Int
) {
    val progressPercent: Int
        get() {
            if (totalCount <= 1) return 0
            val percent = (currentIndex.toFloat() * 100f / (totalCount - 1)).toInt()
            return percent.coerceIn(0, 100)
        }
}
