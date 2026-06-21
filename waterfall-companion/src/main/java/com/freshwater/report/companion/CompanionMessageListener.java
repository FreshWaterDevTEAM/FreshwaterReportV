package com.freshwater.report.companion;

import com.freshwater.report.common.Channels;
import com.freshwater.report.common.Protocol;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.UUID;

/**
 * 处理来自 Velocity 的跨代理请求：
 * <ul>
 *     <li>{@code TP_TO_PLAYER}：把处理者连接到被举报者所在真实子服。</li>
 *     <li>{@code QUERY_LOCATION}：上报被举报者真实子服。</li>
 * </ul>
 */
public final class CompanionMessageListener implements Listener {

    private final FreshwaterReportCompanion plugin;

    public CompanionMessageListener(FreshwaterReportCompanion plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(Channels.REPORT)) {
            return;
        }
        // 本通道消息不应继续转发到后端子服
        event.setCancelled(true);

        Protocol.Message message;
        try {
            message = Protocol.decode(event.getData());
        } catch (Exception e) {
            plugin.getLogger().warning("解码跨代理消息失败: " + e.getMessage());
            return;
        }

        switch (message.type()) {
            case Protocol.TP_TO_PLAYER -> handleTeleport(message.arg(0), message.arg(1));
            case Protocol.QUERY_LOCATION -> handleQuery(message.arg(0),
                    event.getSender() instanceof ProxiedPlayer ? (ProxiedPlayer) event.getSender() : null);
            case Protocol.CONFIG_DATA -> plugin.getConfigSync().write(message.arg(0), message.arg(1));
            default -> {
                // 其它类型为上行方向，伴生插件忽略
            }
        }
    }

    private void handleTeleport(String staffUuid, String targetUuid) {
        UUID staffId = parse(staffUuid);
        UUID targetId = parse(targetUuid);
        if (staffId == null || targetId == null) {
            return;
        }
        ProxiedPlayer staff = plugin.getProxy().getPlayer(staffId);
        ProxiedPlayer target = plugin.getProxy().getPlayer(targetId);
        if (staff == null || target == null || target.getServer() == null) {
            return;
        }
        ServerInfo destination = target.getServer().getInfo();
        staff.connect(destination);
        plugin.getLogger().info("已将 " + staff.getName() + " 传送到 " + target.getName()
                + " 所在子服 " + destination.getName());
    }

    private void handleQuery(String targetUuid, ProxiedPlayer sender) {
        UUID targetId = parse(targetUuid);
        if (targetId == null) {
            return;
        }
        ProxiedPlayer target = plugin.getProxy().getPlayer(targetId);
        String server = (target != null && target.getServer() != null)
                ? target.getServer().getInfo().getName() : "";

        ProxiedPlayer replyVia = target != null ? target : sender;
        if (replyVia != null) {
            sendUpstream(replyVia, Protocol.encode(Protocol.LOCATION, targetUuid, server));
        }
    }

    static void sendUpstream(ProxiedPlayer player, byte[] data) {
        try {
            // ProxiedPlayer.sendData 将插件消息发往客户端方向（即上游 Velocity）
            player.sendData(Channels.REPORT, data);
        } catch (Exception e) {
            // 通道未在客户端注册等情况，静默忽略
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
