package com.freshwater.report.command;

import com.freshwater.report.config.Messages;
import com.freshwater.report.model.Report;
import com.freshwater.report.model.ReportStatus;
import com.freshwater.report.service.ReportService;
import com.freshwater.report.util.TimeFormat;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /reporthistory <玩家>} —— 查询某玩家被举报历史。
 */
public final class ReportHistoryCommand implements SimpleCommand {

    private static final int LIMIT = 50;

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Messages messages;
    private final ReportService service;

    public ReportHistoryCommand(Object plugin, ProxyServer proxy, Logger logger, Messages messages,
                                ReportService service) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.messages = messages;
        this.service = service;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!Permissions.has(source, Permissions.HISTORY)) {
            source.sendMessage(messages.prefixed("general.no-permission"));
            return;
        }
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(messages.prefixed("history.usage"));
            return;
        }
        String targetName = args[0];

        proxy.getScheduler().buildTask(plugin, () -> {
            try {
                Optional<Player> online = proxy.getPlayer(targetName);
                List<Report> reports = online
                        .map(player -> service.getHistoryByUuid(player.getUniqueId(), LIMIT))
                        .filter(list -> !list.isEmpty())
                        .orElseGet(() -> service.getHistoryByName(targetName, LIMIT));

                if (reports.isEmpty()) {
                    source.sendMessage(messages.prefixed("history.empty", Placeholder.unparsed("target", targetName)));
                    return;
                }
                source.sendMessage(messages.prefixed("history.header",
                        Placeholder.unparsed("target", targetName),
                        Placeholder.unparsed("count", String.valueOf(reports.size()))));
                for (Report report : reports) {
                    source.sendMessage(messages.render("history.entry",
                            Placeholder.unparsed("id", String.valueOf(report.getId())),
                            Placeholder.parsed("status", statusDisplay(report.getStatus())),
                            Placeholder.unparsed("reason", report.getReason()),
                            Placeholder.unparsed("server", report.getServer() == null ? "未知" : report.getServer()),
                            Placeholder.unparsed("time", TimeFormat.format(report.getCreatedAt()))));
                }
            } catch (Exception e) {
                logger.warn("查询举报历史失败", e);
                source.sendMessage(messages.prefixed("general.error"));
            }
        }).schedule();
    }

    private String statusDisplay(ReportStatus status) {
        return messages.raw("status." + status.name());
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        String prefix = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        return proxy.getAllPlayers().stream()
                .map(Player::getUsername)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return Permissions.has(invocation.source(), Permissions.HISTORY);
    }
}
