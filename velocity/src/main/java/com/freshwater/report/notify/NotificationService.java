package com.freshwater.report.notify;

import com.freshwater.report.command.Permissions;
import com.freshwater.report.config.Messages;
import com.freshwater.report.model.Report;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

/**
 * 举报通知：向在线、拥有 notify 权限的管理员推送可点击消息，并写入日志。
 */
public final class NotificationService {

    private final ProxyServer proxy;
    private final Messages messages;
    private final Logger logger;

    public NotificationService(ProxyServer proxy, Messages messages, Logger logger) {
        this.proxy = proxy;
        this.messages = messages;
        this.logger = logger;
    }

    public void notifyNewReport(Report report) {
        logger.info("[举报] #{} {} 举报了 {} 原因: {} (服务器: {})",
                report.getId(), report.getReporterName(), report.getTargetName(),
                report.getReason(), report.getServer());

        Component component = messages.render("notify.new-report",
                Placeholder.unparsed("id", String.valueOf(report.getId())),
                Placeholder.unparsed("reporter", report.getReporterName()),
                Placeholder.unparsed("target", report.getTargetName()),
                Placeholder.unparsed("reason", report.getReason()),
                Placeholder.unparsed("server", report.getServer() == null ? "未知" : report.getServer()));

        for (Player player : proxy.getAllPlayers()) {
            if (Permissions.has(player, Permissions.NOTIFY)) {
                player.sendMessage(component);
            }
        }
    }
}
