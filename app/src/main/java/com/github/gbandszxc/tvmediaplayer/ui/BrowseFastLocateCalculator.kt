package com.github.gbandszxc.tvmediaplayer.ui

object BrowseFastLocateCalculator {

    fun canEnter(totalCount: Int, visibleWindowSize: Int): Boolean =
        visibleWindowSize > 0 && totalCount >= visibleWindowSize * 2

    fun jumpPage(state: BrowseFastLocateState, direction: Int): BrowseFastLocateState {
        val target = state.currentIndex + direction * state.visibleWindowSize
        return state.copy(currentIndex = clamp(target, state.totalCount))
    }

    fun jumpSegment(state: BrowseFastLocateState, direction: Int): BrowseFastLocateState {
        val delta = maxOf(1, (state.totalCount * 0.1f).toInt())
        val target = state.currentIndex + direction * delta
        return state.copy(currentIndex = clamp(target, state.totalCount))
    }

    private fun clamp(index: Int, totalCount: Int): Int =
        index.coerceIn(0, (totalCount - 1).coerceAtLeast(0))
}
