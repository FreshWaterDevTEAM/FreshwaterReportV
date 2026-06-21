package com.freshwater.report.velocitycompanion;

import com.freshwater.report.common.Channels;
import com.freshwater.report.common.Protocol;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Velocity 套 Velocity 拓扑下，装在「后端子代理」上的伴生插件。
 *
 * <p>功能与 Waterfall 伴生插件一致：响应前端主插件的子服精确传送、真实子服查询，
 * 主动上报玩家子服位置，并向前端同步 config.yml / messages.yml。</p>
 */
@Plugin(
        id = "freshwaterreportcompanion",
        name = "FreshwaterReportV-Companion",
        version = "1.0.0",
        description = "FreshwaterReportV 的 Velocity 伴生插件（装在后端子代理）。",
        authors = {"淡水岛开发组"}
)
public final class FreshwaterReportVelocityCompanion {

    private static final MinecraftChannelIdentifier IDENTIFIER =
            MinecraftChannelIdentifier.create(Channels.REPORT_NAMESPACE, Channels.REPORT_NAME);
    private static final long CONFIG_REQUEST_INTERVAL_MS = 30_000L;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private volatile long lastConfigRequest = 0L;

    @Inject
    public FreshwaterReportVelocityCompanion(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(IDENTIFIER);
        logger.info("FreshwaterReportV 伴生插件(Velocity)已启用，通道: {}", Channels.REPORT);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!IDENTIFIER.equals(event.getIdentifier())) {
            return;
        }
        // 来自前端代理的数据不应继续转发给真实客户端
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof Player player)) {
            return;
        }
        try {
            Protocol.Message message = Protocol.decode(event.getData());
            switch (message.type()) {
                case Protocol.TP_TO_PLAYER -> handleTeleport(message.arg(0), message.arg(1));
                case Protocol.QUERY_LOCATION -> handleQuery(message.arg(0), player);
                case Protocol.CONFIG_DATA -> writeConfig(message.arg(0), message.arg(1));
                default -> {
                    // 其它类型为上行方向，伴生插件忽略
                }
            }
        } catch (Exception e) {
            logger.warn("处理跨代理插件消息失败", e);
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        String server = event.getServer().getServerInfo().getName();
        sendUpstream(player, Protocol.encode(Protocol.LOCATION_UPDATE, uuid, server));
        requestConfigIfStale(player);
    }

    private void handleTeleport(String staffUuid, String targetUuid) {
        UUID staffId = parse(staffUuid);
        UUID targetId = parse(targetUuid);
        if (staffId == null || targetId == null) {
            return;
        }
        Optional<Player> staff = proxy.getPlayer(staffId);
        Optional<Player> target = proxy.getPlayer(targetId);
        if (staff.isEmpty() || target.isEmpty()) {
            return;
        }
        target.get().getCurrentServer().ifPresent(conn -> {
            RegisteredServer destination = conn.getServer();
            staff.get().createConnectionRequest(destination).fireAndForget();
            logger.info("已将 {} 传送到 {} 所在子服 {}",
                    staff.get().getUsername(), target.get().getUsername(),
                    destination.getServerInfo().getName());
        });
    }

    private void handleQuery(String targetUuid, Player source) {
        UUID targetId = parse(targetUuid);
        String server = "";
        if (targetId != null) {
            server = proxy.getPlayer(targetId)
                    .flatMap(Player::getCurrentServer)
                    .map(c -> c.getServerInfo().getName())
                    .orElse("");
        }
        sendUpstream(source, Protocol.encode(Protocol.LOCATION, targetUuid, server));
    }

    private void requestConfigIfStale(Player player) {
        long now = System.currentTimeMillis();
        if (now - lastConfigRequest < CONFIG_REQUEST_INTERVAL_MS) {
            return;
        }
        lastConfigRequest = now;
        sendUpstream(player, Protocol.encode(Protocol.CONFIG_REQUEST));
    }

    private void writeConfig(String configYaml, String messagesYaml) {
        try {
            Files.createDirectories(dataDirectory);
            boolean changed = false;
            if (configYaml != null && !configYaml.isEmpty()) {
                changed |= writeIfDifferent(dataDirectory.resolve("config.yml"), configYaml);
            }
            if (messagesYaml != null && !messagesYaml.isEmpty()) {
                changed |= writeIfDifferent(dataDirectory.resolve("messages.yml"), messagesYaml);
            }
            if (changed) {
                logger.info("已从主插件同步配置 (config.yml / messages.yml)。");
            }
        } catch (Exception e) {
            logger.warn("写入同步配置失败", e);
        }
    }

    private boolean writeIfDifferent(Path file, String content) throws Exception {
        if (Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).equals(content)) {
            return false;
        }
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return true;
    }

    private void sendUpstream(Player player, byte[] data) {
        try {
            // Player.sendPluginMessage 发往客户端方向（即上游前端 Velocity）
            player.sendPluginMessage(IDENTIFIER, data);
        } catch (Exception e) {
            // 通道未在上游注册等情况，静默忽略
        }
    }

    private static UUID parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
