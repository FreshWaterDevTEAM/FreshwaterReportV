package com.freshwater.report.command;

import com.freshwater.report.config.Messages;
import com.freshwater.report.config.PluginConfig;
import com.freshwater.report.config.ReportReason;
import com.freshwater.report.messaging.ProxyMessaging;
import com.freshwater.report.model.Report;
import com.freshwater.report.notify.NotificationService;
import com.freshwater.report.service.ReportService;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * {@code /report <玩家> [原因id|自定义原因...]}
 */
public final class ReportCommand implements SimpleCommand {

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final PluginConfig config;
    private final Messages messages;
    private final ReportService service;
    private final NotificationService notificationService;
    private final ProxyMessaging messaging;

    private final ConcurrentHashMap<java.util.UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public ReportCommand(Object plugin, ProxyServer proxy, Logger logger, PluginConfig config, Messages messages,
                         ReportService service, NotificationService notificationService, ProxyMessaging messaging) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.config = config;
        this.messages = messages;
        this.service = service;
        this.notificationService = notificationService;
        this.messaging = messaging;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player reporter)) {
            source.sendMessage(messages.prefixed("general.player-only"));
            return;
        }
        if (!Permissions.has(source, Permissions.REPORT)) {
            source.sendMessage(messages.prefixed("general.no-permission"));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            reporter.sendMessage(messages.prefixed("report.usage"));
            return;
        }

        String targetName = args[0];
        Optional<Player> targetOpt = proxy.getPlayer(targetName);
        if (targetOpt.isEmpty()) {
            reporter.sendMessage(messages.prefixed("report.target-not-found",
                    Placeholder.unparsed("target", targetName)));
            return;
        }
        Player target = targetOpt.get();
        if (target.getUniqueId().equals(reporter.getUniqueId())) {
            reporter.sendMessage(messages.prefixed("report.cannot-self"));
            return;
        }

        if (args.length == 1) {
            if (config.getReasons().isEmpty()) {
                reporter.sendMessage(messages.prefixed("report.usage"));
            } else {
                sendReasonMenu(reporter, target.getUsername());
            }
            return;
        }

        String reasonArg = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        Optional<ReportReason> preset = config.findReason(reasonArg);
        String reasonText;
        if (preset.isPresent()) {
            reasonText = preset.get().getDisplay();
        } else if (!config.isAllowCustomReason()) {
            reporter.sendMessage(messages.prefixed("report.invalid-reason",
                    Placeholder.unparsed("reasons", availableReasonIds())));
            return;
        } else if (!Permissions.has(source, Permissions.REPORT_CUSTOM)) {
            reporter.sendMessage(messages.prefixed("report.custom-not-allowed"));
            return;
        } else {
            reasonText = reasonArg;
        }

        if (!Permissions.has(source, Permissions.REPORT_COOLDOWN_BYPASS)) {
            long now = System.currentTimeMillis();
            long cooldownMs = config.getReportCooldownSeconds() * 1000L;
            Long last = cooldowns.get(reporter.getUniqueId());
            if (last != null && now - last < cooldownMs) {
                long remaining = (cooldownMs - (now - last) + 999) / 1000;
                reporter.sendMessage(messages.prefixed("report.cooldown",
                        Placeholder.unparsed("seconds", String.valueOf(remaining))));
                return;
            }
            cooldowns.put(reporter.getUniqueId(), now);
        }

        boolean nested = config.isNestedProxyMode();
        String entryServer = target.getCurrentServer()
                .map(c -> c.getServerInfo().getName())
                .orElse(null);
        String initialServer = entryServer;
        if (nested) {
            initialServer = messaging.getRealServer(target.getUniqueId()).orElse(entryServer);
        }

        final String reason = reasonText;
        final String serverToStore = initialServer;
        final java.util.UUID targetUuid = target.getUniqueId();
        final String targetUsername = target.getUsername();

        proxy.getScheduler().buildTask(plugin, () -> {
            try {
                Report report = service.createReport(reporter.getUniqueId(), reporter.getUsername(),
                        targetUuid, targetUsername, reason, serverToStore, nested);
                notificationService.notifyNewReport(report);
                reporter.sendMessage(messages.prefixed("report.success",
                        Placeholder.unparsed("id", String.valueOf(report.getId())),
                        Placeholder.unparsed("target", targetUsername)));

                if (nested) {
                    refineNestedServer(report.getId(), targetUuid, entryServer);
                }
            } catch (Exception e) {
                logger.warn("创建举报失败", e);
                reporter.sendMessage(messages.prefixed("general.error"));
            }
        }).schedule();
    }

    private void refineNestedServer(int reportId, java.util.UUID targetUuid, String entryServer) {
        proxy.getPlayer(targetUuid).ifPresent(messaging::sendQueryLocation);
        proxy.getScheduler().buildTask(plugin, () -> {
            String real = messaging.getRealServer(targetUuid).orElse(null);
            if (real != null && !real.equals(entryServer)) {
                try {
                    service.updateReportServer(reportId, real, true);
                } catch (Exception e) {
                    logger.debug("更新嵌套子服字段失败", e);
                }
            }
        }).delay(Duration.ofMillis(Math.max(200, config.getCompanionQueryTimeoutMs()))).schedule();
    }

    private void sendReasonMenu(Player reporter, String targetName) {
        reporter.sendMessage(messages.prefixed("report.choose-reason-header",
                Placeholder.unparsed("target", targetName)));
        for (ReportReason reason : config.getReasons()) {
            Component entry = messages.render("report.choose-reason-entry",
                    Placeholder.unparsed("target", targetName),
                    Placeholder.unparsed("id", reason.getId()),
                    Placeholder.unparsed("display", reason.getDisplay()),
                    Placeholder.unparsed("description", reason.getDescription() == null ? "" : reason.getDescription()));
            reporter.sendMessage(entry);
        }
    }

    private String availableReasonIds() {
        return config.getReasons().stream().map(ReportReason::getId).collect(Collectors.joining(", "));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            return proxy.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (ReportReason reason : config.getReasons()) {
                if (reason.getId().startsWith(prefix)) {
                    out.add(reason.getId());
                }
            }
            return out;
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return Permissions.has(invocation.source(), Permissions.REPORT);
    }
}
