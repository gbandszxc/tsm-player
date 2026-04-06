package com.github.gbandszxc.tvmediaplayer.ui

class BrowseFastLocateCalculator {

    fun canEnter(state: BrowseFastLocateState): Boolean =
        state.visibleWindowSize > 0 &&
            state.totalCount >= state.visibleWindowSize * 2

    fun jumpPage(state: BrowseFastLocateState, forward: Boolean): BrowseFastLocateState {
        val direction = if (forward) 1 else -1
        val target = state.currentIndex + direction * state.visibleWindowSize
        return state.copy(currentIndex = clamp(target, state.totalCount))
    }

    fun jumpSegment(state: BrowseFastLocateState, forward: Boolean): BrowseFastLocateState {
        val direction = if (forward) 1 else -1
        val delta = maxOf(1, (state.totalCount * 0.1f).toInt())
        val target = state.currentIndex + direction * delta
        return state.copy(currentIndex = clamp(target, state.totalCount))
    }

    private fun clamp(index: Int, totalCount: Int): Int =
        index.coerceIn(0, (totalCount - 1).coerceAtLeast(0))
}
