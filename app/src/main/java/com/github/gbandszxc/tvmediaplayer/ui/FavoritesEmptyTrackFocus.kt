package com.github.gbandszxc.tvmediaplayer.ui

import android.widget.Button

object FavoritesEmptyTrackFocus {
    fun requestFallbackFocus(tracks: List<Any>, backButton: Button) {
        if (tracks.isNotEmpty()) return
        if (!backButton.requestFocus()) {
            backButton.isFocusableInTouchMode = true
            backButton.requestFocus()
        }
        backButton.post {
            if (!backButton.requestFocus()) {
                backButton.isFocusableInTouchMode = true
                backButton.requestFocus()
            }
        }
    }
}
