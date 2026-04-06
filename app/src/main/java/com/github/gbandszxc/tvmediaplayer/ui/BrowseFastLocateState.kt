package com.github.gbandszxc.tvmediaplayer.ui

data class BrowseFastLocateState(
    val totalCount: Int,
    val currentIndex: Int,
    val visibleWindowSize: Int
) {
    val progressPercent: Int
        get() {
            if (totalCount <= 1) return 0
            val clampedIndex = currentIndex.coerceIn(0, (totalCount - 1).coerceAtLeast(0))
            val percent = (clampedIndex.toFloat() * 100f / (totalCount - 1)).toInt()
            return percent.coerceIn(0, 100)
        }
}
