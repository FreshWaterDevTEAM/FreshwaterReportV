package com.freshwater.report.command;

import com.freshwater.report.config.Messages;
import com.freshwater.report.config.PluginConfig;
import com.freshwater.report.model.Report;
import com.freshwater.report.model.ReportNote;
import com.freshwater.report.model.ReportStatus;
import com.freshwater.report.service.ReportService;
import com.freshwater.report.teleport.TeleportService;
import com.freshwater.report.util.TimeFormat;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /reports <list|info|claim|close|reopen|note|tp|delete>}
 */
public final class ReportsCommand implements SimpleCommand {

    private static final int PAGE_SIZE = 8;
    private static final List<String> SUBCOMMANDS =
            List.of("list", "info", "claim", "close", "reopen", "note", "tp", "delete");

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final PluginConfig config;
    private final Messages messages;
    private final ReportService service;
    private final TeleportService teleportService;

    public ReportsCommand(Object plugin, ProxyServer proxy, Logger logger, PluginConfig config, Messages messages,
                          ReportService service, TeleportService teleportService) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.config = config;
        this.messages = messages;
        this.service = service;
        this.teleportService = teleportService;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            handleList(source, new String[]{"list"});
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> handleList(source, args);
            case "info" -> handleInfo(source, args);
            case "claim" -> handleClaim(source, args);
            case "close" -> handleClose(source, args);
            case "reopen" -> handleReopen(source, args);
            case "note" -> handleNote(source, args);
            case "tp" -> handleTp(source, args);
            case "delete" -> handleDelete(source, args);
            default -> source.sendMessage(messages.prefixed("reports.usage"));
        }
    }

    private void handleList(CommandSource source, String[] args) {
        if (!Permissions.has(source, Permissions.REPORTS_LIST)) {
            source.sendMessage(messages.prefixed("general.no-permission"));
            return;
        }
        int page = args.length >= 2 ? parseInt(args[1], 1) : 1;
        ReportStatus filter = args.length >= 3 ? ReportStatus.fromString(args[2]) : null;
        final int safePage = Math.max(1, page);

        runAsync(() -> {
            int total = service.countReports(filter);
            int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
            List<Report> reports = service.listReports(filter, safePage, PAGE_SIZE);

            source.sendMessage(messages.prefixed("reports.list-header",
                    Placeholder.unparsed("page", String.valueOf(Math.min(safePage, pages))),
                    Placeholder.unparsed("pages", String.valueOf(pages)),
                    Placeholder.unparsed("total", String.valueOf(total))));

            if (reports.isEmpty()) {
                source.sendMessage(messages.prefixed("reports.list-empty"));
                return;
            }
            for (Report report : reports) {
                source.sendMessage(messages.render("reports.list-entry",
                        Placeholder.unparsed("id", String.valueOf(report.getId())),
                        Placeholder.parsed("status", statusDisplay(report.getStatus())),
                        Placeholder.unparsed("reporter", report.getReporterName()),
                        Placeholder.unparsed("target", report.getTargetName()),
                        Placeholder.unparsed("reason", report.getReason()),
                        Placeholder.unparsed("server", nullToUnknown(report.getServer()))));
            }
        });
    }

    private void handleInfo(CommandSource source, String[] args) {
        if (!Permissions.has(source, Permissions.REPORTS_INFO)) {
            source.sendMessage(messages.prefixed("general.no-permission"));
            return;
        }
        Integer id = requireId(source, args);
        if (id == null) {
            return;
        }
        runAsync(() -> {
            Optional<Report> reportOpt = service.getReport(id);
            if (reportOpt.isEmpty()) {
                source.sendMessage(messages.prefixed("reports.not-found", Placeholder.unparsed("id", String.valueOf(id))));
                return;
            }
            Report r = reportOpt.get();
            List<ReportNote> notes = service.getNotes(id);
            String serverDisplay = nullToUnknown(r.getServer()) + (r.isNestedServer() ? " (子代理)" : "");

            source.sendMessage(messages.render("reports.info.title", Placeholder.unparsed("id", String.valueOf(r.getId()))));
            source.sendMessage(messages.render("reports.info.status", Placeholder.parsed("status", statusDisplay(r.getStatus()))));
            source.sendMessage(messages.render("reports.info.reporter", Placeholder.unparsed("reporter", r.getReporterName())));
            source.sendMessage(messages.render("reports.info.target", Placeholder.unparsed("target", r.getTargetName())));
            source.sendMessage(messages.render("reports.info.reason", Placeholder.unparsed("reason", r.getReason())));
            source.sendMessage(messages.render("reports.info.server", Placeholder.unparsed("server", serverDisplay)));
            source.sendMessage(messages.render("reports.info.handler",
                    Placeholder.unparsed("handler", r.getHandlerName() == null ? "-" : r.getHandlerName())));
            source.sendMessage(messages.render("reports.info.created", Placeholder.unparsed("created", TimeFormat.format(r.getCreatedAt()))));
            source.sendMessage(messages.render("reports.info.updated", Placeholder.unparsed("updated", TimeFormat.format(r.getUpdatedAt()))));

            if (!notes.isEmpty()) {
                source.sendMessage(messages.render("reports.info.notes-header"));
                for (ReportNote note : notes) {
                    source.sendMessage(messages.render("reports.info.note-entry",
                            Placeholder.unparsed("author", note.getAuthorName()),
                            Placeholder.unparsed("time", TimeFormat.format(note.getCreatedAt())),
                            Placeholder.unparsed("content", note.getContent())));
                }
            }
            source.sendMessage(messages.render("reports.info.actions", Placeholder.unparsed("id", String.valueOf(r.getId()))));
        });
    }

    private void handleClaim(CommandSource source, String[] args) {
        if (!Permissions.has(source, Permissions.REPORTS_CLAIM)) {
            source.sendMessage(messages.prefixed("general.no-permission"));
            return;
        }
        Integer id = requireId(source, args);
        if (id == null) {
            return;
        }
        UUID handlerUuid = source instanceof Player p ? p.getUniqueId() : null;
        String handlerName = handlerName(source);
        runAsync(() -> {
            if (service.getReport(id).isEmpty()) {
                source.sendMessage(messages.prefixed("reports.not-found", Placeholder.unparsed("id", String.valueOf(id))));
                return;
            }
            service.claimReport(id, handlerUuid, handlerName);
            source.sendMessage(messages.prefixed("reports.claimed", Placeholder.unparsed("id", String.valueOf(id))));
        });
    }

    private void handleClose(CommandSource source, String[] args) {
        if (!Permissions.has(source, Permissions.REPORTS_CLOSE)) {
            source.sendMessage(messages.prefixed("general.no-permission"));
            return;
        }
        Integer id = requireId(source, args);
        if (id == null) {
            return;
        }
        UUID handlerUuid = source instanceof Player p ? p.getUniqueId() : null;
        String handlerName = handlerName(source);
        runAsync(() -> {
            if (service.getReport(id).isEmpty()) {
                source.sendMessage(messages.prefixed("reports.not-found", Placeholder.unparsed("id", String.valueOf(id))));
                return;
            }
            service.closeReport(id, handlerUuid, handlerName);
            source.sendMessage(messages.prefixed("reports.closed", Placeholder.unparsed("id", String.valueOf(id))));
        });
    }

    private void handleReopen(CommandSource source, String[] args) {
        if (!Permissions.has(source, Permissions.REPORTS_REOPEN)) {
            source.sendMessage(messages.prefixed("general.no-permission"));
            return;
        }
        Integer id = requireId(source, args);
        if (id == null) {
            return;
        }
        runAsync(() -> {
            if (service.getReport(id).isEmpty()) {
                source.sendMessage(messages.prefixed("reports.not-found", Placeholder.unparsed("id", String.valueOf(id))));
                return;
            }
            service.reopenReport(id);
            source.sendMessage(messages.prefixed("reports.reopened", Placeholder.unparsed("id", String.valueOf(id))));
        });
    }

    private void handleNote(CommandSource source, String[] args) {
        if (!Permissions.has(source, Permissions.REPORTS_NOTE)) {
            source.sendMessage(messages.prefixed("general.no-permission"));
            return;
        }
        if (args.length < 3) {
            source.sendMessage(messages.prefixed("reports.usage"));
            return;
        }
        Integer id = parseIdOrNull(args[1]);
        if (id == null) {
            source.sendMessage(messages.prefixed("reports.usage"));
            return;
        }
        String content = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        UUID authorUuid = source instanceof Player p ? p.getUniqueId() : null;
        String authorName = handlerName(source);
        runAsync(() -> {
            if (service.getReport(id).isEmpty()) {
                source.sendMessage(messages.prefixed("reports.not-found", Placeholder.unparsed("id", String.valueOf(id))));
                return;
            }
            service.addNote(id, authorUuid, authorName, content);
            source.sendMessage(messages.prefixed("reports.note-added", Placeholder.unparsed("id", String.valueOf(id))));
        });
    }

    private void handleTp(CommandSource source, String[] args) {
        if (!Permissions.has(source, Permissions.REPORTS_TP)) {
            source.sendMessage(messages.prefixed("general.no-permission"));
            return;
        }
        if (!(source instanceof Player staff)) {
            source.sendMessage(messages.prefixed("general.player-only"));
            return;
        }
        Integer id = requireId(source, args);
        if (id == null) {
            return;
        }
        runAsync(() -> {
            Optional<Report> report = service.getReport(id);
            if (report.isEmpty()) {
                source.sendMessage(messages.prefixed("reports.not-found", Placeholder.unparsed("id", String.valueOf(id))));
                return;
            }
            teleportService.teleport(staff, report.get());
        });
    }

    private void handleDelete(CommandSource source, String[] args) {
        if (!Permissions.has(source, Permissions.REPORTS_DELETE)) {
            source.sendMessage(messages.prefixed("general.no-permission"));
            return;
        }
        Integer id = requireId(source, args);
        if (id == null) {
            return;
        }
        runAsync(() -> {
            boolean ok = service.deleteReport(id);
            if (ok) {
                source.sendMessage(messages.prefixed("reports.deleted", Placeholder.unparsed("id", String.valueOf(id))));
            } else {
                source.sendMessage(messages.prefixed("reports.not-found", Placeholder.unparsed("id", String.valueOf(id))));
            }
        });
    }

    // ---- helpers ----

    private void runAsync(Runnable runnable) {
        proxy.getScheduler().buildTask(plugin, () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                logger.warn("处理 /reports 命令失败", e);
            }
        }).schedule();
    }

    private Integer requireId(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(messages.prefixed("reports.usage"));
            return null;
        }
        Integer id = parseIdOrNull(args[1]);
        if (id == null) {
            source.sendMessage(messages.prefixed("reports.usage"));
        }
        return id;
    }

    private Integer parseIdOrNull(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseInt(String value, int def) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private String statusDisplay(ReportStatus status) {
        return messages.raw("status." + status.name());
    }

    private String handlerName(CommandSource source) {
        return source instanceof Player p ? p.getUsername() : "控制台";
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return new ArrayList<>(List.of("1", "OPEN", "CLAIMED", "CLOSED"));
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        CommandSource source = invocation.source();
        return Permissions.has(source, Permissions.REPORTS_LIST)
                || Permissions.has(source, Permissions.REPORTS_INFO)
                || Permissions.has(source, Permissions.REPORTS_CLAIM)
                || Permissions.has(source, Permissions.REPORTS_CLOSE)
                || Permissions.has(source, Permissions.REPORTS_REOPEN)
                || Permissions.has(source, Permissions.REPORTS_NOTE)
                || Permissions.has(source, Permissions.REPORTS_TP)
                || Permissions.has(source, Permissions.REPORTS_DELETE);
    }
}
