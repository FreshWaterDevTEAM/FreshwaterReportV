package com.freshwater.report.teleport;

import com.freshwater.report.config.Messages;
import com.freshwater.report.config.PluginConfig;
import com.freshwater.report.messaging.ProxyMessaging;
import com.freshwater.report.model.Report;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 处理 {@code /reports tp}：常规拓扑直接精确传送；嵌套拓扑经 Waterfall 伴生插件二段传送。
 */
public final class TeleportService {

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final PluginConfig config;
    private final Messages messages;
    private final ProxyMessaging messaging;

    public TeleportService(Object plugin, ProxyServer proxy, Logger logger, PluginConfig config,
                           Messages messages, ProxyMessaging messaging) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.config = config;
        this.messages = messages;
        this.messaging = messaging;
    }

    public void teleport(Player staff, Report report) {
        if (!config.isTeleportEnabled()) {
            staff.sendMessage(messages.prefixed("teleport.disabled"));
            return;
        }

        Optional<Player> targetOpt = proxy.getPlayer(report.getTargetUuid());
        if (targetOpt.isEmpty()) {
            staff.sendMessage(messages.prefixed("teleport.target-offline",
                    Placeholder.unparsed("target", report.getTargetName())));
            return;
        }
        Player target = targetOpt.get();
        Optional<ServerConnection> targetConn = target.getCurrentServer();
        if (targetConn.isEmpty()) {
            staff.sendMessage(messages.prefixed("teleport.target-offline",
                    Placeholder.unparsed("target", report.getTargetName())));
            return;
        }

        RegisteredServer destination = targetConn.get().getServer();
        String destName = destination.getServerInfo().getName();

        if (!config.isNestedProxyMode()) {
            staff.createConnectionRequest(destination).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    staff.sendMessage(messages.prefixed("teleport.success",
                            Placeholder.unparsed("target", report.getTargetName()),
                            Placeholder.unparsed("server", destName)));
                } else {
                    staff.sendMessage(messages.prefixed("teleport.failed",
                            Placeholder.unparsed("server", destName)));
                }
            });
            return;
        }

        // 嵌套模式：先到 Waterfall 入口，再由伴生插件二段传送到真实子服
        UUID targetUuid = target.getUniqueId();
        boolean alreadyAtEntry = staff.getCurrentServer()
                .map(c -> c.getServerInfo().getName().equals(destName))
                .orElse(false);

        if (alreadyAtEntry) {
            dispatchNestedTeleport(staff, targetUuid, report.getTargetName());
        } else {
            staff.createConnectionRequest(destination).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    dispatchNestedTeleport(staff, targetUuid, report.getTargetName());
                } else {
                    staff.sendMessage(messages.prefixed("teleport.failed",
                            Placeholder.unparsed("server", destName)));
                }
            });
        }
    }

    private void dispatchNestedTeleport(Player staff, UUID targetUuid, String targetName) {
        // 略作延迟，确保处理者到 Waterfall 的连接已建立，可承载插件消息
        proxy.getScheduler().buildTask(plugin, () -> {
            boolean sent = messaging.sendTpToPlayer(staff, targetUuid);
            if (sent) {
                staff.sendMessage(messages.prefixed("teleport.nested-sent",
                        Placeholder.unparsed("target", targetName)));
            } else {
                staff.sendMessage(messages.prefixed("teleport.nested-no-companion",
                        Placeholder.unparsed("target", targetName)));
            }
        }).delay(Duration.ofMillis(700)).schedule();
    }
}
