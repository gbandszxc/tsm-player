package com.github.gbandszxc.tvmediaplayer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppUpdateManagerTest {
    @Test
    fun `compare versions by numeric parts`() {
        assertEquals(1, AppUpdateManager.compareVersions("1.0.6", "1.0.5"))
        assertEquals(0, AppUpdateManager.compareVersions("v1.0.5", "1.0.5"))
        assertEquals(-1, AppUpdateManager.compareVersions("1.0.5", "1.0.6"))
        assertEquals(1, AppUpdateManager.compareVersions("1.10.0", "1.9.9"))
    }

    @Test
    fun `finds release apk for current abi and newer version`() {
        val html = """
            <a href="/gbandszxc/tsm-player/releases/download/v1.0.6/tsm-player-release-armeabi-v7a-1.0.6.apk">armv7</a>
            <a href="/gbandszxc/tsm-player/releases/download/v1.0.6/tsm-player-release-arm64-v8a-1.0.6.apk">arm64</a>
        """.trimIndent()

        val update = AppUpdateManager.findMatchingAsset(
            currentVersionName = "1.0.5",
            currentAbi = "arm64-v8a",
            releaseVersionName = "v1.0.6",
            expandedAssetsHtml = html
        )

        assertNotNull(update)
        assertEquals("1.0.6", update?.versionName)
        assertEquals("tsm-player-release-arm64-v8a-1.0.6.apk", update?.assetName)
        assertEquals(
            "https://github.com/gbandszxc/tsm-player/releases/download/v1.0.6/tsm-player-release-arm64-v8a-1.0.6.apk",
            update?.downloadUrl
        )
    }

    @Test
    fun `ignores same or older release`() {
        val html = """
            <a href="/gbandszxc/tsm-player/releases/download/v1.0.5/tsm-player-release-arm64-v8a-1.0.5.apk">arm64</a>
        """.trimIndent()

        val update = AppUpdateManager.findMatchingAsset(
            currentVersionName = "1.0.5",
            currentAbi = "arm64-v8a",
            releaseVersionName = "1.0.5",
            expandedAssetsHtml = html
        )

        assertNull(update)
    }
}
