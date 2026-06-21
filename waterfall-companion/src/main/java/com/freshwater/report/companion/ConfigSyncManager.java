package com.freshwater.report.companion;

import com.freshwater.report.common.Protocol;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置同步：向上游 Velocity 主插件请求 config.yml / messages.yml，
 * 并把收到的内容镜像写入伴生插件自己的数据目录。
 */
public final class ConfigSyncManager {

    private static final long REQUEST_INTERVAL_MS = 30_000L;

    private final FreshwaterReportCompanion plugin;
    private volatile long lastRequest = 0L;

    public ConfigSyncManager(FreshwaterReportCompanion plugin) {
        this.plugin = plugin;
    }

    /** 距上次请求超过冷却时间时，借助一名在线玩家的连接向 Velocity 请求最新配置。 */
    public void requestIfStale(ProxiedPlayer player) {
        long now = System.currentTimeMillis();
        if (now - lastRequest < REQUEST_INTERVAL_MS) {
            return;
        }
        lastRequest = now;
        CompanionMessageListener.sendUpstream(player, Protocol.encode(Protocol.CONFIG_REQUEST));
    }

    /** 写入从主插件同步得到的配置镜像。 */
    public void write(String configYaml, String messagesYaml) {
        try {
            Path dir = plugin.getDataFolder().toPath();
            Files.createDirectories(dir);
            boolean changed = false;
            if (configYaml != null && !configYaml.isEmpty()) {
                changed |= writeIfDifferent(dir.resolve("config.yml"), configYaml);
            }
            if (messagesYaml != null && !messagesYaml.isEmpty()) {
                changed |= writeIfDifferent(dir.resolve("messages.yml"), messagesYaml);
            }
            if (changed) {
                plugin.getLogger().info("已从主插件同步配置 (config.yml / messages.yml)。");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("写入同步配置失败: " + e.getMessage());
        }
    }

    private boolean writeIfDifferent(Path file, String content) throws IOException {
        if (Files.exists(file)) {
            String existing = Files.readString(file, StandardCharsets.UTF_8);
            if (existing.equals(content)) {
                return false;
            }
        }
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return true;
    }
}
