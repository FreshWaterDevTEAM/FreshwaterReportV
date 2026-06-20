package com.freshwater.report.http;

import com.freshwater.report.config.PluginConfig;
import com.freshwater.report.model.Report;
import com.freshwater.report.model.ReportNote;
import com.freshwater.report.model.ReportStatus;
import com.freshwater.report.service.ReportService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 基于 JDK 内置 HttpServer 的 REST API，带 Bearer Token 鉴权。
 */
public final class HttpApiServer {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final PluginConfig.Http config;
    private final ReportService service;
    private final Logger logger;
    private final Gson gson = new Gson();

    private HttpServer server;

    public HttpApiServer(PluginConfig.Http config, ReportService service, Logger logger) {
        this.config = config;
        this.service = service;
        this.logger = logger;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.getHost(), config.getPort()), 0);
        server.createContext("/api/reports", this::handle);
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "FreshwaterReport-HTTP-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        server.setExecutor(Executors.newFixedThreadPool(4, factory));
        server.start();
        logger.info("HTTP API 已启动: http://{}:{}/api/reports", config.getHost(), config.getPort());
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("HTTP API 已停止");
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!authorized(exchange)) {
                sendError(exchange, 401, "未授权：缺少或错误的 Bearer Token");
                return;
            }
            route(exchange);
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "无效的 ID");
        } catch (Exception e) {
            logger.warn("HTTP API 处理异常", e);
            sendError(exchange, 500, "内部错误: " + e.getMessage());
        } finally {
            exchange.close();
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String token = config.getToken();
        if (token == null || token.isBlank()) {
            return false;
        }
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && header.equals("Bearer " + token);
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        // path 形如 /api/reports 或 /api/reports/{id} 或 /api/reports/{id}/notes
        String path = exchange.getRequestURI().getPath();
        String remainder = path.substring("/api/reports".length());
        String[] segments = remainder.split("/");
        // segments[0] 为空串

        if (remainder.isEmpty() || remainder.equals("/")) {
            switch (method) {
                case "GET" -> listReports(exchange);
                case "POST" -> createReport(exchange);
                default -> sendError(exchange, 405, "不支持的方法");
            }
            return;
        }

        int id = Integer.parseInt(segments[1]);
        boolean notesEndpoint = segments.length >= 3 && segments[2].equals("notes");

        if (notesEndpoint) {
            if (method.equals("POST")) {
                addNote(exchange, id);
            } else if (method.equals("GET")) {
                getNotes(exchange, id);
            } else {
                sendError(exchange, 405, "不支持的方法");
            }
            return;
        }

        switch (method) {
            case "GET" -> getReport(exchange, id);
            case "PATCH" -> patchReport(exchange, id);
            case "DELETE" -> deleteReport(exchange, id);
            default -> sendError(exchange, 405, "不支持的方法");
        }
    }

    private void listReports(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        ReportStatus filter = query.containsKey("status") ? ReportStatus.fromString(query.get("status")) : null;
        int page = parseInt(query.get("page"), 1);
        int size = parseInt(query.get("size"), 20);

        List<Report> reports = service.listReports(filter, page, size);
        int total = service.countReports(filter);

        JsonObject out = new JsonObject();
        out.addProperty("total", total);
        out.addProperty("page", page);
        out.addProperty("size", size);
        out.add("data", ReportJson.toJsonArray(reports));
        sendJson(exchange, 200, out);
    }

    private void getReport(HttpExchange exchange, int id) throws IOException {
        Optional<Report> report = service.getReport(id);
        if (report.isEmpty()) {
            sendError(exchange, 404, "举报不存在: " + id);
            return;
        }
        JsonObject obj = ReportJson.toJson(report.get());
        JsonArray notes = new JsonArray();
        for (ReportNote note : service.getNotes(id)) {
            notes.add(ReportJson.toJson(note));
        }
        obj.add("notes", notes);
        sendJson(exchange, 200, obj);
    }

    private void createReport(HttpExchange exchange) throws IOException {
        JsonObject body = readBody(exchange);
        String reason = optString(body, "reason");
        String targetName = optString(body, "targetName");
        if (reason == null || reason.isBlank() || targetName == null || targetName.isBlank()) {
            sendError(exchange, 400, "缺少必填字段: reason, targetName");
            return;
        }
        UUID reporterUuid = optUuid(body, "reporterUuid", ZERO_UUID);
        String reporterName = optString(body, "reporterName");
        if (reporterName == null || reporterName.isBlank()) {
            reporterName = "API";
        }
        UUID targetUuid = optUuid(body, "targetUuid", ZERO_UUID);
        String server = optString(body, "server");
        boolean nested = body.has("nestedServer") && body.get("nestedServer").getAsBoolean();

        Report report = service.createReport(reporterUuid, reporterName, targetUuid, targetName, reason, server, nested);
        sendJson(exchange, 201, ReportJson.toJson(report));
    }

    private void patchReport(HttpExchange exchange, int id) throws IOException {
        if (service.getReport(id).isEmpty()) {
            sendError(exchange, 404, "举报不存在: " + id);
            return;
        }
        JsonObject body = readBody(exchange);
        String status = optString(body, "status");
        if (status == null) {
            sendError(exchange, 400, "缺少 status 字段 (OPEN/CLAIMED/CLOSED)");
            return;
        }
        UUID handlerUuid = optUuid(body, "handlerUuid", null);
        String handlerName = optString(body, "handlerName");

        ReportStatus target = ReportStatus.fromString(status);
        boolean ok;
        switch (target) {
            case CLAIMED -> ok = service.claimReport(id, handlerUuid, handlerName);
            case CLOSED -> ok = service.closeReport(id, handlerUuid, handlerName);
            default -> ok = service.reopenReport(id);
        }
        JsonObject out = new JsonObject();
        out.addProperty("success", ok);
        service.getReport(id).ifPresent(r -> out.add("report", ReportJson.toJson(r)));
        sendJson(exchange, 200, out);
    }

    private void deleteReport(HttpExchange exchange, int id) throws IOException {
        boolean ok = service.deleteReport(id);
        JsonObject out = new JsonObject();
        out.addProperty("success", ok);
        sendJson(exchange, ok ? 200 : 404, out);
    }

    private void addNote(HttpExchange exchange, int id) throws IOException {
        if (service.getReport(id).isEmpty()) {
            sendError(exchange, 404, "举报不存在: " + id);
            return;
        }
        JsonObject body = readBody(exchange);
        String content = optString(body, "content");
        if (content == null || content.isBlank()) {
            sendError(exchange, 400, "缺少 content 字段");
            return;
        }
        UUID authorUuid = optUuid(body, "authorUuid", ZERO_UUID);
        String authorName = optString(body, "authorName");
        if (authorName == null || authorName.isBlank()) {
            authorName = "API";
        }
        ReportNote note = service.addNote(id, authorUuid, authorName, content);
        sendJson(exchange, 201, ReportJson.toJson(note));
    }

    private void getNotes(HttpExchange exchange, int id) throws IOException {
        JsonArray notes = new JsonArray();
        for (ReportNote note : service.getNotes(id)) {
            notes.add(ReportJson.toJson(note));
        }
        sendJson(exchange, 200, notes);
    }

    // ---- helpers ----

    private JsonObject readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return new JsonObject();
            }
            JsonElement element = JsonParser.parseString(raw);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        }
    }

    private void sendJson(HttpExchange exchange, int code, JsonElement body) throws IOException {
        byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        sendJson(exchange, code, error);
    }

    private Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        return java.util.Arrays.stream(rawQuery.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(kv -> kv.length == 2)
                .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1], (a, b) -> b));
    }

    private static int parseInt(String value, int def) {
        if (value == null) {
            return def;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String optString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private static UUID optUuid(JsonObject obj, String key, UUID def) {
        String value = optString(obj, key);
        if (value == null || value.isBlank()) {
            return def;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
