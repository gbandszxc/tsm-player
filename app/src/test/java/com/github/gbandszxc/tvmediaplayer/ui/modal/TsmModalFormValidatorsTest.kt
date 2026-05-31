package com.github.gbandszxc.tvmediaplayer.ui.modal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 表单校验逻辑的单元测试。
 * TDD 先写失败测试，再实现校验器。
 */
class TsmModalFormValidatorsTest {

    @Test
    fun `validatePlaylistName rejects blank and duplicate names`() {
        assertEquals(
            "请输入播放列表名称",
            TsmModalFormValidators.validatePlaylistName(" ", setOf("通勤")),
        )
        assertEquals(
            "播放列表已存在",
            TsmModalFormValidators.validatePlaylistName("通勤", setOf("通勤")),
        )
        assertNull(
            TsmModalFormValidators.validatePlaylistName("夜间播放", setOf("通勤")),
        )
    }

    @Test
    fun `validateLyricsFont keeps configured bounds`() {
        assertEquals(
            "字号范围需在 14-36sp",
            TsmModalFormValidators.validateLyricsFont(40, 14, 36),
        )
        assertNull(
            TsmModalFormValidators.validateLyricsFont(22, 14, 36),
        )
    }

    @Test
    fun `validateLyricsSpacing keeps configured bounds`() {
        assertEquals(
            "间距范围需在 0.8 - 2.0",
            TsmModalFormValidators.validateLyricsSpacing(0.5f, 0.8f, 2.0f),
        )
        assertNull(
            TsmModalFormValidators.validateLyricsSpacing(1.2f, 0.8f, 2.0f),
        )
    }

    @Test
    fun `validateSmbHost rejects blank host`() {
        assertEquals(
            "请输入 SMB 服务器地址",
            TsmModalFormValidators.validateSmbHost("  "),
        )
        assertNull(
            TsmModalFormValidators.validateSmbHost("192.168.0.10"),
        )
    }
}
