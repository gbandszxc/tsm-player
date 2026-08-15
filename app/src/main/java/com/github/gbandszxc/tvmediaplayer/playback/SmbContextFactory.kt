package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator

object SmbContextFactory {
    /**
     * jcifs-ng 的连接池按 CIFSContext 隔离：每次新建 context 都要重新走
     * TCP 连接 + SMB 协商 + 会话建立。按配置缓存 context 可让目录浏览、
     * 元数据探测、封面加载复用同一批 SMB 连接，显著减少网络往返。
     * SmbConfig 是包含凭据的 data class，equals/hashCode 保证不同
     * 主机/账号不会混用连接；context 本身线程安全。
     */
    private val cache = ConcurrentHashMap<SmbConfig, CIFSContext>()

    fun build(config: SmbConfig): CIFSContext =
        cache.getOrPut(config) { createContext(config) }

    private fun createContext(config: SmbConfig): CIFSContext {
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.responseTimeout", "10000")
            setProperty("jcifs.smb.client.connTimeout", "10000")
            setProperty("jcifs.smb.client.soTimeout", "10000")
            if (config.smb1Enabled) {
                setProperty("jcifs.smb.client.minVersion", "SMB1")
            } else {
                setProperty("jcifs.smb.client.minVersion", "SMB202")
            }
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
        }
        val base = BaseContext(PropertyConfiguration(properties))
        return if (config.guest) {
            base.withCredentials(NtlmPasswordAuthenticator("", ""))
        } else {
            base.withCredentials(NtlmPasswordAuthenticator("", config.username.trim(), config.password))
        }
    }
}
