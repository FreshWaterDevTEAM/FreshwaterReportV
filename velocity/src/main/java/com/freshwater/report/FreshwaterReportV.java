package com.freshwater.report;

import com.freshwater.report.command.ReportCommand;
import com.freshwater.report.command.ReportHistoryCommand;
import com.freshwater.report.command.ReportsCommand;
import com.freshwater.report.config.Messages;
import com.freshwater.report.config.PluginConfig;
import com.freshwater.report.http.HttpApiServer;
import com.freshwater.report.messaging.ProxyMessaging;
import com.freshwater.report.notify.NotificationService;
import com.freshwater.report.service.ReportService;
import com.freshwater.report.service.ReportServiceImpl;
import com.freshwater.report.storage.Database;
import com.freshwater.report.storage.ReportRepository;
import com.freshwater.report.teleport.TeleportService;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "freshwaterreportv",
        name = "FreshwaterReportV",
        version = "1.0.0",
        description = "全服通用举报系统：MySQL 存储、跨服传送处理、Java/HTTP API。",
        authors = {"淡水岛开发组"}
)
public final class FreshwaterReportV {

    private static FreshwaterReportV instance;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private PluginConfig config;
    private Messages messages;
    private Database database;
    private ReportRepository repository;
    private ReportServiceImpl reportService;
    private NotificationService notificationService;
    private ProxyMessaging proxyMessaging;
    private TeleportService teleportService;
    private HttpApiServer httpApiServer;

    @Inject
    public FreshwaterReportV(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        instance = this;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            this.config = PluginConfig.load(dataDirectory, logger);
            this.messages = Messages.load(dataDirectory, logger);
        } catch (Exception e) {
            logger.error("加载配置失败，插件未启用。", e);
            return;
        }

        try {
            this.database = new Database(config.getDatabase());
            this.repository = new ReportRepository(database);
            this.repository.initSchema();
            logger.info("数据库连接成功，数据表已就绪。");
        } catch (Exception e) {
            logger.error("数据库初始化失败，请检查 config.yml 中的数据库配置后重启。", e);
            if (database != null) {
                database.close();
            }
            return;
        }

        this.reportService = new ReportServiceImpl(repository);
        this.notificationService = new NotificationService(proxy, messages, logger);
        this.proxyMessaging = new ProxyMessaging(proxy, logger, dataDirectory);
        this.proxyMessaging.register();
        proxy.getEventManager().register(this, proxyMessaging);
        this.teleportService = new TeleportService(this, proxy, logger, config, messages, proxyMessaging);

        registerCommands();

        if (config.getHttp().isEnabled()) {
            try {
                this.httpApiServer = new HttpApiServer(config.getHttp(), reportService, logger);
                this.httpApiServer.start();
            } catch (Exception e) {
                logger.error("HTTP API 启动失败。", e);
            }
        }

        logger.info("FreshwaterReportV 已启用。嵌套代理模式: {}", config.isNestedProxyMode() ? "开启" : "关闭");
    }

    private void registerCommands() {
        CommandManager cm = proxy.getCommandManager();

        CommandMeta reportMeta = cm.metaBuilder("report").plugin(this).build();
        cm.register(reportMeta, new ReportCommand(this, proxy, logger, config, messages, reportService,
                notificationService, proxyMessaging));

        CommandMeta reportsMeta = cm.metaBuilder("reports").aliases("reportadmin").plugin(this).build();
        cm.register(reportsMeta, new ReportsCommand(this, proxy, logger, config, messages, reportService,
                teleportService));

        CommandMeta historyMeta = cm.metaBuilder("reporthistory").aliases("reporthis").plugin(this).build();
        cm.register(historyMeta, new ReportHistoryCommand(this, proxy, logger, messages, reportService));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (httpApiServer != null) {
            httpApiServer.stop();
        }
        if (proxyMessaging != null) {
            proxyMessaging.unregister();
        }
        if (database != null) {
            database.close();
        }
        logger.info("FreshwaterReportV 已停用。");
    }

    /** 供其它插件获取主插件实例。 */
    public static FreshwaterReportV getInstance() {
        return instance;
    }

    /** 供其它插件获取举报服务 Java API（可能为 null，若插件未成功初始化）。 */
    public ReportService getReportService() {
        return reportService;
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public PluginConfig getConfig() {
        return config;
    }
}
