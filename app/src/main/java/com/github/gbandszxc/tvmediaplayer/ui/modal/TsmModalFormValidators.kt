package com.github.gbandszxc.tvmediaplayer.ui.modal

/**
 * 表单字段校验工具集。
 *
 * 提供各业务场景的校验方法，返回 null 表示通过，返回错误文案表示失败。
 * 校验器不依赖 Android Context，方便单元测试。
 */
object TsmModalFormValidators {

    /**
     * 校验播放列表名称。
     *
     * @param name      用户输入的名称
     * @param existing  已存在的播放列表名称集合
     * @return 错误文案，null 表示通过
     */
    fun validatePlaylistName(name: String, existing: Set<String>): String? {
        val normalized = name.trim()
        if (normalized.isBlank()) return "请输入播放列表名称"
        if (normalized in existing) return "播放列表已存在"
        return null
    }

    /**
     * 校验歌词字号。
     *
     * @param value 用户输入的字号值
     * @param min   允许的最小值
     * @param max   允许的最大值
     * @return 错误文案，null 表示通过
     */
    fun validateLyricsFont(value: Int?, min: Int, max: Int): String? {
        if (value == null) return "请输入有效数字"
        if (value !in min..max) return "字号范围需在 ${min}-${max}sp"
        return null
    }

    /**
     * 校验歌词行距倍率。
     *
     * @param value 用户输入的行距值
     * @param min   允许的最小值
     * @param max   允许的最大值
     * @return 错误文案，null 表示通过
     */
    fun validateLyricsSpacing(value: Float?, min: Float, max: Float): String? {
        if (value == null) return "请输入有效数字"
        if (value < min || value > max) {
            return "间距范围需在 ${"%.1f".format(min)} - ${"%.1f".format(max)}"
        }
        return null
    }

    /**
     * 校验 SMB 服务器地址。
     *
     * @param value 用户输入的主机地址
     * @return 错误文案，null 表示通过
     */
    fun validateSmbHost(value: String): String? {
        if (value.trim().isBlank()) return "请输入 SMB 服务器地址"
        return null
    }
}
