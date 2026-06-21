package com.freshwater.report.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 读取 config.yml。
 */
public final class PluginConfig {

    private final Database database;
    private final Http http;
    private final long reportCooldownSeconds;
    private final boolean nestedProxyMode;
    private final long companionQueryTimeoutMs;
    private final boolean allowCustomReason;
    private final boolean teleportEnabled;
    private final List<ReportReason> reasons;

    private PluginConfig(Database database, Http http, long reportCooldownSeconds, boolean nestedProxyMode,
                         long companionQueryTimeoutMs, boolean allowCustomReason, boolean teleportEnabled,
                         List<ReportReason> reasons) {
        this.database = database;
        this.http = http;
        this.reportCooldownSeconds = reportCooldownSeconds;
        this.nestedProxyMode = nestedProxyMode;
        this.companionQueryTimeoutMs = companionQueryTimeoutMs;
        this.allowCustomReason = allowCustomReason;
        this.teleportEnabled = teleportEnabled;
        this.reasons = reasons;
    }

    @SuppressWarnings("unchecked")
    public static PluginConfig load(Path dataDir, Logger logger) throws IOException {
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve("config.yml");
        if (Files.notExists(file)) {
            copyDefault("config.yml", file);
            logger.info("已生成默认 config.yml，请填写数据库连接信息后重启。");
        }

        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(file)) {
            Object loaded = new Yaml().load(in);
            root = loaded instanceof Map ? (Map<String, Object>) loaded : new LinkedHashMap<>();
        }

        Map<String, Object> dbSection = section(root, "database");
        Database database = new Database(
                str(dbSection, "host", "127.0.0.1"),
                intVal(dbSection, "port", 3306),
                str(dbSection, "database", "freshwater_report"),
                str(dbSection, "username", "root"),
                str(dbSection, "password", ""),
                intVal(dbSection, "pool-size", 6),
                str(dbSection, "table-prefix", "report_"),
                bool(dbSection, "use-ssl", false)
        );

        Map<String, Object> httpSection = section(root, "http-api");
        Http http = new Http(
                bool(httpSection, "enabled", false),
                str(httpSection, "host", "127.0.0.1"),
                intVal(httpSection, "port", 8085),
                str(httpSection, "token", "change-me-please")
        );

        long cooldown = longVal(root, "report-cooldown-seconds", 30);
        boolean nested = bool(root, "nested-proxy-mode", false);
        long timeout = longVal(root, "companion-query-timeout-ms", 800);
        boolean allowCustom = bool(root, "allow-custom-reason", false);
        boolean teleport = bool(root, "teleport-enabled", true);

        List<ReportReason> reasons = new ArrayList<>();
        Object reasonsObj = root.get("reasons");
        if (reasonsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> rm = (Map<String, Object>) m;
                    String id = str(rm, "id", null);
                    if (id == null || id.isBlank()) {
                        continue;
                    }
                    String display = str(rm, "display", id);
                    String desc = str(rm, "description", "");
                    reasons.add(new ReportReason(id.trim().toLowerCase(), display, desc));
                }
            }
        }

        return new PluginConfig(database, http, cooldown, nested, timeout, allowCustom, teleport, reasons);
    }

    private static void copyDefault(String resource, Path target) throws IOException {
        try (InputStream in = PluginConfig.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("缺少内置资源: " + resource);
            }
            try (OutputStream out = Files.newOutputStream(target)) {
                in.transferTo(out);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String key) {
        Object v = root.get(key);
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    private static String str(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static int intVal(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? def : Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long longVal(Map<String, Object> map, String key, long def) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return v == null ? def : Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean bool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return v == null ? def : Boolean.parseBoolean(String.valueOf(v).trim());
    }

    public Database getDatabase() {
        return database;
    }

    public Http getHttp() {
        return http;
    }

    public long getReportCooldownSeconds() {
        return reportCooldownSeconds;
    }

    public boolean isNestedProxyMode() {
        return nestedProxyMode;
    }

    public long getCompanionQueryTimeoutMs() {
        return companionQueryTimeoutMs;
    }

    public boolean isAllowCustomReason() {
        return allowCustomReason;
    }

    public boolean isTeleportEnabled() {
        return teleportEnabled;
    }

    public List<ReportReason> getReasons() {
        return reasons;
    }

    public Optional<ReportReason> findReason(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String needle = id.trim().toLowerCase();
        return reasons.stream().filter(r -> r.getId().equals(needle)).findFirst();
    }

    /** 数据库配置。 */
    public static final class Database {
        private final String host;
        private final int port;
        private final String database;
        private final String username;
        private final String password;
        private final int poolSize;
        private final String tablePrefix;
        private final boolean useSsl;

        public Database(String host, int port, String database, String username, String password,
                        int poolSize, String tablePrefix, boolean useSsl) {
            this.host = host;
            this.port = port;
            this.database = database;
            this.username = username;
            this.password = password;
            this.poolSize = poolSize;
            this.tablePrefix = tablePrefix;
            this.useSsl = useSsl;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getDatabase() {
            return database;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public String getTablePrefix() {
            return tablePrefix;
        }

        public boolean isUseSsl() {
            return useSsl;
        }
    }

    /** HTTP API 配置。 */
    public static final class Http {
        private final boolean enabled;
        private final String host;
        private final int port;
        private final String token;

        public Http(boolean enabled, String host, int port, String token) {
            this.enabled = enabled;
            this.host = host;
            this.port = port;
            this.token = token;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getToken() {
            return token;
        }
    }
}
