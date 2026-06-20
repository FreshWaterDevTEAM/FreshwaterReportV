package com.freshwater.report.storage;

import com.freshwater.report.config.PluginConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 基于 HikariCP 的 MySQL/MariaDB 连接池封装。
 */
public final class Database {

    private final HikariDataSource dataSource;
    private final String tablePrefix;

    public Database(PluginConfig.Database cfg) {
        HikariConfig hc = new HikariConfig();
        String url = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=%s&characterEncoding=utf8&serverTimezone=UTC"
                        + "&useUnicode=true&allowPublicKeyRetrieval=true&autoReconnect=true",
                cfg.getHost(), cfg.getPort(), cfg.getDatabase(), cfg.isUseSsl());
        hc.setJdbcUrl(url);
        hc.setUsername(cfg.getUsername());
        hc.setPassword(cfg.getPassword());
        // 显式指定驱动类，避免依赖 DriverManager SPI（Velocity 插件类加载器隔离）
        hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hc.setMaximumPoolSize(Math.max(1, cfg.getPoolSize()));
        hc.setMinimumIdle(1);
        hc.setPoolName("FreshwaterReport");
        hc.setConnectionTimeout(10_000L);
        hc.setMaxLifetime(1_800_000L);
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "200");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        // 使用插件自身类加载器，确保能找到被 shade 进来的驱动
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(Database.class.getClassLoader());
            this.dataSource = new HikariDataSource(hc);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
        this.tablePrefix = cfg.getTablePrefix();
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
